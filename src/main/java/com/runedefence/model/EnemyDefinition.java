package com.runedefence.model;

public final class EnemyDefinition
{
	public String id;
	public String name;
	public int maxHealth;
	public double speed;
	public int armour;
	/** 0 to 1. Reduces magic damage. */
	public double magicResist;
	/** 0 to 1. Chance to dodge a direct hit. Splash and precise shots always land. */
	public double evasion;
	public double regenPerSecond;
	public int goldReward;
	public int baseDamage;
	/** Bosses can't be rooted, and shrug off half of any slow. */
	public boolean boss;
	/** Petrifies the nearest defender within this range for a while, on a repeating timer. */
	public double gazeRange;
	public double gazeEverySeconds;
	public double gazeSeconds;
	/** Bursts into this many of another enemy when killed. */
	public String splitInto;
	public int splitCount;
}
