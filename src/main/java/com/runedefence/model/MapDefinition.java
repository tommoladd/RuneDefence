package com.runedefence.model;

import java.util.Collections;
import java.util.Map;

public final class MapDefinition
{
	/** Colours for painting the ground, as hex strings. */
	public static final class Palette
	{
		public String groundA;
		public String groundB;
		public String[] groundFlecks;
		public String path;
		public String pathEdge;
		public String[] pathFlecks;
	}

	public String id;
	public String name;
	/** Short lines shown on the map picker. */
	public String[] description = new String[0];
	public int requiredLevel = 1;
	public int columns;
	public int rows;
	/** One string per row. '.' is open ground; anything else is scenery and cannot be built on. */
	public String[] tiles;
	/** What to draw for each scenery letter, where it differs from the defaults. */
	public Map<String, String> scenery = Collections.emptyMap();
	public Palette palette;
	public boolean flowers;
	/** Lanes of ordered [column, row] waypoints. The ends of a lane may sit one tile off the grid. */
	public int[][][] paths;
	public WaveDefinition[] waves;
	/** Every monster this map can field. Waves may not use anything else. */
	public String[] monsters = new String[0];
	/** Waves worth celebrating on the way to the end. */
	public int[] milestones = new int[0];

	// The map's rules. The defaults change nothing.
	public int startingGold = 150;
	public int startingHealth = 20;
	public double enemySpeed = 1;
	public double enemyHealth = 1;
	public double extraMagicResist;
	public double defenderSpeed = 1;
	public double goldMultiplier = 1;
	/** Each kill heals other enemies within this radius by a share of their health. */
	public double deathHealRadius;
	public double deathHealPercent;
	/** If set, only one lane is open at a time, and the open lane changes every this many waves. */
	public int rotateLanesEvery;

	public boolean isMilestone(int wavesCleared)
	{
		for (int m : milestones)
		{
			if (m == wavesCleared)
			{
				return true;
			}
		}
		return false;
	}

	/** Can a square of this size sit with its top left corner here? */
	public boolean canFit(int col, int row, int size)
	{
		for (int r = row; r < row + size; r++)
		{
			for (int c = col; c < col + size; c++)
			{
				if (!isBuildable(c, r))
				{
					return false;
				}
			}
		}
		return true;
	}

	public boolean isOnPath(int col, int row)
	{
		for (int lane = 0; lane < paths.length; lane++)
		{
			if (isOnLane(lane, col, row))
			{
				return true;
			}
		}
		return false;
	}

	public boolean isOnLane(int laneIndex, int col, int row)
	{
		int[][] lane = paths[laneIndex];
		for (int i = 0; i + 1 < lane.length; i++)
		{
			int[] a = lane[i];
			int[] b = lane[i + 1];
			if (col >= Math.min(a[0], b[0]) && col <= Math.max(a[0], b[0])
				&& row >= Math.min(a[1], b[1]) && row <= Math.max(a[1], b[1]))
			{
				return true;
			}
		}
		return false;
	}

	public boolean isBuildable(int col, int row)
	{
		return col >= 0 && row >= 0 && col < columns && row < rows
			&& tiles[row].charAt(col) == '.' && !isOnPath(col, row);
	}

	public String sceneryFor(char letter, String fallback)
	{
		String sprite = scenery.get(String.valueOf(letter));
		return sprite != null ? sprite : fallback;
	}
}
