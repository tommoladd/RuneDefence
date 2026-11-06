package com.runedefence;

import com.runedefence.model.Progression;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(RuneDefenceConfig.GROUP)
public interface RuneDefenceConfig extends Config
{
	String GROUP = "runedefence";
	String TUTORIAL_SEEN = "tutorialSeen";
	String XP_RATE = "xpRate";

	@ConfigItem(
		keyName = "autoStartWaves",
		name = "Auto-start waves",
		description = "Start the next wave by itself after the delay below. The first wave of a match is always started by hand.",
		position = 1
	)
	default boolean autoStartWaves()
	{
		return true;
	}

	@Range(min = 3, max = 120)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "interWaveDelay",
		name = "Delay between waves",
		description = "How long to wait before auto-starting the next wave",
		position = 2
	)
	default int interWaveDelay()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "pauseOnLogout",
		name = "Pause on logout",
		description = "Pause the match when you log out of OSRS, and resume when you log back in",
		position = 3
	)
	default boolean pauseOnLogout()
	{
		return true;
	}

	@ConfigItem(
		keyName = "pauseOnHide",
		name = "Pause when panel hidden",
		description = "Pause the match while another sidebar panel is open",
		position = 4
	)
	default boolean pauseOnHide()
	{
		return true;
	}

	@ConfigItem(
		keyName = "pauseOnFocusLost",
		name = "Pause when RuneLite loses focus",
		description = "Pause the match while you are using another window",
		position = 5
	)
	default boolean pauseOnFocusLost()
	{
		return false;
	}

	@ConfigItem(
		keyName = "battleEffects",
		name = "Battle effects",
		description = "Show hit numbers, blasts and death puffs. Turn off for a quieter game.",
		position = 6
	)
	default boolean battleEffects()
	{
		return true;
	}

	@ConfigItem(
		keyName = "unlockMessages",
		name = "Level-up messages",
		description = "Add a chat message when your RuneDefence level rises",
		position = 7
	)
	default boolean unlockMessages()
	{
		return true;
	}

	@Range(min = Progression.MIN_XP_RATE, max = Progression.MAX_XP_RATE)
	@ConfigItem(
		keyName = XP_RATE,
		name = "OSRS XP per RuneDefence XP",
		description = "How much OSRS XP earns 1 RuneDefence XP, from 1 (fastest levelling) to 10 (slowest). This also recalculates your current level from all the XP counted so far, and changing it never loses XP.",
		position = 8
	)
	default int xpRate()
	{
		return Progression.DEFAULT_XP_RATE;
	}
}
