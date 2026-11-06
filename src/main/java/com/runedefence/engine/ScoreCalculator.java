package com.runedefence.engine;

/** Versioned, so that a later balance patch can't be compared against old scores. */
public final class ScoreCalculator
{
	public static final int VERSION = 2;

	private ScoreCalculator()
	{
	}

	public static int score(int wavesCleared, int baseHealth, int enemiesDefeated, boolean victory)
	{
		return wavesCleared * 100 + enemiesDefeated * 2 + baseHealth * 25 + (victory ? 5000 : 0);
	}
}
