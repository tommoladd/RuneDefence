package com.runedefence.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Personal bests and recent match history for one account. */
public final class Records
{
	public static final int SCHEMA = 1;
	public static final int HISTORY_SIZE = 5;

	public static final class MapBest
	{
		public int bestWave;
		public int bestScore;
		public int wins;
		public int matches;
		/** Scores from another formula version are not comparable. */
		public int scoreVersion;
	}

	public static final class MatchResult
	{
		public String mapId;
		public boolean victory;
		public int wave;
		public int score;
		public int scoreVersion;
		public int baseHealth;
		public int goldSpent;
		public int enemiesDefeated;
		public int seconds;
		public String difficulty;
	}

	public int schemaVersion = SCHEMA;
	public Map<String, MapBest> maps = new HashMap<>();
	public List<MatchResult> history = new ArrayList<>();

	public void add(MatchResult result)
	{
		MapBest best = maps.computeIfAbsent(result.mapId, k -> new MapBest());
		if (best.scoreVersion != result.scoreVersion)
		{
			best.bestScore = 0;
			best.scoreVersion = result.scoreVersion;
		}
		best.matches++;
		best.wins += result.victory ? 1 : 0;
		best.bestWave = Math.max(best.bestWave, result.wave);
		best.bestScore = Math.max(best.bestScore, result.score);
		history.add(0, result);
		while (history.size() > HISTORY_SIZE)
		{
			history.remove(history.size() - 1);
		}
	}
}
