package com.efficientmortimer;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.VarPlayer;
import net.runelite.api.widgets.Widget;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Efficient Mortimer",
	description = "Highlights efficient task choices for post-99 Slayer",
	tags = {"slayer", "mortimer", "ehp"}
)
public class EfficientMortimerPlugin extends Plugin
{
	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ConfigManager configManager;
	@Inject
	private EfficientMortimerConfig config;
	@Inject
	private Gson gson;
	@Inject
	private MortimerOffers offers;
	@Inject
	private EfficientMortimerOverlay overlay;
	@Inject
	private BraceletBankOverlay braceletBankOverlay;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private InfoBoxManager infoBoxManager;
	@Inject
	private ItemManager itemManager;

	private volatile boolean running;
	private volatile long profileId;
	private boolean refreshNeeded;
	private TaskRanking mainRanking;
	private TaskRanking ironmanRanking;
	private TaskRanking ironmanBarrageRanking;
	private TaskRanking customRanking;
	private RepeatInfoBox repeatInfoBox;
	private boolean repeatInfoBoxActive;
	private boolean loginFlag;
	private boolean slayerTaskActivityObserved;
	private boolean slayerTaskStateInitialized;
	private String lastMortimerTaskKey;
	private int previousSlayerCount;
	private int previousSlayerTaskSize;
	private int previousSlayerTaskCreature;
	private int previousSlayerMaster;
	private int previousSlayerModifier;
	private boolean expeditiousHighlightActive;
	private boolean slaughterHighlightActive;
	private volatile DisplayState displayState;

	@Override
	protected void startUp()
	{
		mainRanking = loadBundledRanking("main.json");
		ironmanRanking = loadBundledRanking("ironman.json");
		ironmanBarrageRanking = loadBundledRanking("ironman-barrage.json");
		repeatInfoBox = new RepeatInfoBox(itemManager.getImage(ItemID.SKILLCAPE_SLAYER), this);
		repeatInfoBoxActive = false;
		slayerTaskActivityObserved = false;
		slayerTaskStateInitialized = false;
		lastMortimerTaskKey = null;
		loginFlag = client.getGameState() == GameState.LOGGED_IN;
		expeditiousHighlightActive = false;
		slaughterHighlightActive = false;
		running = true;
		profileId = configManager.getProfile().getId();
		overlayManager.add(overlay);
		overlayManager.add(braceletBankOverlay);
		clientThread.invoke(() ->
		{
			if (running)
			{
				reloadCustomList();
				refreshNeeded = true;
	}
		});
	}

	@Override
	protected void shutDown()
	{
		running = false;
		setRepeatInfoBoxActive(false);
		setBraceletHighlights(false, false);
		repeatInfoBox = null;
		overlayManager.remove(braceletBankOverlay);
		overlayManager.remove(overlay);
		displayState = null;
		mainRanking = null;
		ironmanRanking = null;
		ironmanBarrageRanking = null;
		customRanking = null;
		refreshNeeded = false;
	}

