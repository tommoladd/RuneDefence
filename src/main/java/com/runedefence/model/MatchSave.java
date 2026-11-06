package com.runedefence.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A resumable match. It is a checkpoint at the start of a wave rather than a
 * mid-wave snapshot, which keeps it small and means a reload replays the wave
 * exactly: the RNG is reseeded per wave, and cooldowns reset when a wave starts.
 */
public final class MatchSave
{
	public static final int SCHEMA = 1;

	public static final class SavedDefender
	{
		public String id;
		public int col;
		public int row;
		public int tierA;
		public int tierB;
		public int invested;
		public TargetingMode targeting;
	}

	public int schemaVersion = SCHEMA;
	public String contentVersion;
	public String mapId;
	public long seed;
	/** Zero-based index of the next wave to play. */
	public int wave;
	public int gold;
	public int baseHealth;
	public int goldSpent;
	public int enemiesDefeated;
	public long ticks;
	public List<SavedDefender> defenders = new ArrayList<>();
}
