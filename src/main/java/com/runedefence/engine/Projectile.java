package com.runedefence.engine;

import com.runedefence.model.DamageType;

public final class Projectile
{
	public String kind;
	public double x;
	public double y;
	public double prevX;
	public double prevY;
	/** Which way it was last moving, for drawing arrows at the right angle. */
	public double dirX;
	public double dirY;

	/** Whoever fired it, whose stats decide the hit even if it has since been sold. */
	DefenderInstance source;
	EnemyInstance target;
	double targetX;
	double targetY;
	double speed;
	DamageType type;
	int strikes;
}
