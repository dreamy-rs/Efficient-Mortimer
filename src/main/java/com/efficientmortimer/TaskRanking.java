package com.efficientmortimer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TaskRanking
{
	static final int SKIP_COST = 100;
	private static final int MAX_IMPORT_LENGTH = 16_384;
	private final Map<String, Integer> ranks;
	private final Set<String> repeatKeys;
	private final Set<String> expeditiousKeys;
	private final Set<String> slaughterKeys;
	private final int acceptedCount;

	private TaskRanking(Map<String, Integer> ranks, Set<String> repeatKeys,
		Set<String> expeditiousKeys, Set<String> slaughterKeys, int acceptedCount)
	{
		this.ranks = Map.copyOf(ranks);
		this.repeatKeys = Set.copyOf(repeatKeys);
		this.expeditiousKeys = Set.copyOf(expeditiousKeys);
		this.slaughterKeys = Set.copyOf(slaughterKeys);
		this.acceptedCount = acceptedCount;
	}

	static TaskRanking parse(Gson gson, String input)
	{
		if (input == null || input.isBlank())
		{
			throw new IllegalArgumentException("Paste a ranking copied from ehp.gg.");
		}
		if (input.length() > MAX_IMPORT_LENGTH)
		{
			throw new IllegalArgumentException("Ranking is too long.");
		}

		JsonObject data;
		try (JsonReader reader = new JsonReader(new StringReader(input)))
		{
			data = gson.getAdapter(JsonObject.class).read(reader);
			if (data == null || reader.peek() != JsonToken.END_DOCUMENT)
			{
				throw new IllegalArgumentException("Paste a valid ranking export.");
			}
		}
		catch (IOException | JsonParseException | IllegalStateException e)
		{
			throw new IllegalArgumentException("Paste a valid ranking export.");
		}

		if (!"efficient-mortimer".equals(stringValue(data.get("type"))))
		{
			throw new IllegalArgumentException("This is not an Efficient Mortimer export.");
		}

		int version = integerValue(data.get("version"), "Version");
		if (version < 1 || version > 2)
		{
			throw new IllegalArgumentException("Unsupported export version. Update Efficient Mortimer.");
		}

		int acceptedCount = integerValue(data.get("acceptedCount"), "Accepted count");
		JsonElement ranking = data.get("ranking");
		if (ranking == null || !ranking.isJsonArray())
		{
			throw new IllegalArgumentException("Export is missing its task ranking.");
		}
		JsonArray entries = ranking.getAsJsonArray();
		if (entries.size() == 0 || entries.size() > TaskCatalog.getMaxRankingSize())
		{
			throw new IllegalArgumentException("Ranking must contain 1 to " + TaskCatalog.getMaxRankingSize() + " entries.");
		}
		if (acceptedCount < 0 || acceptedCount > entries.size())
		{
			throw new IllegalArgumentException("Accepted count is outside the ranking.");
		}

		Map<String, Integer> ranks = new HashMap<>();
		for (int i = 0; i < entries.size(); i++)
		{
			String key = stringValue(entries.get(i));
			if (!TaskCatalog.isValidKey(key))
			{
				throw new IllegalArgumentException("Unknown task or modifier at entry " + (i + 1) + ".");
			}
			if (ranks.putIfAbsent(key, i) != null)
			{
				throw new IllegalArgumentException("Duplicate task and modifier at entry " + (i + 1) + ".");
			}
		}

		Set<String> repeatKeys = parseKeySet(data.get("repeat"), "Repeat list", "repeat task");
		Set<String> expeditiousKeys = parseKeySet(data.get("expeditious"), "Expeditious list", "expeditious task");
		Set<String> slaughterKeys = parseKeySet(data.get("slaughter"), "Slaughter list", "slaughter task");
		validateKeysInRanking(ranks, repeatKeys, "Repeat list");
		validateKeysInRanking(ranks, expeditiousKeys, "Expeditious list");
		validateKeysInRanking(ranks, slaughterKeys, "Slaughter list");

		return new TaskRanking(ranks, repeatKeys, expeditiousKeys, slaughterKeys, acceptedCount);
	}

	int getSize()
	{
		return ranks.size();
	}

	int getAcceptedCount()
	{
		return acceptedCount;
	}

	boolean isRepeatKey(String key)
	{
		return key != null && repeatKeys.contains(key);
	}

	boolean isExpeditiousKey(String key)
	{
		return key != null && expeditiousKeys.contains(key);
	}

	boolean isSlaughterKey(String key)
	{
		return key != null && slaughterKeys.contains(key);
	}

	Recommendation recommend(List<String> offerKeys, int points)
	{
		if (offerKeys == null || offerKeys.size() < 2 || offerKeys.size() > 3)
		{
			return Recommendation.unavailable();
		}
		int bestSlot = -1;
		int bestRank = Integer.MAX_VALUE;
		for (int slot = 0; slot < offerKeys.size(); slot++)
		{
			String key = offerKeys.get(slot);
			Integer rank = key == null ? null : ranks.get(key);
			if (rank == null)
			{
				return Recommendation.unavailable();
			}
			if (rank < bestRank)
			{
				bestSlot = slot;
				bestRank = rank;
			}
		}
		if (bestRank < acceptedCount)
		{
			return new Recommendation(Recommendation.Action.TAKE, bestSlot);
		}
		if (points < 0)
		{
			return Recommendation.unavailable();
		}
		return new Recommendation(points >= SKIP_COST ? Recommendation.Action.SKIP : Recommendation.Action.TAKE,
			bestSlot);
	}

	private static Set<String> parseKeySet(JsonElement value, String listName, String keyName)
	{
		if (value == null)
		{
			return Set.of();
		}
		if (!value.isJsonArray())
		{
			throw new IllegalArgumentException(listName + " must be an array.");
		}

		JsonArray array = value.getAsJsonArray();
		Set<String> keys = new HashSet<>();
		for (int i = 0; i < array.size(); i++)
		{
			String key = stringValue(array.get(i));
			if (!TaskCatalog.isValidKey(key))
			{
				throw new IllegalArgumentException("Unknown " + keyName + " at entry " + (i + 1) + ".");
			}
			if (!keys.add(key))
			{
				throw new IllegalArgumentException("Duplicate " + keyName + " at entry " + (i + 1) + ".");
			}
		}
		return keys;
	}

	private static void validateKeysInRanking(Map<String, Integer> ranks, Set<String> keys, String listName)
	{
		if (!ranks.keySet().containsAll(keys))
		{
			throw new IllegalArgumentException(listName + " contains entries missing from ranking.");
		}
	}

	private static String stringValue(JsonElement value)
	{
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
			? value.getAsString() : null;
	}

	private static int integerValue(JsonElement value, String field)
	{
		if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber())
		{
			try
			{
				return new BigDecimal(value.getAsString()).intValueExact();
			}
			catch (NumberFormatException | ArithmeticException e)
			{
				throw new IllegalArgumentException(field + " must be a whole number.");
			}
		}
		throw new IllegalArgumentException(field + " must be a whole number.");
	}
}
