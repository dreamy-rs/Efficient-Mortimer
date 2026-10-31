package com.efficientmortimer;

import com.google.gson.Gson;
import com.google.inject.Guice;
import java.util.ArrayDeque;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PluginLifecycleTest
{
	private static final String GROUP = EfficientMortimerConfig.GROUP;
	private static final String VALID = export(1);
	private final Client client = mock(Client.class);
	private final ClientThread clientThread = mock(ClientThread.class);
	private final ConfigManager configManager = mock(ConfigManager.class);
	private final EfficientMortimerConfig config = mock(EfficientMortimerConfig.class);
	private final PluginManager pluginManager = mock(PluginManager.class);
	private final OverlayManager overlayManager = mock(OverlayManager.class);
	private final InfoBoxManager infoBoxManager = mock(InfoBoxManager.class);
	private final ItemManager itemManager = mock(ItemManager.class);
	private final EfficientMortimerPlugin plugin = new EfficientMortimerPlugin();
	private final Queue<Runnable> pending = new ArrayDeque<>();
	private final Map<Long, Map<String, String>> profiles = new HashMap<>();
	private final Map<Integer, Integer> variables = new HashMap<>();
	private long profileId = 1;

	@Before
	public void setUp()
	{
		ConfigProfile profile = mock(ConfigProfile.class);
		when(profile.getId()).thenAnswer(call -> profileId);
		when(configManager.getProfile()).thenReturn(profile);
		when(config.listMode()).thenReturn(ListMode.CUSTOM);
		when(config.customList()).thenAnswer(call -> settings().getOrDefault("customList", ""));
		when(config.showBraceletBankHighlights()).thenReturn(true);
		when(config.showRepeatInfoBox()).thenReturn(true);
		when(itemManager.getImage(ItemID.SKILLCAPE_SLAYER)).thenReturn(new AsyncBufferedImage(
			clientThread, 36, 32, BufferedImage.TYPE_INT_ARGB));
		when(pluginManager.getPlugins()).thenReturn(Collections.emptyList());
		doAnswer(call -> {
			pending.add(call.getArgument(0));
			return null;
		}).when(clientThread).invoke(any(Runnable.class));

		Guice.createInjector(binder -> {
			binder.bind(Client.class).toInstance(client);
			binder.bind(ClientThread.class).toInstance(clientThread);
			binder.bind(ConfigManager.class).toInstance(configManager);
			binder.bind(EfficientMortimerConfig.class).toInstance(config);
			binder.bind(PluginManager.class).toInstance(pluginManager);
			binder.bind(Gson.class).toInstance(new Gson());
			binder.bind(OverlayManager.class).toInstance(overlayManager);
			binder.bind(InfoBoxManager.class).toInstance(infoBoxManager);
			binder.bind(ItemManager.class).toInstance(itemManager);
			binder.bind(EfficientMortimerPlugin.class).toInstance(plugin);
		});

		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getVarbitValue(anyInt())).thenAnswer(call -> variables.getOrDefault(call.getArgument(0), 0));
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT))
			.thenAnswer(call -> variables.getOrDefault(call.getArgument(0), 0));
		variables.put(VarbitID.SLAYER_CHOOSE_ROLLED_MASTER, 10);
		variables.put(VarbitID.SLAYER_POINTS, 100);
		Widget content = mock(Widget.class);
		Widget[] children = new Widget[23];
		children[20] = widget(WidgetType.RECTANGLE, "");
		children[21] = widget(WidgetType.TEXT, "Complete 50 tasks with Mortimer to unlock a third choice.");
		when(client.getWidget(InterfaceID.SlayerTaskChoice.CONTENT)).thenReturn(content);
		when(content.getDynamicChildren()).thenReturn(children);
		offer(children, 0, 11, "Abyssal Demons", VarbitID.SLAYER_CHOOSE_TASK_1,
			VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_ID, VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_VALUE);
		offer(children, 10, 22, "Gargoyles", VarbitID.SLAYER_CHOOSE_TASK_2,
			VarbitID.SLAYER_CHOOSE_TASK_2_MODIFIER_ID, VarbitID.SLAYER_CHOOSE_TASK_2_MODIFIER_VALUE);
		settings().put("customList", VALID);
	}

	@After
	public void tearDown()
	{
		plugin.shutDown();
	}

	@Test
	public void readsValidRankingFromSettings()
	{
		start();
		assertEquals(VALID, settings().get("customList"));
		assertAction(Recommendation.Action.TAKE);
		assertEquals(0, plugin.getDisplayState().recommendation.getSlot());
	}

	@Test
	public void mainListWorksWithoutCustomImport()
	{
		settings().clear();
		when(config.listMode()).thenReturn(ListMode.MAIN);
		start();
		assertAction(Recommendation.Action.TAKE);
		assertEquals(0, plugin.getDisplayState().recommendation.getSlot());
	}

	@Test
	public void ironmanListWorksWithoutCustomImport()
	{
		settings().clear();
		when(config.listMode()).thenReturn(ListMode.IRONMAN);
		start();
		assertAction(Recommendation.Action.TAKE);
		assertEquals(0, plugin.getDisplayState().recommendation.getSlot());
	}

	@Test
	public void ironmanBarrageUsesItsOwnCutoffWithoutCustomImport()
	{
		settings().clear();
		Widget[] children = client.getWidget(InterfaceID.SlayerTaskChoice.CONTENT).getDynamicChildren();
		offer(children, 0, 11, "Aberrant Spectres", VarbitID.SLAYER_CHOOSE_TASK_1,
			VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_ID, VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_VALUE);
		when(config.listMode()).thenReturn(ListMode.IRONMAN_BARRAGE);
		start();
		assertAction(Recommendation.Action.TAKE);
		assertEquals(0, plugin.getDisplayState().recommendation.getSlot());
		selectMode(ListMode.IRONMAN);
		assertAction(Recommendation.Action.SKIP);
		selectMode(ListMode.IRONMAN_BARRAGE);
		assertAction(Recommendation.Action.TAKE);
		assertEquals(0, plugin.getDisplayState().recommendation.getSlot());
	}

	@Test
	public void switchingListsPreservesCustomImport()
	{
		String custom = export(0);
		settings().put("customList", custom);
		start();
		assertAction(Recommendation.Action.SKIP);
		selectMode(ListMode.MAIN);
		assertAction(Recommendation.Action.TAKE);
		assertEquals(0, plugin.getDisplayState().recommendation.getSlot());
		selectMode(ListMode.IRONMAN);
		assertAction(Recommendation.Action.TAKE);
		assertEquals(0, plugin.getDisplayState().recommendation.getSlot());
		selectMode(ListMode.IRONMAN_BARRAGE);
		assertAction(Recommendation.Action.TAKE);
		assertEquals(0, plugin.getDisplayState().recommendation.getSlot());
		selectMode(ListMode.CUSTOM);
		assertAction(Recommendation.Action.SKIP);
		assertEquals(custom, settings().get("customList"));
	}

	@Test
	public void switchingBetweenDefaultsUsesTheirOwnCutoffs()
	{
		Widget[] children = client.getWidget(InterfaceID.SlayerTaskChoice.CONTENT).getDynamicChildren();
		offer(children, 0, 11, "Bloodveld", VarbitID.SLAYER_CHOOSE_TASK_1,
			VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_ID, VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_VALUE);
		when(config.listMode()).thenReturn(ListMode.MAIN);
		start();
		assertAction(Recommendation.Action.TAKE);
		assertEquals(0, plugin.getDisplayState().recommendation.getSlot());
		selectMode(ListMode.IRONMAN);
		assertAction(Recommendation.Action.SKIP);
		selectMode(ListMode.MAIN);
		assertAction(Recommendation.Action.TAKE);
		assertEquals(0, plugin.getDisplayState().recommendation.getSlot());
	}

	@Test
	public void invalidImportClearsRecommendationAndValidImportRestoresIt()
	{
		start();
		settings().put("customList", "invalid");
		plugin.onConfigChanged(configChanged());
		drain();
		plugin.onClientTick(new ClientTick());
		assertNull(plugin.getDisplayState());
		assertEquals("invalid", settings().get("customList"));
		verify(configManager, never()).setConfiguration(eq(GROUP), anyString(), anyString());
		settings().put("customList", export(0));
		plugin.onConfigChanged(configChanged());
		drain();
		plugin.onClientTick(new ClientTick());
		assertAction(Recommendation.Action.SKIP);
	}

	@Test
	public void invalidSavedInputDoesNotAffectBuiltInLists()
	{
		settings().put("customList", "invalid");
		start();
		assertNull(plugin.getDisplayState());
		assertEquals("invalid", settings().get("customList"));
		selectMode(ListMode.MAIN);
		assertAction(Recommendation.Action.TAKE);
		selectMode(ListMode.CUSTOM);
		assertNull(plugin.getDisplayState());
	}

	@Test
	public void clearingImportClearsRecommendation()
	{
		start();
		settings().put("customList", " ");
		plugin.onConfigChanged(configChanged());
		drain();
		plugin.onClientTick(new ClientTick());
		assertEquals(" ", settings().get("customList"));
		assertNull(plugin.getDisplayState());
	}

	@Test
	public void profileSwitchNeverReusesPreviousRanking()
	{
		start();
		profileId = 2;
		plugin.onProfileChanged(new ProfileChanged());
		plugin.onGameTick(new GameTick());
		plugin.onClientTick(new ClientTick());
		assertNull(plugin.getDisplayState());
		drain();
		plugin.onClientTick(new ClientTick());
		assertNull(plugin.getDisplayState());
	}

	@Test
	public void queuedImportCannotReadOrWriteAnotherProfile()
	{
		start();
		plugin.onConfigChanged(configChanged());
		profileId = 2;
		settings().put("customList", export(0));
		plugin.onProfileChanged(new ProfileChanged());
		clearInvocations(config, configManager);
		pending.remove().run();
		verify(config, never()).customList();
		verify(configManager, never()).setConfiguration(eq(GROUP), anyString(), anyString());
		drain();
		plugin.onClientTick(new ClientTick());
		assertAction(Recommendation.Action.SKIP);
	}

	@Test
	public void profileSwitchWithInvalidInputClearsRecommendation()
	{
		start();
		profileId = 2;
		settings().put("customList", "invalid");
		plugin.onProfileChanged(new ProfileChanged());
		drain();
		plugin.onClientTick(new ClientTick());
		assertNull(plugin.getDisplayState());
	}

	@Test
	public void pointChangesRefreshTheRecommendation()
	{
		settings().put("customList", export(0));
		variables.put(VarbitID.SLAYER_POINTS, 99);
		start();
		assertAction(Recommendation.Action.TAKE);
		variables.put(VarbitID.SLAYER_POINTS, 100);
		plugin.onVarbitChanged(new VarbitChanged());
		plugin.onClientTick(new ClientTick());
		assertAction(Recommendation.Action.SKIP);
	}

	@Test
	public void swappingMortimerTaskImmediatelyUpdatesBraceletInfobox()
	{
		settings().put("customList", exportWithBraceletConditions());
		Color expeditiousColor = new Color(220, 30, 30);
		Color slaughterColor = new Color(20, 40, 220);
		when(itemManager.getImage(ItemID.EXPEDITIOUS_BRACELET)).thenReturn(asyncImageWithPixel(expeditiousColor));
		when(itemManager.getImage(ItemID.BRACELET_OF_SLAUGHTER)).thenReturn(asyncImageWithPixel(slaughterColor));
		setAtMortimer();
		assertTrue(BraceletLocation.isAtMortimer(client));
		start();

		openTaskChoice();
		setMortimerSlayerTask(20);
		closeTaskChoice();
		plugin.onGameTick(new GameTick());
		plugin.onClientTick(new ClientTick());

		assertEquals(20, client.getVarpValue(VarPlayerID.SLAYER_COUNT));
		assertEquals("abyssal-demon/points", new MortimerOffers(client).currentTaskKey());
		assertTrue(plugin.isExpeditiousHighlightActive());
		org.mockito.ArgumentCaptor<InfoBox> infoBoxCaptor = org.mockito.ArgumentCaptor.forClass(InfoBox.class);
		verify(infoBoxManager).addInfoBox(infoBoxCaptor.capture());
		BraceletInfoBox braceletInfoBox = (BraceletInfoBox) infoBoxCaptor.getValue();
		assertEquals(expeditiousColor.getRGB(), braceletInfoBox.getImage().getRGB(0, 0));

		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(322);
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL)).thenReturn(247);
		when(client.getVarpValue(VarPlayerID.SLAYER_TARGET)).thenReturn(22);
		variables.put(VarbitID.SLAYER_MODIFIER_ID, 2);
		VarbitChanged taskSwap = new VarbitChanged();
		taskSwap.setVarpId(VarPlayerID.SLAYER_TARGET);
		taskSwap.setValue(22);
		plugin.onVarbitChanged(taskSwap);
		plugin.onClientTick(new ClientTick());
		plugin.onClientTick(new ClientTick());

		assertTrue(plugin.isSlaughterHighlightActive());
		assertEquals("gargoyle/quantity", new MortimerOffers(client).currentTaskKey());
		assertEquals(expeditiousColor.getRGB(), braceletInfoBox.getImage().getRGB(0, 0));
		org.mockito.ArgumentCaptor<InfoBox> replacementCaptor = org.mockito.ArgumentCaptor.forClass(InfoBox.class);
		verify(infoBoxManager, times(2)).addInfoBox(replacementCaptor.capture());
		BraceletInfoBox replacement = (BraceletInfoBox) replacementCaptor.getAllValues().get(1);
		assertNotSame(braceletInfoBox, replacement);
		assertEquals(slaughterColor.getRGB(), replacement.getImage().getRGB(0, 0));
		verify(infoBoxManager).removeInfoBox(braceletInfoBox);
	}

	@Test
	public void swappingNechryaelForGryphonsAtMortimerImmediatelyUsesActiveTaskBracelet()
	{
		settings().put("customList", exportWithStoredTaskBracelets());
		Color expeditiousColor = new Color(220, 30, 30);
		Color slaughterColor = new Color(20, 40, 220);
		when(itemManager.getImage(ItemID.EXPEDITIOUS_BRACELET)).thenReturn(asyncImageWithPixel(expeditiousColor));
		when(itemManager.getImage(ItemID.BRACELET_OF_SLAUGHTER)).thenReturn(asyncImageWithPixel(slaughterColor));
		setAtMortimer();
		assertTrue(BraceletLocation.isAtMortimer(client));
		setSlayerTask(90, 90, 128, 4, 115);
		mapSlayerTask(128, "Nechryael");
		mapSlayerTask(52, "Gryphons");
		start();

		activateBraceletRecommendationsWithSlayerCheck();
		assertEquals("nechryael/unique", new MortimerOffers(client).currentTaskKey());
		assertTrue(plugin.isSlaughterHighlightActive());
		org.mockito.ArgumentCaptor<InfoBox> infoBoxCaptor = org.mockito.ArgumentCaptor.forClass(InfoBox.class);
		verify(infoBoxManager).addInfoBox(infoBoxCaptor.capture());
		BraceletInfoBox braceletInfoBox = (BraceletInfoBox) infoBoxCaptor.getValue();
		assertEquals(slaughterColor.getRGB(), braceletInfoBox.getImage().getRGB(0, 0));

		// Mortimer's task store swaps the active and stored assignment data in place.
		when(client.getVarpValue(VarPlayerID.SLAYER_TARGET)).thenReturn(52);
		plugin.onClientTick(new ClientTick());
		assertEquals(slaughterColor.getRGB(), braceletInfoBox.getImage().getRGB(0, 0));

		setSlayerTask(322, 247, 52, 2, 75);
		variables.put(VarbitID.SLAYER_MODIFIER_ID_STORED, 4);
		variables.put(VarbitID.SLAYER_MODIFIER_VALUE_STORED, 115);
		plugin.onClientTick(new ClientTick());
		assertEquals(slaughterColor.getRGB(), braceletInfoBox.getImage().getRGB(0, 0));
		plugin.onClientTick(new ClientTick());

		assertEquals("gryphons/quantity", new MortimerOffers(client).currentTaskKey());
		assertTrue(plugin.isExpeditiousHighlightActive());
		assertFalse(plugin.isSlaughterHighlightActive());
		assertEquals(slaughterColor.getRGB(), braceletInfoBox.getImage().getRGB(0, 0));
		org.mockito.ArgumentCaptor<InfoBox> replacementCaptor = org.mockito.ArgumentCaptor.forClass(InfoBox.class);
		verify(infoBoxManager, times(2)).addInfoBox(replacementCaptor.capture());
		BraceletInfoBox replacement = (BraceletInfoBox) replacementCaptor.getAllValues().get(1);
		assertNotSame(braceletInfoBox, replacement);
		assertEquals(expeditiousColor.getRGB(), replacement.getImage().getRGB(0, 0));
		verify(infoBoxManager).removeInfoBox(braceletInfoBox);
	}

	@Test
	public void loginTaskVarHydrationDoesNotShowBraceletUntilExplicitActivity()
	{
		settings().put("customList", exportWithBraceletConditions());
		setAtMortimer();
		start();

		GameStateChanged loggedOut = mock(GameStateChanged.class);
		when(loggedOut.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		plugin.onGameStateChanged(loggedOut);
		GameStateChanged loading = mock(GameStateChanged.class);
		when(loading.getGameState()).thenReturn(GameState.LOADING);
		plugin.onGameStateChanged(loading);
		GameStateChanged loggedIn = mock(GameStateChanged.class);
		when(loggedIn.getGameState()).thenReturn(GameState.LOGGED_IN);
		plugin.onGameStateChanged(loggedIn);
		plugin.onClientTick(new ClientTick());

		setMortimerSlayerTask(20);
		VarbitChanged restoredTaskVars = new VarbitChanged();
		restoredTaskVars.setVarpId(VarPlayerID.SLAYER_TARGET);
		restoredTaskVars.setValue(11);
		plugin.onVarbitChanged(restoredTaskVars);
		plugin.onClientTick(new ClientTick());

		assertFalse(plugin.isExpeditiousHighlightActive());
		verify(infoBoxManager, never()).addInfoBox(any(InfoBox.class));

		Widget checkedItem = mock(Widget.class);
		when(checkedItem.getItemId()).thenReturn(ItemID.SLAYER_GEM);
		when(checkedItem.getDynamicChildren()).thenReturn(new Widget[0]);
		when(client.getWidget(1)).thenReturn(checkedItem);
		MenuOptionClicked checkEvent = mock(MenuOptionClicked.class);
		when(checkEvent.getMenuAction()).thenReturn(MenuAction.CC_OP);
		when(checkEvent.getMenuOption()).thenReturn("Check");
		when(checkEvent.getParam0()).thenReturn(-1);
		when(checkEvent.getParam1()).thenReturn(1);
		plugin.onMenuOptionClicked(checkEvent);
		plugin.onClientTick(new ClientTick());

		assertTrue(plugin.isExpeditiousHighlightActive());
		verify(infoBoxManager).addInfoBox(any(BraceletInfoBox.class));
	}

	@Test
	public void currentTaskKeyRequiresMortimerAsTheSlayerMaster()
	{
		setMortimerSlayerTask(20);
		MortimerOffers offers = new MortimerOffers(client);
		assertEquals("abyssal-demon/points", offers.currentTaskKey());

		variables.put(VarbitID.SLAYER_MASTER, 9);
		assertNull(offers.currentTaskKey());
	}

	@Test
	public void repeatAndBraceletFeaturesRequireSlayerActivityAndRespectRepeatToggle()
	{
		settings().put("customList", exportWithRepeatAndBraceletConditions());
		start();
		assertFalse(plugin.isRepeatInfoBoxActive());
		assertFalse(plugin.isExpeditiousHighlightActive());

		openTaskChoice();
		setMortimerSlayerTask(20);
		closeTaskChoice();
		assertEquals(10, client.getVarbitValue(VarbitID.SLAYER_MASTER));
		assertEquals(20, client.getVarpValue(VarPlayerID.SLAYER_COUNT));
		assertEquals(11, client.getVarpValue(VarPlayerID.SLAYER_TARGET));
		assertEquals("abyssal-demon/points", new MortimerOffers(client).currentTaskKey());
		plugin.onGameTick(new GameTick());
		plugin.onClientTick(new ClientTick());
		assertFalse(plugin.isRepeatInfoBoxActive());
		assertTrue(plugin.isExpeditiousHighlightActive());

		when(config.showRepeatInfoBox()).thenReturn(false);
		ConfigChanged event = configChanged();
		event.setKey("showRepeatInfoBox");
		plugin.onConfigChanged(event);
		drain();
		plugin.onClientTick(new ClientTick());
		assertFalse(plugin.isRepeatInfoBoxActive());
		assertTrue(plugin.isExpeditiousHighlightActive());

		variables.put(VarPlayerID.SLAYER_COUNT, 0);
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(0);
		plugin.onGameTick(new GameTick());
		plugin.onClientTick(new ClientTick());
		assertFalse(plugin.isExpeditiousHighlightActive());
	}

	@Test
	public void existingTaskAtLaunchDoesNotActivateRepeatUntilTaskProgresses()
	{
		settings().put("customList", exportWithRepeatAndBraceletConditions());
		setMortimerSlayerTask(20);
		ItemContainer equipment = mock(ItemContainer.class);
		when(equipment.getItems()).thenReturn(new Item[]{new Item(ItemID.SLAYER_HELM_I_HYDRA, 1)});
		when(client.getItemContainer(InventoryID.WORN)).thenReturn(equipment);

		start();

		assertFalse(plugin.isRepeatInfoBoxActive());
		plugin.onGameTick(new GameTick());
		plugin.onClientTick(new ClientTick());
		assertFalse(plugin.isRepeatInfoBoxActive());

		variables.put(VarPlayerID.SLAYER_COUNT, 19);
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(19);
		plugin.onGameTick(new GameTick());
		plugin.onClientTick(new ClientTick());

		assertFalse(plugin.isRepeatInfoBoxActive());
		variables.put(VarbitID.SLAYER_MASTER, 0);
		plugin.onGameTick(new GameTick());
		plugin.onClientTick(new ClientTick());
		assertFalse(plugin.isRepeatInfoBoxActive());
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(0);
		plugin.onGameTick(new GameTick());
		plugin.onClientTick(new ClientTick());
		assertTrue(plugin.isRepeatInfoBoxActive());
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(19);
		plugin.onGameTick(new GameTick());
		plugin.onClientTick(new ClientTick());
		assertFalse(plugin.isRepeatInfoBoxActive());

		variables.put(VarPlayerID.SLAYER_COUNT, 0);
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(0);
		plugin.onGameTick(new GameTick());
		plugin.onClientTick(new ClientTick());
		assertTrue(plugin.isRepeatInfoBoxActive());

		variables.put(VarPlayerID.SLAYER_COUNT, 10);
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(10);
		when(client.getVarpValue(VarPlayerID.SLAYER_TARGET)).thenReturn(22);
		variables.put(VarbitID.SLAYER_MASTER, 10);
		plugin.onGameTick(new GameTick());
		plugin.onClientTick(new ClientTick());
		plugin.onClientTick(new ClientTick());
		assertFalse(plugin.isRepeatInfoBoxActive());
	}

	@Test
	public void checkingSlayerItemActivatesRepeatForExistingMortimerTask()
	{
		settings().put("customList", exportWithRepeatAndBraceletConditions());
		setMortimerSlayerTask(20);
		start();
		assertFalse(plugin.isRepeatInfoBoxActive());

		Widget checkedItem = mock(Widget.class);
		when(checkedItem.getItemId()).thenReturn(ItemID.SLAYER_GEM);
		when(checkedItem.getDynamicChildren()).thenReturn(new Widget[0]);
		when(client.getWidget(1)).thenReturn(checkedItem);
		MenuOptionClicked event = mock(MenuOptionClicked.class);
		when(event.getMenuAction()).thenReturn(MenuAction.CC_OP);
		when(event.getMenuOption()).thenReturn("Check");
		when(event.getParam0()).thenReturn(-1);
		when(event.getParam1()).thenReturn(1);

		plugin.onMenuOptionClicked(event);
		plugin.onClientTick(new ClientTick());

		assertFalse(plugin.isRepeatInfoBoxActive());
	}

	@Test
	public void taskCompletionActivatesRepeatForTheNextAssignment()
	{
		settings().put("customList", exportWithRepeatAndBraceletConditions());
		setMortimerSlayerTask(1);
		start();
		assertFalse(plugin.isRepeatInfoBoxActive());

		plugin.onGameTick(new GameTick());
		plugin.onClientTick(new ClientTick());
		variables.put(VarPlayerID.SLAYER_COUNT, 0);
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(0);
		plugin.onGameTick(new GameTick());
		plugin.onClientTick(new ClientTick());

		assertTrue(plugin.isRepeatInfoBoxActive());
	}

	@Test
	public void repeatInfoBoxSurvivesTransientLoadingWhenLeavingTaskArea()
	{
		settings().put("customList", exportWithRepeatAndBraceletConditions());
		setMortimerSlayerTask(20);
		start();
		plugin.onGameTick(new GameTick());
		plugin.onClientTick(new ClientTick());
		variables.put(VarPlayerID.SLAYER_COUNT, 0);
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(0);
		plugin.onGameTick(new GameTick());
		plugin.onClientTick(new ClientTick());
		assertTrue(plugin.isRepeatInfoBoxActive());

		when(client.getGameState()).thenReturn(GameState.LOADING);
		GameStateChanged loading = mock(GameStateChanged.class);
		when(loading.getGameState()).thenReturn(GameState.LOADING);
		plugin.onGameStateChanged(loading);
		plugin.onClientTick(new ClientTick());
		assertTrue(plugin.isRepeatInfoBoxActive());

		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		GameStateChanged loggedIn = mock(GameStateChanged.class);
		when(loggedIn.getGameState()).thenReturn(GameState.LOGGED_IN);
		plugin.onGameStateChanged(loggedIn);
		plugin.onClientTick(new ClientTick());
		assertTrue(plugin.isRepeatInfoBoxActive());
	}

	@Test
	public void slayerActivityRecognizesSlayerHelmVariations()
	{
		ItemContainer equipment = mock(ItemContainer.class);
		Item hydraSlayerHelm = new Item(ItemID.SLAYER_HELM_I_HYDRA, 1);
		when(equipment.getItems()).thenReturn(new Item[]{hydraSlayerHelm});
		when(client.getItemContainer(InventoryID.WORN)).thenReturn(equipment);

		assertTrue(plugin.hasSlayerActivity());
	}

	private void selectMode(ListMode mode)
	{
		when(config.listMode()).thenReturn(mode);
		ConfigChanged event = configChanged();
		event.setKey("listMode");
		plugin.onConfigChanged(event);
		drain();
		plugin.onClientTick(new ClientTick());
	}

	private void start()
	{
		plugin.startUp();
		drain();
		plugin.onClientTick(new ClientTick());
	}

	private void drain()
	{
		while (!pending.isEmpty())
		{
			pending.remove().run();
		}
	}

	private Map<String, String> settings()
	{
		return profiles.computeIfAbsent(profileId, id -> new HashMap<>());
	}

	private void assertAction(Recommendation.Action expected)
	{
		assertNotNull(plugin.getDisplayState());
		assertNotNull(plugin.getDisplayState().recommendation);
		assertEquals(expected, plugin.getDisplayState().recommendation.getAction());
	}

	private void setAtMortimer()
	{
		WorldPoint location = new WorldPoint(3200, 3200, 0);
		Player player = mock(Player.class);
		NPC mortimer = mock(NPC.class);
		WorldView worldView = mock(WorldView.class);
		@SuppressWarnings("rawtypes")
		IndexedObjectSet npcs = mock(IndexedObjectSet.class);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldLocation()).thenReturn(location);
		when(client.getTopLevelWorldView()).thenReturn(worldView);
		doReturn(npcs).when(worldView).npcs();
		doAnswer(call -> Collections.singleton(mortimer).iterator()).when(npcs).iterator();
		when(mortimer.getName()).thenReturn("Mortimer");
		when(mortimer.getWorldLocation()).thenReturn(location);
	}

	private void openTaskChoice()
	{
		WidgetLoaded event = new WidgetLoaded();
		event.setGroupId(InterfaceID.SLAYER_TASK_CHOICE);
		plugin.onWidgetLoaded(event);
	}

	private void closeTaskChoice()
	{
		WidgetClosed event = new WidgetClosed(InterfaceID.SLAYER_TASK_CHOICE, 0, true);
		plugin.onWidgetClosed(event);
	}

	private AsyncBufferedImage asyncImageWithPixel(Color color)
	{
		AsyncBufferedImage image = new AsyncBufferedImage(clientThread, 36, 32, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, color.getRGB());
		return image;
	}

	private void setMortimerSlayerTask(int amount)
	{
		variables.put(VarPlayerID.SLAYER_COUNT, amount);
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(amount);
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL)).thenReturn(amount);
		when(client.getVarpValue(VarPlayerID.SLAYER_TARGET)).thenReturn(11);
		variables.put(VarbitID.SLAYER_MASTER, 10);
		variables.put(VarbitID.SLAYER_MODIFIER_ID, 1);
	}

	private void setSlayerTask(int count, int originalCount, int target, int modifierId, int modifierValue)
	{
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(count);
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL)).thenReturn(originalCount);
		when(client.getVarpValue(VarPlayerID.SLAYER_TARGET)).thenReturn(target);
		variables.put(VarbitID.SLAYER_MASTER, 10);
		variables.put(VarbitID.SLAYER_MODIFIER_ID, modifierId);
		variables.put(VarbitID.SLAYER_MODIFIER_VALUE, modifierValue);
	}

	private void mapSlayerTask(int target, String name)
	{
		when(client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, target))
			.thenReturn(Collections.singletonList(target));
		when(client.getDBTableField(target, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0))
			.thenReturn(new Object[]{name});
	}

	private void activateBraceletRecommendationsWithSlayerCheck()
	{
		Widget checkedItem = mock(Widget.class);
		when(checkedItem.getItemId()).thenReturn(ItemID.SLAYER_GEM);
		when(checkedItem.getDynamicChildren()).thenReturn(new Widget[0]);
		when(client.getWidget(1)).thenReturn(checkedItem);
		MenuOptionClicked checkEvent = mock(MenuOptionClicked.class);
		when(checkEvent.getMenuAction()).thenReturn(MenuAction.CC_OP);
		when(checkEvent.getMenuOption()).thenReturn("Check");
		when(checkEvent.getParam0()).thenReturn(-1);
		when(checkEvent.getParam1()).thenReturn(1);
		plugin.onMenuOptionClicked(checkEvent);
		plugin.onClientTick(new ClientTick());
	}

	private void offer(Widget[] children, int child, int id, String name, int taskVarbit, int modifierVarbit, int valueVarbit)
	{
		variables.put(taskVarbit, id);
		variables.put(modifierVarbit, 1);
		variables.put(valueVarbit, 15);
		children[child] = widget(WidgetType.RECTANGLE, "");
		children[child + 1] = widget(WidgetType.TEXT, name);
		children[child + 2] = widget(WidgetType.TEXT, "Amount: 80 to 120");
		children[child + 6] = widget(WidgetType.TEXT, "+15 Slayer points");
		when(client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, id))
			.thenReturn(Collections.singletonList(id));
		when(client.getDBTableField(id, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)).thenReturn(new Object[]{name});
	}

	private static Widget widget(int type, String text)
	{
		Widget widget = mock(Widget.class);
		when(widget.getType()).thenReturn(type);
		when(widget.getText()).thenReturn(text);
		return widget;
	}

	private static ConfigChanged configChanged()
	{
		ConfigChanged event = new ConfigChanged();
		event.setGroup(GROUP);
		event.setKey("customList");
		return event;
	}

	private static String export(int acceptedCount)
	{
		return "{\"type\":\"efficient-mortimer\",\"version\":1,\"acceptedCount\":" + acceptedCount
			+ ",\"ranking\":[\"abyssal-demon/points\",\"gargoyle/points\"]}";
	}

	private static String exportWithRepeatAndBraceletConditions()
	{
		return export(1).replace("\"version\":1",
			"\"version\":2,\"repeat\":[\"abyssal-demon/points\"],"
				+ "\"expeditious\":[\"abyssal-demon/points\"],\"slaughter\":[]");
	}

	private static String exportWithBraceletConditions()
	{
		return "{\"type\":\"efficient-mortimer\",\"version\":2,\"acceptedCount\":1,"
			+ "\"ranking\":[\"abyssal-demon/points\",\"gargoyle/quantity\"],"
			+ "\"expeditious\":[\"abyssal-demon/points\"],"
			+ "\"slaughter\":[\"gargoyle/quantity\"]}";
	}

	private static String exportWithStoredTaskBracelets()
	{
		return "{\"type\":\"efficient-mortimer\",\"version\":2,\"acceptedCount\":1,"
			+ "\"ranking\":[\"nechryael/unique\",\"gryphons/quantity\"],"
			+ "\"expeditious\":[\"gryphons/quantity\"],"
			+ "\"slaughter\":[\"nechryael/unique\"]}";
	}
}



