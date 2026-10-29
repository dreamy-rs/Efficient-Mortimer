package com.efficientmortimer;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TaskRankingTest
{
	private static final String FIRST = "abyssal-demon/unique";
	private static final String SECOND = "bloodveld/xp";
	private static final String THIRD = "aquanite/points";
	private static final Gson GSON = new Gson();

	@Test
	public void readsWebsiteExportFixture() throws IOException
	{
		try (InputStream input = getClass().getResourceAsStream("/efficient-mortimer-v1.json"))
		{
			TaskRanking ranking = TaskRanking.parse(GSON, new String(input.readAllBytes(), StandardCharsets.UTF_8));
			Recommendation result = ranking.recommend(List.of("nechryael/points", "dust-devil/xp"), 100);
			assertEquals(3, ranking.getSize());
			assertEquals(2, ranking.getAcceptedCount());
			assertEquals(Recommendation.Action.TAKE, result.getAction());
			assertEquals(1, result.getSlot());
		}
	}

	@Test
	public void bundledMainRankingPreservesExportCutoff() throws IOException
	{
		assertBundledCutoff("main.json", 85, "dark-beast/xp", "wyrm/quantity");
	}

	@Test
	public void bundledIronmanRankingPreservesExportCutoff() throws IOException
	{
		assertBundledCutoff("ironman.json", 84, "gryphons/quantity", "wyrm/unique");
	}

	@Test
	public void bundledIronmanBarrageRankingPreservesExportCutoff() throws IOException
	{
		assertBundledCutoff("ironman-barrage.json", 85, "kurask/unique", "aberrant-spectre/unique");
	}

	@Test
	public void takesHighestRankedAcceptableOffer()
	{
		TaskRanking ranking = parse(2);
		Recommendation result = ranking.recommend(List.of(THIRD, FIRST, SECOND), 100);

		assertEquals(Recommendation.Action.TAKE, result.getAction());
		assertEquals(1, result.getSlot());
		assertEquals(3, ranking.getSize());
		assertEquals(2, ranking.getAcceptedCount());
	}

	@Test
	public void skipsBelowCutoffAtExactlyOneHundredPoints()
	{
		Recommendation result = parse(0).recommend(List.of(THIRD, SECOND, FIRST), 100);

		assertEquals(Recommendation.Action.SKIP, result.getAction());
		assertEquals(2, result.getSlot());
	}

	@Test
	public void takesBestOfferWhenPointsAreInsufficient()
	{
		TaskRanking ranking = parse(0);
		for (int points : new int[]{0, 99})
		{
			Recommendation result = ranking.recommend(List.of(THIRD, SECOND, FIRST), points);
			assertEquals(Recommendation.Action.TAKE, result.getAction());
			assertEquals(2, result.getSlot());
		}
	}

	@Test
	public void cutoffUsesAnAcceptedPrefix()
	{
		TaskRanking ranking = parse(1);
		assertEquals(Recommendation.Action.TAKE,
			ranking.recommend(List.of(FIRST, THIRD, SECOND), 100).getAction());
		assertEquals(Recommendation.Action.SKIP,
			ranking.recommend(List.of(SECOND, THIRD, THIRD), 100).getAction());
		assertEquals(Recommendation.Action.TAKE,
			parse(3).recommend(List.of(THIRD, THIRD, THIRD), 100).getAction());
	}

	@Test
	public void missingOfferOrRankingEntryPreventsRecommendation()
	{
		TaskRanking ranking = parse(1);
		for (List<String> offers : Arrays.asList(
			Arrays.asList(FIRST, null, SECOND),
			List.of(FIRST, "new-task/xp", SECOND),
			List.of(FIRST, "wyrm/xp", SECOND),
			List.of(FIRST),
			List.<String>of()))
		{
			Recommendation result = ranking.recommend(offers, 100);
			assertEquals(Recommendation.Action.UNAVAILABLE, result.getAction());
			assertEquals(-1, result.getSlot());
		}
	}

	@Test
	public void supportsTwoOffersBeforeThirdCardUnlock()
	{
		Recommendation result = parse(2).recommend(List.of(THIRD, SECOND), 100);
		assertEquals(Recommendation.Action.TAKE, result.getAction());
		assertEquals(1, result.getSlot());
	}

	@Test
	public void unknownPointsDoNotChooseBetweenSkippingAndTaking()
	{
		assertEquals(Recommendation.Action.UNAVAILABLE,
			parse(0).recommend(List.of(FIRST, SECOND, THIRD), -1).getAction());
		assertEquals(Recommendation.Action.TAKE,
			parse(1).recommend(List.of(FIRST, SECOND, THIRD), -1).getAction());
	}

	@Test
	public void rejectsWrongExportTypeAndVersion()
	{
		assertInvalid(export(1).replace("efficient-mortimer", "other-plugin"), "not an Efficient Mortimer");
		assertInvalid(export(1).replace("\"version\":1", "\"version\":3"), "Unsupported export version");
		assertInvalid(export(1).replace("\"version\":1", "\"version\":\"1\""), "whole number");
		assertInvalid(export(1).replace("\"version\":1", "\"version\":1.5"), "whole number");
	}

	@Test
	public void rejectsInvalidCutoffs()
	{
		assertInvalid(export(-1), "outside the ranking");
		assertInvalid(export(4), "outside the ranking");
		for (String count : new String[]{"0.5", "\"1\"", "null", "true", "10000000000"})
		{
			assertInvalid(export(1).replace("\"acceptedCount\":1", "\"acceptedCount\":" + count), "whole number");
		}
	}

	@Test
	public void rejectsInvalidRankingEntries()
	{
		assertInvalid(export(1).replace(FIRST, SECOND), "Duplicate");
		for (String key : new String[]{"other-task/xp", "abyssal-demon/other", "abyssal-demon", "abyssal-demon/xp/unique"})
		{
			assertInvalid(export(1).replace(FIRST, key), "Unknown task or modifier");
		}
		assertInvalid(export(1).replace('"' + FIRST + '"', "null"), "Unknown task or modifier");
		assertInvalid(export(1).replace('"' + FIRST + '"', "42"), "Unknown task or modifier");
	}

	@Test
	public void rejectsEmptyMissingAndOversizedRankings()
	{
		assertInvalid("{\"type\":\"efficient-mortimer\",\"version\":1,\"acceptedCount\":0,\"ranking\":[]}", "1 to 145");
		assertInvalid("{\"type\":\"efficient-mortimer\",\"version\":1,\"acceptedCount\":0}", "missing its task ranking");
		String oversized = "{\"type\":\"efficient-mortimer\",\"version\":1,\"acceptedCount\":0,\"ranking\":["
			+ ("\"" + FIRST + "\",").repeat(145) + "\"" + FIRST + "\"]}";
		assertInvalid(oversized, "1 to 145");
		assertInvalid(" ".repeat(16_385) + export(1), "too long");
	}
	@Test
	public void supportsRepeatListInVersionOneAndTwo()
	{
		TaskRanking v1 = TaskRanking.parse(GSON,
			export(1).replace("\"version\":1", "\"version\":1,\"repeat\":[\"" + FIRST + "\"]"));
		assertTrue(v1.isRepeatKey(FIRST));
		assertTrue(!v1.isRepeatKey(SECOND));

		TaskRanking v2 = TaskRanking.parse(GSON,
			export(1).replace("\"version\":1", "\"version\":2,\"repeat\":[\"" + SECOND + "\"]"));
		assertTrue(v2.isRepeatKey(SECOND));
		assertTrue(!v2.isRepeatKey(FIRST));
	}

	@Test
	public void rejectsInvalidRepeatEntries()
	{
		assertInvalid(export(1).replace("\"version\":1", "\"version\":2,\"repeat\":\"" + FIRST + "\""),
			"Repeat list must be an array");
		assertInvalid(export(1).replace("\"version\":1", "\"version\":2,\"repeat\":[\"other-task/xp\"]"),
			"Unknown repeat task");
		assertInvalid(export(1).replace("\"version\":1", "\"version\":2,\"repeat\":[\"" + FIRST + "\",\"" + FIRST + "\"]"),
			"Duplicate repeat task");
		assertInvalid(export(1).replace("\"version\":1", "\"version\":2,\"repeat\":[\"venator/quantity\"]"),
			"missing from ranking");
	}
	@Test
	public void supportsExpeditiousAndSlaughterLists()
	{
		TaskRanking ranking = TaskRanking.parse(GSON,
			export(1).replace("\"version\":1",
				"\"version\":2,\"expeditious\":[\"" + FIRST + "\"],\"slaughter\":[\"" + SECOND + "\"]"));
		assertTrue(ranking.isExpeditiousKey(FIRST));
		assertTrue(!ranking.isExpeditiousKey(SECOND));
		assertTrue(ranking.isSlaughterKey(SECOND));
		assertTrue(!ranking.isSlaughterKey(FIRST));
	}

	@Test
	public void rejectsInvalidExpeditiousAndSlaughterEntries()
	{
		assertInvalid(export(1).replace("\"version\":1", "\"version\":2,\"expeditious\":\"" + FIRST + "\""),
			"Expeditious list must be an array");
		assertInvalid(export(1).replace("\"version\":1", "\"version\":2,\"expeditious\":[\"other-task/xp\"]"),
			"Unknown expeditious task");
		assertInvalid(export(1).replace("\"version\":1", "\"version\":2,\"expeditious\":[\"" + FIRST + "\",\"" + FIRST + "\"]"),
			"Duplicate expeditious task");
		assertInvalid(export(1).replace("\"version\":1", "\"version\":2,\"expeditious\":[\"venator/quantity\"]"),
			"Expeditious list contains entries missing from ranking");

		assertInvalid(export(1).replace("\"version\":1", "\"version\":2,\"slaughter\":\"" + FIRST + "\""),
			"Slaughter list must be an array");
		assertInvalid(export(1).replace("\"version\":1", "\"version\":2,\"slaughter\":[\"other-task/xp\"]"),
			"Unknown slaughter task");
		assertInvalid(export(1).replace("\"version\":1", "\"version\":2,\"slaughter\":[\"" + FIRST + "\",\"" + FIRST + "\"]"),
			"Duplicate slaughter task");
		assertInvalid(export(1).replace("\"version\":1", "\"version\":2,\"slaughter\":[\"venator/quantity\"]"),
			"Slaughter list contains entries missing from ranking");
	}

	@Test
	public void rejectsMalformedInput()
	{
		assertInvalid(null, "Paste a ranking");
		assertInvalid("  ", "Paste a ranking");
		for (String input : new String[]{"{", "[]", "null", "42", export(1) + " garbage", export(1).replace("\"version\"", "version")})
		{
			assertInvalid(input, "valid ranking export");
		}
	}

	private static void assertBundledCutoff(String filename, int acceptedCount, String lastAccepted, String firstSkipped) throws IOException
	{
		try (InputStream input = TaskRankingTest.class.getResourceAsStream("/com/efficientmortimer/" + filename))
		{
			TaskRanking ranking = TaskRanking.parse(GSON, new String(input.readAllBytes(), StandardCharsets.UTF_8));
			assertEquals(129, ranking.getSize());
			assertEquals(acceptedCount, ranking.getAcceptedCount());
			Recommendation accepted = ranking.recommend(List.of(firstSkipped, lastAccepted), 100);
			assertEquals(Recommendation.Action.TAKE, accepted.getAction());
			assertEquals(1, accepted.getSlot());
			List<String> belowCutoff = List.of("venator/quantity", firstSkipped);
			assertEquals(Recommendation.Action.SKIP, ranking.recommend(belowCutoff, 100).getAction());
			Recommendation fallback = ranking.recommend(belowCutoff, 99);
			assertEquals(Recommendation.Action.TAKE, fallback.getAction());
			assertEquals(1, fallback.getSlot());
		}
	}

	private static TaskRanking parse(int acceptedCount)
	{
		return TaskRanking.parse(GSON, export(acceptedCount));
	}

	private static String export(int acceptedCount)
	{
		return "{\"type\":\"efficient-mortimer\",\"version\":1,\"acceptedCount\":" + acceptedCount
			+ ",\"ranking\":[\"" + FIRST + "\",\"" + SECOND + "\",\"" + THIRD + "\"]}";
	}

	private static void assertInvalid(String input, String message)
	{
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> TaskRanking.parse(GSON, input));
		assertTrue(error.getMessage(), error.getMessage().contains(message));
	}
}




