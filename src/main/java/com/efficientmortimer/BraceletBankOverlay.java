package com.efficientmortimer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

final class BraceletBankOverlay extends Overlay
{
	private static final Color HIGHLIGHT = new Color(66, 133, 244);
	private static final BasicStroke OUTLINE = new BasicStroke(2);
	private static final String EXPEDITIOUS_NAME = "expeditious bracelet";
	private static final String SLAUGHTER_NAME = "bracelet of slaughter";
	private static final int HIGHLIGHT_X_OFFSET = -6;
	private static final int HIGHLIGHT_Y_OFFSET = -1;
	private static final int BANK_ITEM_WIDTH = 36;
	private static final int BANK_ITEM_HEIGHT = 32;
	private static final String BANK_TAG_LAYOUTS_PLUGIN = "com.banktaglayouts.BankTagLayoutsPlugin";

	private final Client client;
	private final EfficientMortimerPlugin plugin;
	private final PluginManager pluginManager;

	@Inject
	BraceletBankOverlay(Client client, EfficientMortimerPlugin plugin, PluginManager pluginManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.pluginManager = pluginManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		drawAfterLayer(InterfaceID.Bankmain.ITEMS);
		drawAfterLayer(InterfaceID.SharedBank.ITEMS);
		drawAfterInterface(InterfaceID.BANKMAIN);
		drawAfterInterface(InterfaceID.BANKSIDE);
		drawAfterInterface(InterfaceID.BANK_DEPOSITBOX);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.isExpeditiousHighlightActive() && !plugin.isSlaughterHighlightActive())
		{
			return null;
		}

