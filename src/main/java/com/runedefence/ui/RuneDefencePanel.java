package com.runedefence.ui;

import com.runedefence.engine.GameEngine;
import com.runedefence.engine.ScoreCalculator;
import com.runedefence.engine.SimulationClock;
import com.runedefence.model.MapDefinition;
import com.runedefence.model.MatchSave;
import com.runedefence.model.Progression;
import com.runedefence.model.Records;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Timer;
import net.runelite.client.ui.PluginPanel;

/**
 * The sidebar. All game and UI state belongs to the Swing EDT: one timer steps the
 * engine and repaints, so the engine can never be mutated while it is being drawn.
 * None of it touches the game's client thread.
 */
public final class RuneDefencePanel extends PluginPanel
{
	public static final int PAUSE_HIDDEN = 1;
	public static final int PAUSE_LOGOUT = 2;
	public static final int PAUSE_FOCUS = 4;

	private static final int ACTIVE_FRAME_MS = 33;
	private static final int HIDDEN_FRAME_MS = 100;
	private static final int ARM_FRAMES = 90;
	private static final int CONTENT_HEIGHT = 472;

	/** What the panel needs from whichever program is hosting it. */
	public interface Host
	{
		void saveMatch(MatchSave save);

		void saveRecords(Records records);

		void tutorialFinished();
	}

	/** Settings, as a plain value so the panel doesn't depend on RuneLite's config proxy. */
	public static final class Settings
	{
		public boolean autoStart;
		public int interWaveDelaySeconds;
		public boolean pauseOnHide;
		public boolean effects;
	}

	private final GameEngine engine;
	private final Host host;
	private final SimulationClock clock = new SimulationClock();
	private final Timer timer;

	private final BattlefieldPanel field;
	private final HudBar hud;
	private final ShopDock shop;
	private final DefenderDock detail;
	private final RosterView roster;
	private final ProgressView progressView;

	private final JPanel dock = new JPanel(new CardLayout());
	private final JPanel cards = new JPanel(new CardLayout());
	private final StoneButton[] tabs = {new StoneButton("Play"), new StoneButton("Roster"), new StoneButton("Progress")};
	private final StoneButton primary = new StoneButton("");
	private final StoneButton pause = new StoneButton("Pause");
	private final StoneButton quit = new StoneButton("Quit");

	private Records records = new Records();
	private int playerLevel = 1;
	private int menuIndex;
	private MatchSave savedMatch;
	private Settings settings = new Settings();
	private int pauseReasons;
	private boolean systemPaused;
	private boolean active;
	private boolean disposed;
	private long lastNanos;
	private int quitArmedFrames;

	private int uiSignature;
	private int saveSignature = Integer.MIN_VALUE;
	private MatchSave lastSaved;
	private boolean showingDetail;

	public RuneDefencePanel(GameEngine engine, SpriteAtlas atlas, Host host)
	{
		super(false);
		this.engine = engine;
		this.host = host;
		engine.setMatchEndListener(this::onMatchEnd);

		field = new BattlefieldPanel(engine, atlas, new BattlefieldPanel.Listener()
		{
			@Override
			public void onFieldChanged()
			{
				refreshUi();
			}

			@Override
			public void onTutorialFinished()
			{
				host.tutorialFinished();
			}
		});
		hud = new HudBar(engine, atlas);
		shop = new ShopDock(engine, atlas, field);
		detail = new DefenderDock(engine, field, this::refreshUi);
		roster = new RosterView(engine.content(), atlas);
		progressView = new ProgressView(engine.content(), atlas);

		setLayout(new BorderLayout());
		setBackground(Theme.STONE_DARK);
		add(buildTabs(), BorderLayout.NORTH);
		cards.add(scroll(buildPlay()), tabs[0].getText());
		cards.add(scroll(roster), tabs[1].getText());
		cards.add(scroll(progressView), tabs[2].getText());
		// Fixed, or the longest tab would stretch the panel. Longer tabs scroll instead.
		cards.setPreferredSize(new Dimension(PANEL_WIDTH, CONTENT_HEIGHT));
		add(cards, BorderLayout.CENTER);
		selectTab(0);

		timer = new Timer(ACTIVE_FRAME_MS, e -> tick());
		timer.setInitialDelay(0);
		refreshUi();
	}

	private JPanel buildTabs()
	{
		JPanel row = new JPanel(new GridLayout(1, tabs.length));
		row.setBackground(Theme.STONE_DARK);
		for (int i = 0; i < tabs.length; i++)
		{
			int index = i;
			tabs[i].addActionListener(e -> selectTab(index));
			row.add(tabs[i]);
		}
		return row;
	}

