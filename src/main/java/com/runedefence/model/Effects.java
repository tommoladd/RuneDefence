package com.runedefence.model;

/**
 * Combat effects shared by defenders and their upgrades. An upgrade's values add to
 * the defender's. Durations and multipliers take the longest or largest instead.
 */
public class Effects
{
	public double splash;
	public int armourPierce;
	public int armourBreak;
	/** Every Nth attack strikes twice. */
	public int strikeEvery;
	public double critChance;
	public double critMultiplier;
	public double burnDps;
	public double burnSeconds;
	public double slowPercent;
	public double slowSeconds;
	public double rootChance;
	public double rootSeconds;
	/** Cursed enemies take this much more damage from everything. */
	public double weakenPercent;
	public double weakenSeconds;
	/** Nearby defenders hit harder or faster. Auras of one kind don't stack. */
	public double auraRadius;
	public double auraDamagePercent;
	public double auraSpeedPercent;

	public void add(Effects o)
	{
		splash += o.splash;
		armourPierce += o.armourPierce;
		armourBreak += o.armourBreak;
		if (o.strikeEvery > 0)
		{
			strikeEvery = o.strikeEvery;
		}
		critChance += o.critChance;
		critMultiplier = Math.max(critMultiplier, o.critMultiplier);
		burnDps += o.burnDps;
		burnSeconds = Math.max(burnSeconds, o.burnSeconds);
		slowPercent += o.slowPercent;
		slowSeconds = Math.max(slowSeconds, o.slowSeconds);
		rootChance += o.rootChance;
		rootSeconds = Math.max(rootSeconds, o.rootSeconds);
		weakenPercent += o.weakenPercent;
		weakenSeconds = Math.max(weakenSeconds, o.weakenSeconds);
		auraRadius = Math.max(auraRadius, o.auraRadius);
		auraDamagePercent += o.auraDamagePercent;
		auraSpeedPercent += o.auraSpeedPercent;
	}
}
