package com.runedefence.model;

public final class DefenderDefinition extends Effects
{
	/** Upgrade tiers on each of the two paths. */
	public static final int TIERS = 2;

	public String id;
	public String name;
	public String role;
	public String description;
	public int requiredLevel;
	public int cost;
	/** Tiles along each side of its square footprint. */
	public int size = 1;
	public double rangeTiles;
	public int damage;
	public double attacksPerSecond;
	public DamageType damageType;
	public TargetingMode targeting;
	/** Empty for melee, which hits instantly. Otherwise the projectile to draw. */
	public String projectile;
	public double projectileSpeed;
	/** Which burst to show on a splash hit. */
	public String blast = "blast";
	/** Shots that cannot be dodged. */
	public boolean precise;
	/** Swaps between physical and magic damage on every attack. */
	public boolean alternatesDamage;
	/** Prefers enemies it can still bind, whatever its targeting mode. */
	public boolean seeksUnbound;
	public UpgradeDefinition[] pathA;
	public UpgradeDefinition[] pathB;

	public UpgradeDefinition[] path(int path)
	{
		return path == 0 ? pathA : pathB;
	}
}