	@Provides
	EfficientMortimerConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(EfficientMortimerConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!EfficientMortimerConfig.GROUP.equals(event.getGroup())
			|| configManager.getProfile().getId() != profileId)
		{
			return;
		}
		if ("customList".equals(event.getKey())
			|| "listMode".equals(event.getKey())
			|| "showBraceletBankHighlights".equals(event.getKey())
			|| "showRepeatInfoBox".equals(event.getKey()))
		{
			long changedProfile = profileId;
			boolean imported = "customList".equals(event.getKey());
			clientThread.invoke(() ->
			{
				if (running && configManager.getProfile().getId() == changedProfile)
				{
					if (imported)
					{
						reloadCustomList();
					}
					refreshNeeded = true;
				}
			});
		}
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		displayState = null;
		slayerTaskActivityObserved = false;
		slayerTaskStateInitialized = false;
		lastMortimerTaskKey = null;
		clientThread.invoke(() ->
		{
			if (running)
			{
				reloadCustomList();
				profileId = configManager.getProfile().getId();
				refreshNeeded = true;
			}
		});
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.SLAYER_TASK_CHOICE)
		{
			refreshNeeded = true;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.SLAYER_TASK_CHOICE)
		{
			displayState = null;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (displayState != null)
		{
			refreshNeeded = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		loginFlag = false;
		refreshNeeded = true;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if ((event.getMenuAction() != MenuAction.CC_OP && event.getMenuAction() != MenuAction.CC_OP_LOW_PRIORITY)
			|| !"Check".equals(event.getMenuOption()))
		{
			return;
		}

		Widget widget = client.getWidget(event.getParam1());
		if (widget == null)
		{
			return;
		}
		if (event.getParam0() != -1)
		{
			widget = widget.getChild(event.getParam0());
			if (widget == null)
			{
				return;
			}
		}

		int itemId = widget.getItemId();
		Widget[] children = widget.getDynamicChildren();
		if (children != null)
		{
			for (Widget child : children)
			{
				if (itemId == -1 && child != null)
				{
					itemId = child.getItemId();
				}
			}
		}

		itemId = ItemVariationMapping.map(itemId);
		if (itemId == ItemID.SLAYER_HELM || itemId == ItemID.SLAYER_RING_8 || itemId == ItemID.SLAYER_GEM)
		{
			slayerTaskActivityObserved = true;
			refreshNeeded = true;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.WORN)
		{
			refreshNeeded = true;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		displayState = null;
		if (isLoggedOutState(event.getGameState()))
		{
			loginFlag = true;
			slayerTaskActivityObserved = false;
			slayerTaskStateInitialized = false;
			lastMortimerTaskKey = null;
			setRepeatInfoBoxActive(false);
			setBraceletHighlights(false, false);
		}
		else if (event.getGameState() == GameState.LOGGED_IN)
		{
			loginFlag = true;
		}
		refreshNeeded = event.getGameState() == GameState.LOGGED_IN;
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (running && refreshNeeded)
		{
			refreshNeeded = false;
			refreshRecommendation();
		}
	}

	DisplayState getDisplayState()
	{
		return displayState;
	}

	boolean isExpeditiousHighlightActive()
	{
		return expeditiousHighlightActive;
	}

	boolean isSlaughterHighlightActive()
	{
		return slaughterHighlightActive;
	}

	private TaskRanking loadBundledRanking(String filename)
	{
		try (InputStream input = getClass().getResourceAsStream("/com/efficientmortimer/" + filename))
		{
			if (input == null)
			{
				throw new IllegalStateException("Missing bundled task list: " + filename);
			}
			return TaskRanking.parse(gson, new String(input.readAllBytes(), StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Could not read bundled task list: " + filename, e);
		}
	}

	private void reloadCustomList()
	{
		try
		{
			customRanking = TaskRanking.parse(gson, config.customList().trim());
		}
		catch (IllegalArgumentException e)
		{
			customRanking = null;
		}
	}

	private void refreshRecommendation()
	{
		GameState gameState = client.getGameState();
		if (configManager.getProfile().getId() != profileId)
		{
			setRepeatInfoBoxActive(false);
			setBraceletHighlights(false, false);
			displayState = null;
			return;
		}
		if (gameState != GameState.LOGGED_IN)
		{
			if (isLoggedOutState(gameState))
			{
				setRepeatInfoBoxActive(false);
			}
			setBraceletHighlights(false, false);
			displayState = null;
			return;
		}

		TaskRanking ranking = getActiveRanking();
		String taskKey = offers.currentTaskKey();
		if (taskKey != null)
		{
			lastMortimerTaskKey = taskKey;
		}
		observeSlayerTaskActivity();
		boolean slayerActivity = hasSlayerActivity();
		setRepeatInfoBoxActive(config.showRepeatInfoBox() && slayerTaskActivityObserved
			&& ranking != null && ranking.isRepeatKey(lastMortimerTaskKey));
		boolean braceletHighlightsEnabled = config.showBraceletBankHighlights();
		setBraceletHighlights(
			braceletHighlightsEnabled && slayerActivity && ranking != null && ranking.isExpeditiousKey(taskKey),
			braceletHighlightsEnabled && slayerActivity && ranking != null && ranking.isSlaughterKey(taskKey));
		if (ranking == null)
		{
			displayState = null;
			return;
		}

		MortimerOffers.Snapshot snapshot = offers.read();
		if (snapshot == null)
		{
			displayState = null;
			return;
		}
		if (!snapshot.isComplete())
		{
			displayState = new DisplayState(snapshot, null, snapshot.getMessage());
			return;
		}

		Recommendation recommendation = ranking.recommend(snapshot.getKeys(), client.getVarbitValue(VarbitID.SLAYER_POINTS));
		String message = null;
		if (recommendation.getAction() == Recommendation.Action.UNAVAILABLE)
		{
			message = config.listMode() == ListMode.CUSTOM ? "An offer is missing from this list. Import an updated ranking."
				: "An offer is missing from the " + Text.titleCase(config.listMode()) + " list.";
		}
		displayState = new DisplayState(snapshot, recommendation, message);
	}

	private TaskRanking getActiveRanking()
	{
		switch (config.listMode())
		{
			case MAIN:
				return mainRanking;
			case IRONMAN:
				return ironmanRanking;
			case IRONMAN_BARRAGE:
				return ironmanBarrageRanking;
			default:
				return customRanking;
		}
	}

	boolean hasSlayerActivity()
	{
		if (slayerTaskActivityObserved)
		{
			return true;
		}

		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment == null)
		{
			return false;
		}

		for (Item item : equipment.getItems())
		{
			if (item != null && ItemVariationMapping.map(item.getId()) == ItemID.SLAYER_HELM)
			{
				return true;
			}
		}

		return false;
	}

	private void observeSlayerTaskActivity()
	{
		int slayerCount = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		int taskSize = client.getVarpValue(VarPlayer.SLAYER_TASK_SIZE);
		int taskCreature = client.getVarpValue(VarPlayer.SLAYER_TASK_CREATURE);
		int slayerMaster = client.getVarbitValue(VarbitID.SLAYER_MASTER);
		int modifier = client.getVarbitValue(VarbitID.SLAYER_MODIFIER_ID);
		boolean taskChanged = slayerCount != previousSlayerCount
			|| taskSize != previousSlayerTaskSize
			|| taskCreature != previousSlayerTaskCreature
			|| slayerMaster != previousSlayerMaster
			|| modifier != previousSlayerModifier;
		boolean activeTask = slayerCount > 0 && taskSize > 0;
		boolean taskJustCompleted = previousSlayerCount > 0 && slayerCount == 0;

		if (!slayerTaskStateInitialized)
		{
			slayerTaskStateInitialized = true;
		}
		else if (!loginFlag && taskChanged && (activeTask || taskJustCompleted))
		{
			slayerTaskActivityObserved = true;
		}

		previousSlayerCount = slayerCount;
		previousSlayerTaskSize = taskSize;
		previousSlayerTaskCreature = taskCreature;
		previousSlayerMaster = slayerMaster;
		previousSlayerModifier = modifier;
	}

	private boolean isLoggedOutState(GameState gameState)
	{
		return gameState == GameState.LOGIN_SCREEN
			|| gameState == GameState.LOGIN_SCREEN_AUTHENTICATOR
			|| gameState == GameState.CONNECTION_LOST;
	}

	boolean isRepeatInfoBoxActive()
	{
		return repeatInfoBoxActive;
	}

	private void setRepeatInfoBoxActive(boolean active)
	{
		if (repeatInfoBox == null || repeatInfoBoxActive == active)
		{
			return;
		}

		repeatInfoBoxActive = active;
		if (active)
		{
			infoBoxManager.addInfoBox(repeatInfoBox);
		}
		else
		{
			infoBoxManager.removeInfoBox(repeatInfoBox);
		}
	}

	private void setBraceletHighlights(boolean expeditious, boolean slaughter)
	{
		expeditiousHighlightActive = expeditious;
		slaughterHighlightActive = slaughter;
	}

	static final class DisplayState
	{
		final MortimerOffers.Snapshot snapshot;
		final Recommendation recommendation;
		final String message;

		DisplayState(MortimerOffers.Snapshot snapshot, Recommendation recommendation, String message)
		{
			this.snapshot = snapshot;
			this.recommendation = recommendation;
			this.message = message;
		}
	}
}
