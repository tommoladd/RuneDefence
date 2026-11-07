package com.runedefence.engine;

import com.runedefence.model.EnemyDefinition;

public final class EnemyInstance
{
	public final EnemyDefinition def;
	public final double maxHp;
	public double hp;

	/** Distance travelled along the path, in tiles. */
	public double progress;
	public double x;
	public double y;
	public double prevX;
	public double prevY;

	/** A small sideways offset that stops a group walking in a single-file line. Visual only. */
	public final double laneOffset;
	/** The lane it walks. */
	final PathTable path;
	/** Its own resistance plus whatever the map adds. */
	final double magicResist;
	/** Render hints: time left on the hit flash, and which way it last walked. */
	public double hitAnim;
	public boolean facingLeft;

	int armourBroken;
	double armourBreakTimer;
	double burnDps;
	double burnTimer;
	double slowPercent;
	double slowTimer;
	double rootTimer;
	/** Can't be bound again until this runs out. */
	double bindLockout;
	double weakenPercent;
	double weakenTimer;
	double gazeTimer;
	boolean dead;

	EnemyInstance(EnemyDefinition def, double maxHp, double laneOffset)
	{
		this(def, maxHp, laneOffset, null, def.magicResist);
	}

	EnemyInstance(EnemyDefinition def, double maxHp, double laneOffset, PathTable path, double magicResist)
	{
		this.def = def;
		this.maxHp = maxHp;
		this.hp = maxHp;
		this.laneOffset = laneOffset;
		this.path = path;
		this.magicResist = magicResist;
	}

	/** Tiles left to walk, which compares fairly across lanes of different lengths. */
	double distanceLeft()
	{
		return (path != null ? path.length : 0) - progress;
	}

	public boolean isArmourBroken()
	{
		return armourBroken > 0;
	}

	public boolean isBurning()
	{
		return burnTimer > 0;
	}

	public boolean isSlowed()
	{
		return slowTimer > 0;
	}

	public boolean isRooted()
	{
		return rootTimer > 0;
	}

	boolean canBeBound()
	{
		return !def.boss && rootTimer <= 0 && bindLockout <= 0;
	}

	public boolean isWeakened()
	{
		return weakenTimer > 0;
	}

	public int effectiveArmour(int pierce)
	{
		return Math.max(0, def.armour - armourBroken - pierce);
	}
}
