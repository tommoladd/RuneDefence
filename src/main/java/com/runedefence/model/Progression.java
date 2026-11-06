package com.runedefence.model;

/**
 * Permanent progress, as an immutable value that is safe to pass between threads.
 * Only the eligible OSRS XP is stored; everything else derives from it, so the
 * remainder can never drift out of step with the total.
 */
public final class Progression
{
	public static final int MAX_LEVEL = 99;
	public static final int MIN_XP_RATE = 1;
	public static final int MAX_XP_RATE = 10;
	public static final int DEFAULT_XP_RATE = MAX_XP_RATE;

	private static final int[] XP_TABLE = buildXpTable();

	public final long eligibleOsrsXp;
	public final long sessionOsrsXp;
	/** The OSRS XP that earns 1 RuneDefence XP, so lower levels up faster. */
	public final int xpRate;

	public Progression(long eligibleOsrsXp, long sessionOsrsXp)
	{
		this(eligibleOsrsXp, sessionOsrsXp, DEFAULT_XP_RATE);
	}

	public Progression(long eligibleOsrsXp, long sessionOsrsXp, int xpRate)
	{
		this.eligibleOsrsXp = eligibleOsrsXp;
		this.sessionOsrsXp = sessionOsrsXp;
		// The config UI enforces the range, but a hand-edited profile doesn't, and this is a divisor.
		this.xpRate = Math.max(MIN_XP_RATE, Math.min(xpRate, MAX_XP_RATE));
	}

	public Progression plus(long osrsXp)
	{
		return new Progression(eligibleOsrsXp + osrsXp, sessionOsrsXp + osrsXp, xpRate);
	}

	public Progression withXpRate(int rate)
	{
		return new Progression(eligibleOsrsXp, sessionOsrsXp, rate);
	}

	public long runeDefenceXp()
	{
		return eligibleOsrsXp / xpRate;
	}

	public int remainder()
	{
		return (int) (eligibleOsrsXp % xpRate);
	}

	public int level()
	{
		return levelForXp(runeDefenceXp());
	}

	/** Fraction of the way to the next level, 1 at the cap. */
	public double levelProgress()
	{
		int level = level();
		if (level >= MAX_LEVEL)
		{
			return 1;
		}
		int from = xpForLevel(level);
		return (double) (runeDefenceXp() - from) / (xpForLevel(level + 1) - from);
	}

	public static int xpForLevel(int level)
	{
		return XP_TABLE[Math.max(1, Math.min(level, MAX_LEVEL))];
	}

	public static int levelForXp(long xp)
	{
		for (int level = MAX_LEVEL; level >= 2; level--)
		{
			if (xp >= XP_TABLE[level])
			{
				return level;
			}
		}
		return 1;
	}

	private static int[] buildXpTable()
	{
		int[] table = new int[MAX_LEVEL + 1];
		int points = 0;
		for (int level = 2; level <= MAX_LEVEL; level++)
		{
			int prev = level - 1;
			points += (int) Math.floor(prev + 300 * Math.pow(2, prev / 7.0));
			table[level] = points / 4;
		}
		return table;
	}
}
