package com.runedefence.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.runedefence.model.MatchSave;
import com.runedefence.model.Records;
import lombok.extern.slf4j.Slf4j;

/**
 * Per-account saves on top of a simple key/value store. Damaged or unknown data
 * is ignored, not thrown, so that a bad save can never stop the plugin starting.
 */
@Slf4j
public final class SaveRepository
{
	public interface Store
	{
		String get(String key);

		void set(String key, String value);

		void unset(String key);
	}

	static final String KEY_XP = "eligibleXp";
	static final String KEY_RECORDS = "records";
	static final String KEY_MATCH = "match";

	private final Store store;
	private final Gson gson;

	public SaveRepository(Store store, Gson gson)
	{
		this.store = store;
		this.gson = gson;
	}

	public long loadEligibleXp()
	{
		String value = store.get(KEY_XP);
		if (value == null)
		{
			return 0;
		}
		try
		{
			return Math.max(0, Long.parseLong(value));
		}
		catch (NumberFormatException e)
		{
			log.warn("Ignoring unreadable RuneDefence XP value");
			return 0;
		}
	}

	public void saveEligibleXp(long xp)
	{
		store.set(KEY_XP, Long.toString(xp));
	}

	public Records loadRecords()
	{
		Records records = read(KEY_RECORDS, Records.class);
		return records != null && records.schemaVersion == Records.SCHEMA && records.maps != null
			&& records.history != null ? records : new Records();
	}

	public void saveRecords(Records records)
	{
		store.set(KEY_RECORDS, gson.toJson(records));
	}

	public MatchSave loadMatch()
	{
		return read(KEY_MATCH, MatchSave.class);
	}

	public void saveMatch(MatchSave save)
	{
		if (save == null)
		{
			store.unset(KEY_MATCH);
		}
		else
		{
			store.set(KEY_MATCH, gson.toJson(save));
		}
	}

	private <T> T read(String key, Class<T> type)
	{
		String json = store.get(key);
		if (json == null)
		{
			return null;
		}
		try
		{
			return gson.fromJson(json, type);
		}
		catch (JsonParseException e)
		{
			log.warn("Ignoring damaged RuneDefence save entry: {}", key);
			return null;
		}
	}
}
