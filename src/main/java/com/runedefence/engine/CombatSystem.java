package com.runedefence.engine;

import com.runedefence.model.DamageType;
import com.runedefence.model.TargetingMode;
import java.util.List;

/** Targeting and damage rules. Every point of damage is calculated in {@link #damageFor}. */
final class CombatSystem
{
	private CombatSystem()
	{
	}

	static EnemyInstance select(DefenderInstance d, List<EnemyInstance> enemies)
	{
		EnemyInstance best = null;
		double bestScore = 0;
		boolean bestWanted = false;
		double rangeSq = d.range * d.range;
		TargetingMode mode = d.targeting;
		for (int i = 0, n = enemies.size(); i < n; i++)
		{
			EnemyInstance e = enemies.get(i);
			if (e.dead)
			{
				continue;
			}
			double dx = e.x - d.x;
			double dy = e.y - d.y;
			double distSq = dx * dx + dy * dy;
			if (distSq > rangeSq)
			{
				continue;
			}
			double score;
			switch (mode)
			{
				case LAST:
					score = e.distanceLeft();
					break;
				case STRONGEST:
					score = e.hp;
					break;
				case WEAKEST:
					score = -e.hp;
					break;
				case CLOSEST:
					score = -distSq;
					break;
				case FIRST:
				default:
					score = -e.distanceLeft();
					break;
			}
			// A binder spreads out rather than re-hitting what it can't hold, but never holds fire.
			boolean wanted = !d.def.seeksUnbound || e.canBeBound();
			if (best == null || (wanted && !bestWanted) || (wanted == bestWanted && score > bestScore))
			{
				best = e;
				bestScore = score;
				bestWanted = wanted;
			}
		}
		return best;
	}

	/** Armour is a flat reduction to physical hits; magic resist is a share of magic hits. */
	static int damageFor(int raw, DamageType type, EnemyInstance e, int armourPierce)
	{
		double damage = raw;
		if (type == DamageType.PHYSICAL)
		{
			damage -= e.effectiveArmour(armourPierce);
		}
		else
		{
			damage *= 1 - e.magicResist;
		}
		return Math.max(1, (int) Math.round(damage));
	}
}
