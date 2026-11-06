package com.runedefence.engine;

/** Turns distance along a map's waypoints into a position, in tile units. */
final class PathTable
{
	private final double[] xs;
	private final double[] ys;
	private final double[] start;
	final double length;

	PathTable(int[][] waypoints)
	{
		int n = waypoints.length;
		xs = new double[n];
		ys = new double[n];
		start = new double[n];
		for (int i = 0; i < n; i++)
		{
			xs[i] = waypoints[i][0] + 0.5;
			ys[i] = waypoints[i][1] + 0.5;
			if (i > 0)
			{
				start[i] = start[i - 1] + Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
			}
		}
		length = start[n - 1];
	}

	void locate(double distance, EnemyInstance e)
	{
		int i = 0;
		while (i < xs.length - 2 && distance >= start[i + 1])
		{
			i++;
		}
		double t = (distance - start[i]) / (start[i + 1] - start[i]);
		t = Math.min(1, Math.max(0, t));
		e.x = xs[i] + (xs[i + 1] - xs[i]) * t;
		e.y = ys[i] + (ys[i + 1] - ys[i]) * t;
	}
}
