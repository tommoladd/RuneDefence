package com.runedefence.engine;

/**
 * Converts real elapsed time into fixed simulation steps. The cap on each advance
 * stops a stalled UI or a suspended laptop from fast-forwarding through waves.
 */
public final class SimulationClock
{
	static final long MAX_DELTA_MS = 250;
	private static final long STEP_MS = 50;

	private long accumulatorMs;

	/** Returns how many fixed steps to run for this much elapsed time. */
	public int advance(long deltaMs)
	{
		accumulatorMs += Math.max(0, Math.min(deltaMs, MAX_DELTA_MS));
		int steps = (int) (accumulatorMs / STEP_MS);
		accumulatorMs -= steps * STEP_MS;
		return steps;
	}

	/** How far between the last two steps to draw, 0 to 1. */
	public double alpha()
	{
		return accumulatorMs / (double) STEP_MS;
	}
}
