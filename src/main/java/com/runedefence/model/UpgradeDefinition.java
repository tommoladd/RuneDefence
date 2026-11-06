package com.runedefence.model;

/** One step on a defender's upgrade path. Unset fields have no effect. */
public final class UpgradeDefinition extends Effects
{
	public String id;
	public String name;
	public String description;
	public int cost;
	public int damage;
	public double range;
	public double attackSpeedMultiplier;
}
