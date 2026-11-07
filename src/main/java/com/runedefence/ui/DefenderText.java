package com.runedefence.ui;

import com.runedefence.engine.DefenderInstance;
import com.runedefence.engine.GameEngine;
import com.runedefence.model.DamageType;
import com.runedefence.model.DefenderDefinition;
import com.runedefence.model.Effects;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Describes defenders and upgrades in exact numbers. Everything comes from previewing
 * the real defender, so the words can't drift away from what the engine does.
 */
final class DefenderText
{
	/** The font has no arrow glyph. */
	private static final String TO = " -> ";

	private DefenderText()
	{
	}

	/** A defender before any upgrades, in the order a player would ask about them. */
	static List<String> baseStats(DefenderDefinition d)
	{
		DefenderInstance base = DefenderInstance.preview(d, 0, 0);
		List<String> lines = new ArrayList<>();
		lines.add("Damage: " + base.damage + " " + damageKind(d));
		lines.add("Attacks every " + seconds(1 / base.attacksPerSecond));
		lines.add("Range: " + tiles(base.range));
		lines.addAll(effectChanges(new Effects(), base.stats));
		if (d.precise)
		{
			lines.add("Its attacks can't be dodged");
		}
		if (d.seeksUnbound)
		{
			lines.add("Goes for enemies it can still bind");
		}
		return lines;
	}

	/** The exact effect of the next upgrade on a path, on its own. */
	static List<String> upgradeEffects(DefenderDefinition d, int tierA, int tierB, int path)
	{
		return upgradeEffects(d, tierA, tierB, path, 1, 1);
	}

	/** As above, for a defender on the board, whose auras and map change its real numbers. */
	static List<String> upgradeEffects(DefenderDefinition d, int tierA, int tierB, int path, double damageBoost, double speedBoost)
	{
		DefenderInstance before = DefenderInstance.preview(d, tierA, tierB);
		DefenderInstance after = DefenderInstance.preview(d, path == 0 ? tierA + 1 : tierA, path == 1 ? tierB + 1 : tierB);
		List<String> lines = new ArrayList<>();
		if (after.damage != before.damage)
		{
			lines.add("Damage " + Math.round(before.damage * damageBoost) + TO + Math.round(after.damage * damageBoost));
		}
		if (after.attacksPerSecond != before.attacksPerSecond)
		{
			lines.add("Attacks every " + seconds(1 / (before.attacksPerSecond * speedBoost)) + TO + seconds(1 / (after.attacksPerSecond * speedBoost)));
		}
		if (after.range != before.range)
		{
			lines.add("Range " + number(before.range) + TO + tiles(after.range));
		}
		lines.addAll(effectChanges(before.stats, after.stats));
		return lines;
	}

	/** Whether anything on this defender, base or upgraded, can bind. */
	static boolean canBind(DefenderDefinition d)
	{
		for (int path = 0; path < 2; path++)
		{
			for (int tier = 0; tier < DefenderDefinition.TIERS; tier++)
			{
				if (d.path(path)[tier].rootChance > 0)
				{
					return true;
				}
			}
		}
		return d.rootChance > 0;
	}

	static String seconds(double value)
	{
		return String.format(Locale.ROOT, "%.2fs", value);
	}

	private static String damageKind(DefenderDefinition d)
	{
		if (d.alternatesDamage)
		{
			return "physical and magic in turn";
		}
		return d.damageType == DamageType.MAGIC ? "magic" : "physical";
	}

