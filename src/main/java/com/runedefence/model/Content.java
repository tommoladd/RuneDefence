package com.runedefence.model;

/** All game data, loaded from content.json. */
public final class Content
{
	public String version;
	public DefenderDefinition[] defenders;
	public EnemyDefinition[] enemies;
	public MapDefinition[] maps;

	public DefenderDefinition defender(String id)
	{
		for (DefenderDefinition d : defenders)
		{
			if (d.id.equals(id))
			{
				return d;
			}
		}
		return null;
	}

	public EnemyDefinition enemy(String id)
	{
		for (EnemyDefinition e : enemies)
		{
			if (e.id.equals(id))
			{
				return e;
			}
		}
		return null;
	}

	public MapDefinition map(String id)
	{
		for (MapDefinition m : maps)
		{
			if (m.id.equals(id))
			{
				return m;
			}
		}
		return null;
	}
}
