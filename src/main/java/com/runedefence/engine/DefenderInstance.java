package com.runedefence.engine;

import com.runedefence.model.DefenderDefinition;
import com.runedefence.model.Effects;
import com.runedefence.model.TargetingMode;
import com.runedefence.model.UpgradeDefinition;

/** A placed defender and its stats, recalculated whenever it is upgraded. */
public final class DefenderInstance
{
	public final DefenderDefinition def;
	/** Top left tile of its footprint. */
	public final int col;
	public final int row;
	/** Centre of its footprint, which is where range is measured from. */
	public final double x;
	public final double y;

	public int tierA;
	public int tierB;
	public int invested;
	public TargetingMode targeting;

	public int damage;
	public double range;
	public double attacksPerSecond;
	public Effects stats;

	/** The best aura from a neighbour, recalculated when the board changes. */
	public double auraDamage;
	public double auraSpeed;

	double cooldown;
	int attackCount;
	/** Petrified and unable to attack while above zero. */
	public double stunTimer;

	/** Render hints: time left in the attack animation, and which way to face. */
	public double attackAnim;
	public boolean facingLeft;

	DefenderInstance(DefenderDefinition def, int col, int row)
	{
		this.def = def;
		this.col = col;
		this.row = row;
		this.x = col + def.size / 2.0;
		this.y = row + def.size / 2.0;
		this.targeting = def.targeting;
		recalculate();
	}

	public int tier(int path)
	{
		return path == 0 ? tierA : tierB;
	}

	public boolean occupies(int c, int r)
	{
		return c >= col && r >= row && c < col + def.size && r < row + def.size;
	}

	public int effectiveDamage()
	{
		return (int) Math.round(damage * (1 + auraDamage));
	}

	public double effectiveAttacksPerSecond()
	{
		return attacksPerSecond * (1 + auraSpeed);
	}

	public void recalculate()
	{
		damage = def.damage;
		range = def.rangeTiles;
		attacksPerSecond = def.attacksPerSecond;
		stats = new Effects();
		stats.add(def);
		for (int path = 0; path < 2; path++)
		{
			UpgradeDefinition[] upgrades = def.path(path);
			for (int i = 0; i < tier(path); i++)
			{
				UpgradeDefinition u = upgrades[i];
				damage += u.damage;
				range += u.range;
				if (u.attackSpeedMultiplier > 0)
				{
					attacksPerSecond *= u.attackSpeedMultiplier;
				}
				stats.add(u);
			}
		}
	}
}
