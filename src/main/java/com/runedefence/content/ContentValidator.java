package com.runedefence.content;

import com.runedefence.model.Content;
import com.runedefence.model.DefenderDefinition;
import com.runedefence.model.EnemyDefinition;
import com.runedefence.model.MapDefinition;
import com.runedefence.model.UpgradeDefinition;
import com.runedefence.model.WaveDefinition;
import java.util.HashSet;
import java.util.Set;

/** Rejects bad game data at startup, so that it can't surface as a crash mid-match. */
public final class ContentValidator
{
	private ContentValidator()
	{
	}

	public static void validate(Content c)
	{
		check(c != null && c.version != null, "content has no version");
		check(c.defenders != null && c.defenders.length > 0, "no defenders");
		check(c.enemies != null && c.enemies.length > 0, "no enemies");
		check(c.maps != null && c.maps.length > 0, "no maps");

		Set<String> ids = new HashSet<>();
		for (DefenderDefinition d : c.defenders)
		{
			check(d.id != null && ids.add(d.id), "duplicate defender id " + d.id);
			check(d.requiredLevel >= 1 && d.requiredLevel <= 99, d.id + ": illegal required level");
			check(d.cost > 0, d.id + ": cost must be positive");
			check(d.size >= 1 && d.size <= 3, d.id + ": size must be 1 to 3");
			check(fraction(d.critChance) && fraction(d.rootChance) && fraction(d.slowPercent), d.id + ": bad chance");
			check(d.burnDps >= 0 && d.splash >= 0 && d.armourPierce >= 0, d.id + ": bad effect");
			check(positive(d.rangeTiles) && positive(d.attacksPerSecond), d.id + ": bad range or attack speed");
			check(d.damage > 0, d.id + ": damage must be positive");
			check(d.rootChance == 0 || positive(d.rootSeconds), d.id + ": a bind needs a duration");
			check(d.damageType != null && d.targeting != null, d.id + ": missing damage type or targeting");
			boolean ranged = d.projectile != null && !d.projectile.isEmpty();
			check(!ranged || positive(d.projectileSpeed), d.id + ": projectile needs a speed");
			checkPath(d, d.pathA);
			checkPath(d, d.pathB);
		}

		ids.clear();
		for (EnemyDefinition e : c.enemies)
		{
			check(e.id != null && ids.add(e.id), "duplicate enemy id " + e.id);
			check(e.maxHealth > 0 && positive(e.speed), e.id + ": bad health or speed");
			check(e.goldReward >= 0 && e.baseDamage > 0 && e.armour >= 0, e.id + ": bad reward, damage or armour");
			check(fraction(e.magicResist) && fraction(e.evasion), e.id + ": resist and evasion must be 0 to 1");
			check(e.splitCount <= 0 || c.enemy(e.splitInto) != null, e.id + ": splits into an unknown enemy");
			check(e.gazeRange <= 0 || positive(e.gazeEverySeconds) && positive(e.gazeSeconds), e.id + ": bad gaze");
		}

		ids.clear();
		for (MapDefinition m : c.maps)
		{
			check(m.id != null && ids.add(m.id), "duplicate map id " + m.id);
			checkMap(c, m);
		}
	}

	private static void checkPath(DefenderDefinition d, UpgradeDefinition[] path)
	{
		check(path != null && path.length == DefenderDefinition.TIERS, d.id + ": each path needs two tiers");
		for (UpgradeDefinition u : path)
		{
			check(u.cost > 0 && u.name != null, d.id + ": bad upgrade " + u.id);
			check(u.rootChance == 0 || positive(d.rootSeconds) || positive(u.rootSeconds), d.id + ": " + u.id + " binds with no duration");
		}
	}

	private static void checkMap(Content c, MapDefinition m)
	{
		check(m.columns > 0 && m.rows > 0 && m.tiles != null && m.tiles.length == m.rows, m.id + ": bad grid");
		for (String row : m.tiles)
		{
			check(row.length() == m.columns, m.id + ": bad row width");
		}
		check(m.requiredLevel >= 1 && m.requiredLevel <= 99, m.id + ": illegal required level");
		check(m.startingGold > 0 && m.startingHealth > 0, m.id + ": bad starting gold or health");
		check(positive(m.enemySpeed) && positive(m.enemyHealth) && positive(m.defenderSpeed) && positive(m.goldMultiplier),
			m.id + ": bad map rules");
		check(m.rotateLanesEvery >= 0 && m.deathHealRadius >= 0 && fraction(m.deathHealPercent), m.id + ": bad map rules");
		check(m.paths != null && m.paths.length > 0, m.id + ": no lanes");
		int[] goal = null;
		for (int[][] lane : m.paths)
		{
			checkLane(m, lane);
			int[] end = lane[lane.length - 1];
			// Lanes may start anywhere, but they all have to finish at the same keep.
			check(goal == null || (goal[0] == end[0] && goal[1] == end[1]), m.id + ": lanes must end together");
			goal = end;
		}
		check(m.waves != null && m.waves.length > 0, m.id + ": no waves");
		for (int milestone : m.milestones)
		{
			check(milestone > 0 && milestone <= m.waves.length, m.id + ": milestone beyond the last wave");
		}
		for (int w = 0; w < m.waves.length; w++)
		{
			WaveDefinition wave = m.waves[w];
			check(wave.groups != null && wave.groups.length > 0, m.id + ": empty wave " + (w + 1));
			for (WaveDefinition.Group g : wave.groups)
			{
				check(c.enemy(g.enemy) != null, m.id + ": unknown enemy " + g.enemy);
				check(fields(m, g.enemy), m.id + ": wave " + (w + 1) + " uses " + g.enemy + ", which isn't on its list");
				String child = c.enemy(g.enemy).splitInto;
				check(child == null || fields(m, child), m.id + ": " + g.enemy + " splits into " + child + ", which isn't on its list");
				check(g.count > 0 && g.intervalMs >= 0 && g.delayMs >= 0 && positive(g.health),
					m.id + ": bad group in wave " + (w + 1));
			}
		}
	}

	private static boolean fields(MapDefinition m, String enemy)
	{
		for (String id : m.monsters)
		{
			if (id.equals(enemy))
			{
				return true;
			}
		}
		return false;
	}

	private static void checkLane(MapDefinition m, int[][] lane)
	{
		check(lane != null && lane.length >= 2, m.id + ": lane too short");
		for (int i = 0; i < lane.length; i++)
		{
			int[] p = lane[i];
			check(p.length == 2, m.id + ": bad waypoint");
			check(p[0] >= -1 && p[1] >= -1 && p[0] <= m.columns && p[1] <= m.rows, m.id + ": waypoint off the map");
			if (i > 0)
			{
				int[] q = lane[i - 1];
				check((p[0] == q[0]) != (p[1] == q[1]), m.id + ": lane must move in straight, connected lines");
			}
		}
	}

	private static boolean positive(double v)
	{
		return v > 0 && !Double.isInfinite(v);
	}

	private static boolean fraction(double v)
	{
		return v >= 0 && v <= 1;
	}

	private static void check(boolean ok, String message)
	{
		if (!ok)
		{
			throw new IllegalArgumentException("Invalid game data: " + message);
		}
	}
}
