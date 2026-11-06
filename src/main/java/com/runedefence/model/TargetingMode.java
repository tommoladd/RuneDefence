package com.runedefence.model;

public enum TargetingMode
{
	/** Closest to the keep, regardless of when it spawned or which lane it took. */
	FIRST,
	LAST,
	STRONGEST,
	WEAKEST,
	CLOSEST;

	public TargetingMode next()
	{
		return values()[(ordinal() + 1) % values().length];
	}

	public String label()
	{
		return name().charAt(0) + name().substring(1).toLowerCase();
	}
}