	private static List<String> effectChanges(Effects a, Effects b)
	{
		List<String> lines = new ArrayList<>();
		if (b.splash != a.splash)
		{
			lines.add(a.splash == 0 ? "Hits everything within " + tiles(b.splash) : "Blast radius " + number(a.splash) + TO + tiles(b.splash));
		}
		if (b.armourPierce != a.armourPierce)
		{
			lines.add(a.armourPierce == 0 ? "Ignores " + b.armourPierce + " armour" : "Ignores " + a.armourPierce + TO + b.armourPierce + " armour");
		}
		if (b.armourBreak != a.armourBreak)
		{
			lines.add(a.armourBreak == 0
				? "Cracks " + b.armourBreak + " armour for " + number(GameEngine.ARMOUR_BREAK_SECONDS) + "s"
				: "Cracks " + a.armourBreak + TO + b.armourBreak + " armour");
		}
		if (b.strikeEvery != a.strikeEvery)
		{
			lines.add("Every " + ordinal(b.strikeEvery) + " attack strikes twice");
		}
		if (b.critChance != a.critChance)
		{
			lines.add(a.critChance == 0
				? percent(b.critChance) + " chance of " + number(b.critMultiplier > 0 ? b.critMultiplier : GameEngine.DEFAULT_CRIT_MULTIPLIER) + "x damage"
				: "Critical chance " + percent(a.critChance) + TO + percent(b.critChance));
		}
		burn(a, b, lines);
		slow(a, b, lines);
		bind(a, b, lines);
		if (b.weakenPercent != a.weakenPercent)
		{
			lines.add(a.weakenPercent == 0
				? "Cursed foes take " + percent(b.weakenPercent) + " more damage for " + number(b.weakenSeconds) + "s"
				: "Cursed foes take " + percent(a.weakenPercent) + TO + percent(b.weakenPercent) + " more");
		}
		aura(a, b, lines);
		return lines;
	}

	private static void burn(Effects a, Effects b, List<String> lines)
	{
		if (a.burnDps == 0 && b.burnDps > 0)
		{
			lines.add("Burns for " + number(b.burnDps) + " a second for " + number(b.burnSeconds) + "s");
			return;
		}
		if (b.burnDps != a.burnDps)
		{
			lines.add("Burn " + number(a.burnDps) + TO + number(b.burnDps) + " a second");
		}
		if (b.burnSeconds != a.burnSeconds)
		{
			lines.add("Burn lasts " + number(a.burnSeconds) + "s" + TO + number(b.burnSeconds) + "s");
		}
	}

	private static void slow(Effects a, Effects b, List<String> lines)
	{
		if (a.slowPercent == 0 && b.slowPercent > 0)
		{
			lines.add("Slows by " + percent(b.slowPercent) + " for " + number(b.slowSeconds) + "s");
			return;
		}
		if (b.slowPercent != a.slowPercent)
		{
			lines.add("Slow " + percent(a.slowPercent) + TO + percent(b.slowPercent));
		}
		if (b.slowSeconds != a.slowSeconds)
		{
			lines.add("Slow lasts " + number(a.slowSeconds) + "s" + TO + number(b.slowSeconds) + "s");
		}
	}

	private static void bind(Effects a, Effects b, List<String> lines)
	{
		if (a.rootChance == 0 && b.rootChance > 0)
		{
			lines.add((b.rootChance >= 1 ? "Every hit binds" : percent(b.rootChance) + " chance to bind") + " for " + number(b.rootSeconds) + "s");
			return;
		}
		if (b.rootChance != a.rootChance)
		{
			lines.add("Bind chance " + percent(a.rootChance) + TO + percent(b.rootChance));
		}
		if (b.rootSeconds != a.rootSeconds)
		{
			lines.add("Binds last " + number(a.rootSeconds) + "s" + TO + number(b.rootSeconds) + "s");
		}
	}

	private static void aura(Effects a, Effects b, List<String> lines)
	{
		if (b.auraSpeedPercent != a.auraSpeedPercent)
		{
			lines.add("Allies within " + tiles(b.auraRadius) + ": +" + percent(b.auraSpeedPercent) + " attack speed");
		}
		if (b.auraDamagePercent != a.auraDamagePercent)
		{
			lines.add("Allies within " + tiles(b.auraRadius) + ": +" + percent(b.auraDamagePercent) + " damage");
		}
	}

	private static String tiles(double value)
	{
		return number(value) + (value == 1 ? " tile" : " tiles");
	}

	private static String percent(double fraction)
	{
		return Math.round(fraction * 100) + "%";
	}

	/** Up to two decimal places, without trailing zeros. */
	private static String number(double value)
	{
		String s = String.format(Locale.ROOT, "%.2f", value);
		return s.replaceAll("\\.?0+$", "");
	}

	private static String ordinal(int n)
	{
		return n == 2 ? "2nd" : n == 3 ? "3rd" : n + "th";
	}
}
