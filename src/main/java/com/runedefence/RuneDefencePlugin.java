package com.runedefence;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.runedefence.client.XpProgressTracker;
import com.runedefence.content.ContentLoader;
import com.runedefence.engine.GameEngine;
import com.runedefence.model.Content;
import com.runedefence.model.DefenderDefinition;
import com.runedefence.model.MatchSave;
import com.runedefence.model.Progression;
import com.runedefence.model.Records;
import com.runedefence.persistence.SaveRepository;
import com.runedefence.ui.RuneDefencePanel;
import com.runedefence.ui.SpriteAtlas;
import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.FocusChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "RuneDefence",
	description = "A tower defence minigame in the sidebar, levelled up by your OSRS XP",
	tags = {"game", "minigame", "tower", "defence", "td", "skilling"}
)
public class RuneDefencePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private RuneDefenceConfig config;

	@Inject
	private Gson gson;

	private final XpProgressTracker tracker = new XpProgressTracker();

	private NavigationButton navButton;
	private volatile RuneDefencePanel panel;
	private Content content;

	/** The account currently loaded, or null before the first login. */
	private volatile String profileKey;
	private volatile SaveRepository repository;
	/** Written on the client thread only; immutable, so other threads can read it safely. */
	private volatile Progression progress = new Progression(0, 0);

	@Override
	protected void startUp()
	{
		BufferedImage icon = ImageUtil.loadImageResource(RuneDefencePlugin.class, "/com/runedefence/panel_icon.png");
		PluginPanel view;
		try
		{
			content = ContentLoader.load(gson);
			SpriteAtlas atlas = new SpriteAtlas(gson);
			panel = new RuneDefencePanel(new GameEngine(content), atlas, new PanelHost());
			view = panel;
		}
		catch (IOException | RuntimeException e)
		{
			// Bad data must not take the client down with it, so explain in the sidebar instead.
			log.warn("RuneDefence could not load its game data", e);
			content = null;
			panel = null;
			view = new ErrorPanel();
		}

		navButton = NavigationButton.builder()
			.tooltip("RuneDefence")
			.icon(icon)
			.priority(8)
			.panel(view)
			.build();
		clientToolbar.addNavigation(navButton);

		if (panel == null)
		{
			return;
		}
		panel.applySettings(readSettings());
		tracker.reset();
		switchAccount(configManager.getRSProfileKey());
		if (!Boolean.TRUE.toString().equals(configManager.getConfiguration(RuneDefenceConfig.GROUP, RuneDefenceConfig.TUTORIAL_SEEN)))
		{
			panel.showTutorial();
		}
		log.info("RuneDefence started");
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		if (panel != null)
		{
			SaveRepository repo = repository;
			if (repo != null)
			{
				repo.saveMatch(panel.currentSave());
			}
			panel.dispose();
			panel = null;
		}
		tracker.reset();
		repository = null;
		profileKey = null;
		progress = new Progression(0, 0);
		content = null;
		log.info("RuneDefence stopped");
	}

	// ---- OSRS events. These only observe, and never affect the game. --------

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (panel == null)
		{
			return;
		}
		GameState state = event.getGameState();
		// Each of these is a session boundary: any XP change across one was not observed.
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			tracker.reset();
		}
		if (state == GameState.LOGIN_SCREEN && config.pauseOnLogout())
		{
			setPauseReason(RuneDefencePanel.PAUSE_LOGOUT, true);
		}
		else if (state == GameState.LOGGED_IN)
		{
			setPauseReason(RuneDefencePanel.PAUSE_LOGOUT, false);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (panel == null || tracker.isBaselined() || profileKey == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		// Taken on a tick because the login stat burst has finished by then.
		Map<String, Integer> baseline = new HashMap<>();
		long total = 0;
		for (Skill skill : Skill.values())
		{
			if (skill != Skill.OVERALL)
			{
				int xp = client.getSkillExperience(skill);
				baseline.put(skill.name(), xp);
				total += xp;
			}
		}
		// All zeroes means stats haven't arrived. Baselining now would credit a whole account.
		if (total > 0)
		{
			tracker.baseline(baseline);
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (panel == null || repository == null || event.getSkill() == Skill.OVERALL)
		{
			return;
		}
		int gain = tracker.observe(event.getSkill().name(), event.getXp());
		if (gain <= 0)
		{
			return;
		}
		Progression before = progress;
		Progression after = before.plus(gain);
		progress = after;
		repository.saveEligibleXp(after.eligibleOsrsXp);
		if (after.level() > before.level())
		{
			announceLevel(before.level(), after.level());
		}
		RuneDefencePanel target = panel;
		SwingUtilities.invokeLater(() -> target.setProgress(after));
	}

	private void announceLevel(int from, int to)
	{
		if (!config.unlockMessages())
		{
			return;
		}
		StringBuilder message = new StringBuilder("RuneDefence level ").append(to).append('!');
		for (DefenderDefinition d : content.defenders)
		{
			if (d.requiredLevel > from && d.requiredLevel <= to)
			{
				message.append(" Unlocked ").append(d.name).append('.');
			}
		}
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message.toString(), null);
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		if (panel == null)
		{
			return;
		}
		// Never credit a difference between two characters.
		tracker.reset();
		String key = event.getNewProfile();
		// Logging out can clear the profile. Stay on the last account until there is a new one.
		if (key != null && !key.equals(profileKey))
		{
			switchAccount(key);
		}
	}

	@Subscribe
	public void onFocusChanged(FocusChanged event)
	{
		if (panel != null)
		{
			setPauseReason(RuneDefencePanel.PAUSE_FOCUS, config.pauseOnFocusLost() && !event.isFocused());
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		// Saves share this group but carry a profile, and arrive on every XP drop.
		if (panel != null && RuneDefenceConfig.GROUP.equals(event.getGroup()) && event.getProfile() == null)
		{
			RuneDefencePanel.Settings settings = readSettings();
			RuneDefencePanel target = panel;
			SwingUtilities.invokeLater(() -> target.applySettings(settings));
			if (RuneDefenceConfig.XP_RATE.equals(event.getKey()))
			{
				clientThread.invoke(this::applyXpRate);
			}
		}
	}

	/** On the client thread because that's where progress is written. No chat message, as the player just did this. */
	private void applyXpRate()
	{
		RuneDefencePanel target = panel;
		if (target == null)
		{
			return;
		}
		Progression updated = progress.withXpRate(config.xpRate());
		progress = updated;
		SwingUtilities.invokeLater(() -> target.setProgress(updated));
	}

	private void setPauseReason(int reason, boolean on)
	{
		RuneDefencePanel target = panel;
		SwingUtilities.invokeLater(() -> target.setPauseReason(reason, on));
	}

	// ---- Accounts and saves -------------------------------------------------

	/** Loads an account's data. With no key, this session's progress is simply not kept. */
	private void switchAccount(String key)
	{
		SaveRepository previous = repository;
		boolean wasGuest = previous != null && profileKey == null;
		SaveRepository next = new SaveRepository(key == null ? new MemoryStore() : new ProfileStore(key), gson);
		repository = next;
		profileKey = key;
		Progression loaded = new Progression(next.loadEligibleXp(), 0, config.xpRate());
		progress = loaded;
		RuneDefencePanel target = panel;
		Runnable swap = () ->
		{
			if (previous != null)
			{
				previous.saveMatch(target.currentSave());
			}
			MatchSave saved = next.loadMatch();
			// A match started before logging in carries over, unless this account has its own.
			target.loadAccount(loaded, next.loadRecords(), saved, wasGuest && saved == null);
		};
		if (SwingUtilities.isEventDispatchThread())
		{
			swap.run();
		}
		else
		{
			SwingUtilities.invokeLater(swap);
		}
	}

	private RuneDefencePanel.Settings readSettings()
	{
		RuneDefencePanel.Settings s = new RuneDefencePanel.Settings();
		s.autoStart = config.autoStartWaves();
		s.interWaveDelaySeconds = config.interWaveDelay();
		s.pauseOnHide = config.pauseOnHide();
		s.effects = config.battleEffects();
		return s;
	}

	@Provides
	RuneDefenceConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(RuneDefenceConfig.class);
	}

	private final class PanelHost implements RuneDefencePanel.Host
	{
		@Override
		public void saveMatch(MatchSave save)
		{
			SaveRepository repo = repository;
			if (repo != null)
			{
				repo.saveMatch(save);
			}
		}

		@Override
		public void saveRecords(Records records)
		{
			SaveRepository repo = repository;
			if (repo != null)
			{
				repo.saveRecords(records);
			}
		}

		@Override
		public void tutorialFinished()
		{
			configManager.setConfiguration(RuneDefenceConfig.GROUP, RuneDefenceConfig.TUTORIAL_SEEN, true);
		}
	}

	/** Saves against one RuneLite profile, which is what keeps characters apart. */
	private final class ProfileStore implements SaveRepository.Store
	{
		private final String key;

		ProfileStore(String key)
		{
			this.key = key;
		}

		@Override
		public String get(String name)
		{
			return configManager.getConfiguration(RuneDefenceConfig.GROUP, key, name);
		}

		@Override
		public void set(String name, String value)
		{
			configManager.setConfiguration(RuneDefenceConfig.GROUP, key, name, value);
		}

		@Override
		public void unset(String name)
		{
			configManager.unsetConfiguration(RuneDefenceConfig.GROUP, key, name);
		}
	}

	/** For playing before the first login of a client run, when there's no profile yet. */
	private static final class MemoryStore implements SaveRepository.Store
	{
		private final Map<String, String> values = new HashMap<>();

		@Override
		public String get(String name)
		{
			return values.get(name);
		}

		@Override
		public void set(String name, String value)
		{
			values.put(name, value);
		}

		@Override
		public void unset(String name)
		{
			values.remove(name);
		}
	}

	private static final class ErrorPanel extends PluginPanel
	{
		ErrorPanel()
		{
			setLayout(new BorderLayout());
			add(new JLabel("<html>RuneDefence could not load its game data. Try reinstalling the plugin.</html>"), BorderLayout.NORTH);
		}
	}
}