	void selectTab(int index)
	{
		for (int i = 0; i < tabs.length; i++)
		{
			tabs[i].setActive(i == index);
			tabs[i].setTextColour(i == index ? Theme.ORANGE : Theme.TEXT);
		}
		((CardLayout) cards.getLayout()).show(cards, tabs[index].getText());
	}

	private JPanel buildPlay()
	{
		JPanel play = new JPanel();
		play.setLayout(new BoxLayout(play, BoxLayout.Y_AXIS));
		play.setBackground(Theme.STONE_DARK);

		JPanel fieldRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		fieldRow.setBackground(Theme.STONE_DARK);
		fieldRow.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Theme.STONE_EDGE));
		fieldRow.add(field);

		primary.setTextColour(Theme.YELLOW);
		primary.addActionListener(e -> primaryAction());
		pause.addActionListener(e -> togglePause());
		quit.addActionListener(e -> quitAction());
		JPanel controls = new JPanel(new BorderLayout(1, 0));
		controls.setBackground(Theme.STONE_DARK);
		JPanel right = new JPanel(new GridLayout(1, 2, 1, 0));
		right.setOpaque(false);
		right.add(pause);
		right.add(quit);
		right.setPreferredSize(new Dimension(96, 24));
		controls.add(primary, BorderLayout.CENTER);
		controls.add(right, BorderLayout.EAST);

		dock.setBackground(Theme.STONE_DARK);
		dock.add(shop, "shop");
		dock.add(detail, "detail");
		dock.setPreferredSize(new Dimension(224, 88));

		for (JPanel part : new JPanel[]{fieldRow, controls, dock})
		{
			part.setAlignmentX(LEFT_ALIGNMENT);
		}
		hud.setAlignmentX(LEFT_ALIGNMENT);
		play.add(hud);
		play.add(fieldRow);
		play.add(controls);
		play.add(dock);
		return play;
	}

	private static JScrollPane scroll(java.awt.Component view)
	{
		JPanel holder = new JPanel(new BorderLayout());
		holder.setBackground(Theme.STONE_DARK);
		holder.add(view, BorderLayout.NORTH);
		JScrollPane pane = new JScrollPane(holder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		pane.setBorder(null);
		pane.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
		return pane;
	}

	// ---- Host-facing API. Call these on the EDT. ----------------------------

	/**
	 * Swaps in another account's data, after the host has saved the previous account's.
	 * With keepMatch, a match in progress carries over, for someone who started playing
	 * at the login screen and has now logged in.
	 */
	public void loadAccount(Progression progress, Records accountRecords, MatchSave saved, boolean keepMatch)
	{
		if (!keepMatch || !engine.isMatchActive())
		{
			engine.abandon();
			field.clearSelection();
		}
		records = accountRecords;
		savedMatch = engine.isMatchActive() ? null : saved;
		if (savedMatch != null)
		{
			selectMap(savedMatch.mapId);
		}
		lastSaved = null;
		saveSignature = Integer.MIN_VALUE;
		progressView.setRecords(records);
		setProgress(progress);
		refreshUi();
	}

	public void setProgress(Progression progress)
	{
		int level = progress.level();
		playerLevel = level;
		engine.setPlayerLevel(level);
		hud.setLevel(level);
		shop.setLevel(level);
		roster.setProgress(progress);
		progressView.setProgress(progress);
		refreshUi();
	}

	public void applySettings(Settings value)
	{
		settings = value;
		engine.setAutoStartDelay(value.autoStart ? value.interWaveDelaySeconds : 0);
		field.setEffects(value.effects);
		if (!value.pauseOnHide)
		{
			setPauseReason(PAUSE_HIDDEN, false);
		}
		refreshUi();
	}

	public void showTutorial()
	{
		field.showTutorial();
	}

	/** Pauses for an outside reason, and resumes once every such reason has cleared. */
	public void setPauseReason(int reason, boolean on)
	{
		pauseReasons = on ? pauseReasons | reason : pauseReasons & ~reason;
		if (pauseReasons != 0)
		{
			if (engine.state() == GameEngine.State.RUNNING || engine.state() == GameEngine.State.WAVE_COMPLETE)
			{
				engine.pause();
				systemPaused = true;
			}
		}
		else if (systemPaused)
		{
			systemPaused = false;
			engine.resume();
		}
		refreshUi();
		updateTimer();
	}

	/** The resume point to store for this account, or null if there is no active match. */
	public MatchSave currentSave()
	{
		return engine.isMatchActive() ? engine.checkpoint() : savedMatch;
	}

	public void dispose()
	{
		disposed = true;
		active = false;
		timer.stop();
		engine.setMatchEndListener(null);
	}

	// ---- Sidebar lifecycle --------------------------------------------------

	@Override
	public void onActivate()
	{
		active = true;
		setPauseReason(PAUSE_HIDDEN, false);
		updateTimer();
	}

	@Override
	public void onDeactivate()
	{
		active = false;
		if (settings.pauseOnHide)
		{
			setPauseReason(PAUSE_HIDDEN, true);
		}
		updateTimer();
	}

	/** Idle costs nothing: the timer only runs if there's something to show or simulate. */
	private void updateTimer()
	{
		boolean simulating = engine.state() == GameEngine.State.RUNNING || engine.state() == GameEngine.State.WAVE_COMPLETE;
		if (!disposed && (active || simulating))
		{
			timer.setDelay(active ? ACTIVE_FRAME_MS : HIDDEN_FRAME_MS);
			if (!timer.isRunning())
			{
				lastNanos = System.nanoTime();
				timer.start();
			}
		}
		else
		{
			timer.stop();
		}
	}

	// ---- Actions ------------------------------------------------------------

	private void primaryAction()
	{
		switch (engine.state())
		{
			case MENU:
				if (savedMatch != null && engine.restore(savedMatch))
				{
					savedMatch = null;
				}
				else
				{
					startNewMatch();
				}
				break;
			case VICTORY:
			case DEFEAT:
				startNewMatch();
				break;
			case PREPARING:
			case WAVE_COMPLETE:
				engine.startWave();
				break;
			default:
				break;
		}
		refreshUi();
		updateTimer();
	}

	private void startNewMatch()
	{
		// After a match, New match replays that map. Otherwise it's whatever the picker shows.
		String mapId = engine.map() != null ? engine.map().id : selectedMap().id;
		selectMap(mapId);
		if (engine.newMatch(mapId, System.nanoTime()) == GameEngine.Result.OK)
		{
			savedMatch = null;
			field.clearSelection();
		}
	}

	private MapDefinition selectedMap()
	{
		return engine.content().maps[menuIndex];
	}

	private void selectMap(String mapId)
	{
		MapDefinition[] maps = engine.content().maps;
		for (int i = 0; i < maps.length; i++)
		{
			if (maps[i].id.equals(mapId))
			{
				menuIndex = i;
				return;
			}
		}
	}

	void changeMap(int delta)
	{
		menuIndex = Math.floorMod(menuIndex + delta, engine.content().maps.length);
		refreshUi();
	}

	private void togglePause()
	{
		if (engine.state() == GameEngine.State.MENU)
		{
			changeMap(-1);
			return;
		}
		if (engine.state() == GameEngine.State.PAUSED)
		{
			systemPaused = false;
			engine.resume();
		}
		else if (engine.pause() == GameEngine.Result.OK)
		{
			systemPaused = false;
		}
		refreshUi();
		updateTimer();
	}

	/** Quit needs a second click, because it throws the match away. */
	private void quitAction()
	{
		if (engine.state() == GameEngine.State.MENU && savedMatch != null)
		{
			// On the menu, this discards a saved match and starts a clean slate.
			if (quitArmedFrames == 0)
			{
				quitArmedFrames = ARM_FRAMES;
				refreshUi();
				return;
			}
			savedMatch = null;
			quitArmedFrames = 0;
			host.saveMatch(null);
			refreshUi();
			return;
		}
		if (engine.state() == GameEngine.State.MENU)
		{
			changeMap(1);
			return;
		}
		boolean finished = !engine.isMatchActive();
		if (!finished && quitArmedFrames == 0)
		{
			quitArmedFrames = ARM_FRAMES;
			refreshUi();
			return;
		}
		quitArmedFrames = 0;
		engine.abandon();
		systemPaused = false;
		field.clearSelection();
		host.saveMatch(null);
		lastSaved = null;
		refreshUi();
		updateTimer();
	}

	private void onMatchEnd(GameEngine e, boolean victory)
	{
		Records.MatchResult r = new Records.MatchResult();
		r.mapId = e.map().id;
		r.victory = victory;
		r.wave = e.wavesCleared();
		r.score = e.score();
		r.scoreVersion = ScoreCalculator.VERSION;
		r.baseHealth = e.baseHealth();
		r.goldSpent = e.goldSpent();
		r.enemiesDefeated = e.enemiesDefeated();
		r.seconds = (int) (e.ticks() * GameEngine.STEP_SECONDS);
		r.difficulty = "normal";
		records.add(r);
		selectMap(r.mapId);
		progressView.setRecords(records);
		host.saveRecords(records);
		host.saveMatch(null);
		lastSaved = null;
	}

	// ---- Frame --------------------------------------------------------------

	void tick()
	{
		long now = System.nanoTime();
		long deltaMs = (now - lastNanos) / 1_000_000;
		lastNanos = now;
		int steps = clock.advance(deltaMs);
		for (int i = 0; i < steps; i++)
		{
			engine.step();
		}
		if (quitArmedFrames > 0 && --quitArmedFrames == 0)
		{
			refreshUi();
		}
		saveIfChanged();
		if (isShowing())
		{
			field.animate(deltaMs / 1000.0, clock.alpha());
			field.repaint();
			refreshIfChanged();
		}
		updateTimer();
	}

	/** Between waves the board is the save, so each change to it is written. Mid-wave nothing changes. */
	private void saveIfChanged()
	{
		if (!engine.isMatchActive())
		{
			return;
		}
		int signature = 31 * engine.gold() + 7 * engine.waveIndex() + engine.state().ordinal() + 1009 * engine.defenders().size();
		if (signature == saveSignature)
		{
			return;
		}
		saveSignature = signature;
		MatchSave save = engine.checkpoint();
		if (save != null && save != lastSaved)
		{
			lastSaved = save;
			host.saveMatch(save);
		}
	}

	private void refreshIfChanged()
	{
		if (uiState() != uiSignature)
		{
			refreshUi();
		}
		hud.repaint();
	}

	/** Changes whenever the buttons or docks need redrawing, which is far less often than every frame. */
	private int uiState()
	{
		return engine.gold() * 31 + engine.baseHealth() * 37 + engine.state().ordinal() * 41 + engine.waveIndex() * 43
			+ (int) Math.ceil(engine.autoStartTimer()) * 47;
	}

	void refreshUi()
	{
		uiSignature = uiState();
		if (field.selected() != null && !engine.defenders().contains(field.selected()))
		{
			field.clearSelection();
			return;
		}
		updateControls();
		boolean wantDetail = field.selected() != null;
		if (wantDetail != showingDetail)
		{
			showingDetail = wantDetail;
			((CardLayout) dock.getLayout()).show(dock, wantDetail ? "detail" : "shop");
		}
		shop.refresh();
		detail.refresh();
		hud.repaint();
		field.repaint();
	}

	private void updateControls()
	{
		GameEngine.State state = engine.state();
		switch (state)
		{
			case MENU:
				boolean locked = savedMatch == null && playerLevel < selectedMap().requiredLevel;
				String label = savedMatch != null ? "Resume wave " + (savedMatch.wave + 1) : locked ? "Level " + selectedMap().requiredLevel + " needed" : "New match";
				primary.setLabel(label, savedMatch != null ? "Carry on from your saved match" : "Start a new match on this map");
				primary.setEnabled(!locked);
				break;
			case PREPARING:
				primary.setLabel("Start wave " + (engine.waveIndex() + 1), "Send the next wave");
				primary.setEnabled(true);
				break;
			case WAVE_COMPLETE:
				int left = (int) Math.ceil(engine.autoStartTimer());
				primary.setLabel(left > 0 ? "Next wave (" + left + "s)" : "Start wave " + (engine.waveIndex() + 1), "Send the next wave now");
				primary.setEnabled(true);
				break;
			case RUNNING:
				primary.setLabel("Wave in progress", null);
				primary.setEnabled(false);
				break;
			case PAUSED:
				primary.setLabel("Paused", null);
				primary.setEnabled(false);
				break;
			default:
				primary.setLabel("New match", "Start again on this map");
				primary.setEnabled(true);
				break;
		}
		boolean menu = state == GameEngine.State.MENU;
		boolean canPause = state == GameEngine.State.RUNNING || state == GameEngine.State.WAVE_COMPLETE
			|| state == GameEngine.State.PREPARING || state == GameEngine.State.PAUSED;
		// On the menu these two become the map picker, unless a saved match has to be dealt with first.
		boolean menuWithSave = menu && savedMatch != null;
		pause.setEnabled(canPause || (menu && !menuWithSave));
		if (menu)
		{
			pause.setLabel("< Map", "Previous map");
		}
		else
		{
			pause.setLabel(state == GameEngine.State.PAUSED ? "Resume" : "Pause", "Freeze or resume the match");
		}
		quit.setEnabled(true);
		String quitLabel = menuWithSave ? "Discard" : menu ? "Map >" : engine.isMatchActive() ? "Quit" : "Menu";
		String quitTip = menuWithSave ? "Delete your saved match" : menu ? "Next map" : "Leave this match. Progress is safe.";
		quit.setLabel(quitArmedFrames > 0 ? "Sure?" : quitLabel, quitTip);
		field.setMenu(selectedMap(), savedMatch == null && playerLevel < selectedMap().requiredLevel, records.maps.get(selectedMap().id));
		quit.setTextColour(quitArmedFrames > 0 ? Theme.RED : Theme.TEXT);
	}

	BattlefieldPanel field()
	{
		return field;
	}

	ShopDock shop()
	{
		return shop;
	}
}
