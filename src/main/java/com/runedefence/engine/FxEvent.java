package com.runedefence.engine;

/** A visual cue the renderer picks up. It has no effect on the simulation. */
public final class FxEvent
{
	public enum Kind
	{
		HIT,
		CRIT,
		MISS,
		BLAST,
		DEATH,
		LEAK,
		GAZE
	}

	public Kind kind;
	public double x;
	public double y;
	public int value;
	/** Burst sprite and radius in tiles, for a blast. */
	public String sprite;
	public double radius;
}
