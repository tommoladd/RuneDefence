package com.runedefence.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Decides how much of each observed XP change counts as earned. Only gains seen
 * since the session baseline count, so existing XP and anything gained while the
 * plugin wasn't watching are never credited.
 *
 * Skills are plain strings so that this can be tested without a client.
 */
public final class XpProgressTracker
{
	/** Larger than any single legitimate XP drop. Anything above it is treated as bad data. */
	static final int MAX_PLAUSIBLE_GAIN = 2_000_000;

	private final Map<String, Integer> lastObserved = new HashMap<>();
	private boolean baselined;

	/** Starts a new session: nothing counts until {@link #baseline} is next called. */
	public void reset()
	{
		lastObserved.clear();
		baselined = false;
	}

	public boolean isBaselined()
	{
		return baselined;
	}

	public void baseline(Map<String, Integer> skillXp)
	{
		lastObserved.clear();
		lastObserved.putAll(skillXp);
		baselined = true;
	}

	/** Returns the XP to credit for this observation, or 0. */
	public int observe(String skill, int xp)
	{
		if (!baselined)
		{
			return 0;
		}
		Integer last = lastObserved.put(skill, xp);
		if (last == null)
		{
			return 0;
		}
		int gain = xp - last;
		// Zero is a repeat or a boost change. Negative is a rollback: rebaselined, nothing is taken away.
		return gain > 0 && gain <= MAX_PLAUSIBLE_GAIN ? gain : 0;
	}
}
