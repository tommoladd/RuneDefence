package com.runedefence.model;

public final class WaveDefinition
{
	public static final class Group
	{
		public String enemy;
		public int count;
		public int intervalMs;
		public int delayMs;
		/** Authored per group, so that late waves never rely on exponential scaling. */
		public double health = 1;
	}

	public Group[] groups;

	public int enemyCount()
	{
		int total = 0;
		for (Group g : groups)
		{
			total += g.count;
		}
		return total;
	}
}