		renderContainer(graphics, client.getWidget(InterfaceID.Bankmain.ITEMS));
		renderContainer(graphics, client.getWidget(InterfaceID.SharedBank.ITEMS));
		renderContainer(graphics, client.getWidget(InterfaceID.BANKMAIN));
		renderContainer(graphics, client.getWidget(InterfaceID.BANKSIDE));
		renderContainer(graphics, client.getWidget(InterfaceID.BANK_DEPOSITBOX));
		renderBankTagLayoutItems(graphics);
		return null;
	}

	private void renderBankTagLayoutItems(Graphics2D graphics)
	{
		Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (container == null || container.isHidden())
		{
			return;
		}

		Plugin bankTagLayouts = findBankTagLayoutsPlugin();
		if (bankTagLayouts == null)
		{
			return;
		}

		try
		{
			Method getCurrentLayoutableThing = bankTagLayouts.getClass().getDeclaredMethod("getCurrentLayoutableThing");
			getCurrentLayoutableThing.setAccessible(true);
			Object currentLayoutableThing = getCurrentLayoutableThing.invoke(bankTagLayouts);
			if (currentLayoutableThing == null)
			{
				return;
			}

			Method getBankOrder = bankTagLayouts.getClass().getDeclaredMethod("getBankOrder", currentLayoutableThing.getClass());
			getBankOrder.setAccessible(true);
			if (getBankOrder.invoke(bankTagLayouts, currentLayoutableThing) == null)
			{
				return;
			}

			Field fakeItemsField = bankTagLayouts.getClass().getField("fakeItems");
			Collection<?> fakeItems = (Collection<?>) fakeItemsField.get(bankTagLayouts);
			if (fakeItems == null || fakeItems.isEmpty())
			{
				return;
			}

			Method getItemId = fakeItems.iterator().next().getClass().getMethod("getItemId");
			Method getIndex = fakeItems.iterator().next().getClass().getMethod("getIndex");
			Method isLayoutPlaceholder = fakeItems.iterator().next().getClass().getMethod("isLayoutPlaceholder");
			Method getXForIndex = bankTagLayouts.getClass().getDeclaredMethod("getXForIndex", int.class);
			Method getYForIndex = bankTagLayouts.getClass().getDeclaredMethod("getYForIndex", int.class);
			Field configField = bankTagLayouts.getClass().getDeclaredField("config");
			configField.setAccessible(true);
			Object bankTagLayoutsConfig = configField.get(bankTagLayouts);
			Method showLayoutPlaceholders = bankTagLayoutsConfig.getClass().getMethod("showLayoutPlaceholders");
			getXForIndex.setAccessible(true);
			getYForIndex.setAccessible(true);

			Point canvasLocation = container.getCanvasLocation();
			int relativeY = 0;
			for (Widget current = container; current.getParent() != null; current = current.getParent())
			{
				relativeY += current.getRelativeY();
			}

			Rectangle clip = new Rectangle(canvasLocation.getX() + 45, relativeY,
				container.getWidth() - 45, container.getHeight());
			Graphics2D clippedGraphics = (Graphics2D) graphics.create();
			try
			{
				clippedGraphics.clip(clip);
				for (Object fakeItem : fakeItems)
				{
					if ((boolean) isLayoutPlaceholder.invoke(fakeItem)
						&& !(boolean) showLayoutPlaceholders.invoke(bankTagLayoutsConfig))
					{
						continue;
					}

					int itemId = ((Number) getItemId.invoke(fakeItem)).intValue();
					if (!isBraceletHighlightActive(itemId))
					{
						continue;
					}

					int index = ((Number) getIndex.invoke(fakeItem)).intValue();
					int x = canvasLocation.getX() + ((Number) getXForIndex.invoke(null, index)).intValue();
					int y = relativeY - container.getScrollY()
						+ ((Number) getYForIndex.invoke(null, index)).intValue();
					drawBounds(clippedGraphics, new Rectangle(x, y, BANK_ITEM_WIDTH, BANK_ITEM_HEIGHT));
				}
			}
			finally
			{
				clippedGraphics.dispose();
			}
		}
		catch (ReflectiveOperationException | ClassCastException e)
		{
			return;
		}
	}

	private Plugin findBankTagLayoutsPlugin()
	{
		for (Plugin candidate : pluginManager.getPlugins())
		{
			if (BANK_TAG_LAYOUTS_PLUGIN.equals(candidate.getClass().getName()))
			{
				return candidate;
			}
		}

		return null;
	}

	private boolean isBraceletHighlightActive(int itemId)
	{
		return (plugin.isExpeditiousHighlightActive() && itemNameStartsWith(itemId, EXPEDITIOUS_NAME))
			|| (plugin.isSlaughterHighlightActive() && itemNameStartsWith(itemId, SLAUGHTER_NAME));
	}

	private void renderContainer(Graphics2D graphics, Widget container)
	{
		if (container == null || container.isHidden())
		{
			return;
		}

		renderWidgets(graphics, container.getDynamicChildren());
		renderWidgets(graphics, container.getStaticChildren());
		renderWidgets(graphics, container.getNestedChildren());
		renderWidgets(graphics, container.getChildren());
	}

	private void renderWidgets(Graphics2D graphics, Widget[] widgets)
	{
		if (widgets == null)
		{
			return;
		}

		Set<Integer> expeditiousItemSignatures = new HashSet<>();
		Set<Integer> expeditiousSpriteSignatures = new HashSet<>();
		Set<Integer> slaughterItemSignatures = new HashSet<>();
		Set<Integer> slaughterSpriteSignatures = new HashSet<>();

		for (Widget widget : widgets)
		{
			if (widget == null || widget.isHidden())
			{
				continue;
			}

			if (plugin.isExpeditiousHighlightActive() && isExpeditiousDirect(widget))
			{
				addSignatures(widget, expeditiousItemSignatures, expeditiousSpriteSignatures);
			}

			if (plugin.isSlaughterHighlightActive() && isSlaughterDirect(widget))
			{
				addSignatures(widget, slaughterItemSignatures, slaughterSpriteSignatures);
			}
		}

		for (Widget widget : widgets)
		{
			if (widget == null || widget.isHidden())
			{
				continue;
			}

			boolean highlightExpeditious = plugin.isExpeditiousHighlightActive()
				&& (isExpeditiousDirect(widget) || matchesSignatures(widget, expeditiousItemSignatures, expeditiousSpriteSignatures));
			boolean highlightSlaughter = plugin.isSlaughterHighlightActive()
				&& (isSlaughterDirect(widget) || matchesSignatures(widget, slaughterItemSignatures, slaughterSpriteSignatures));
			if (!highlightExpeditious && !highlightSlaughter)
			{
				continue;
			}

			Rectangle bounds = widget.getBounds();
			if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
			{
				continue;
			}

			drawBounds(graphics, bounds);
		}
	}

	private void drawBounds(Graphics2D graphics, Rectangle bounds)
	{
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
		{
			return;
		}

		int x = bounds.x + HIGHLIGHT_X_OFFSET;
		int y = bounds.y + HIGHLIGHT_Y_OFFSET;
		int width = bounds.width;
		int height = bounds.height;

		graphics.setColor(HIGHLIGHT);
		graphics.setStroke(OUTLINE);
		graphics.drawRect(x, y, width - 1, height - 1);
	}

	private boolean isExpeditiousDirect(Widget widget)
	{
		return nameStartsWith(widget, EXPEDITIOUS_NAME);
	}

	private boolean isSlaughterDirect(Widget widget)
	{
		return nameStartsWith(widget, SLAUGHTER_NAME);
	}

	private static void addSignatures(Widget widget, Set<Integer> itemSignatures, Set<Integer> spriteSignatures)
	{
		if (widget.getItemId() > 0)
		{
			itemSignatures.add(widget.getItemId());
		}

		if (widget.getSpriteId() > 0)
		{
			spriteSignatures.add(widget.getSpriteId());
		}
	}

	private static boolean matchesSignatures(Widget widget, Set<Integer> itemSignatures, Set<Integer> spriteSignatures)
	{
		int itemId = widget.getItemId();
		if (itemId > 0 && itemSignatures.contains(itemId))
		{
			return true;
		}

		int spriteId = widget.getSpriteId();
		return spriteId > 0 && spriteSignatures.contains(spriteId);
	}

	private boolean nameStartsWith(Widget widget, String prefix)
	{
		if (widgetTextContains(widget, prefix))
		{
			return true;
		}

		if (itemNameStartsWith(widget.getItemId(), prefix))
		{
			return true;
		}

		return itemNameStartsWith(widget.getSpriteId(), prefix);
	}

	private static boolean widgetTextContains(Widget widget, String needle)
	{
		String widgetName = normalizeName(widget.getName());
		if (widgetName.contains(needle))
		{
			return true;
		}

		String widgetText = normalizeName(widget.getText());
		if (widgetText.contains(needle))
		{
			return true;
		}

		String[] actions = widget.getActions();
		if (actions == null)
		{
			return false;
		}

		for (String action : actions)
		{
			if (normalizeName(action).contains(needle))
			{
				return true;
			}
		}

		return false;
	}

	private boolean itemNameStartsWith(int itemId, String prefix)
	{
		if (itemId <= 0)
		{
			return false;
		}

		ItemComposition item = client.getItemDefinition(itemId);
		if (item == null)
		{
			return false;
		}

		return normalizeName(item.getName()).startsWith(prefix);
	}

	private static String normalizeName(String value)
	{
		if (value == null)
		{
			return "";
		}

		return Text.removeTags(value).toLowerCase(Locale.ROOT);
	}
}
