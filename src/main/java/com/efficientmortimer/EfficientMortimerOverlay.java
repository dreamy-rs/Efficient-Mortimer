package com.efficientmortimer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

final class EfficientMortimerOverlay extends Overlay {
	private static final Color HIGHLIGHT = Color.CYAN;
	private static final Color FILL = new Color(0, 255, 255, 22);
	private static final Color NOTICE_BACKGROUND = new Color(48, 200, 215, 150);
	private static final Font FONT = FontManager.getRunescapeFont();
	private static final int NOTICE_WIDTH = 240;
	private static final int PADDING = ComponentConstants.STANDARD_BORDER;
	private static final int GAP = 8;
	private static final BasicStroke OUTLINE = new BasicStroke(2);
	private final Client client;
	private final EfficientMortimerPlugin plugin;
	private final MortimerOffers offers;
	private final PanelComponent noticePanel = new PanelComponent();

	@Inject
	EfficientMortimerOverlay(Client client, EfficientMortimerPlugin plugin, MortimerOffers offers) {
		this.client = client;
		this.plugin = plugin;
		this.offers = offers;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		EfficientMortimerPlugin.DisplayState state = plugin.getDisplayState();
		if (state == null) {
			return null;
		}

		Graphics2D g = (Graphics2D) graphics.create();
		try {
			Recommendation recommendation = state.recommendation;
			if (recommendation != null && recommendation.getAction() == Recommendation.Action.TAKE) {
				Rectangle bounds = offers.getBounds(state.snapshot, recommendation.getSlot());
				if (bounds != null) {
					g.setColor(FILL);
					g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
					g.setColor(HIGHLIGHT);
					g.setStroke(OUTLINE);
					g.drawRect(bounds.x + 1, bounds.y + 1, bounds.width - 2, bounds.height - 2);
				}
			} else {
				boolean skip = recommendation != null && recommendation.getAction() == Recommendation.Action.SKIP;
				String message = skip ? null : state.message;
				Rectangle content = offers.getContentBounds(state.snapshot);
				if (content != null && (skip || message != null)) {
					Widget frame = client.getWidget(InterfaceID.SlayerTaskChoice.FRAME);
					Rectangle anchor = frame != null && !frame.isHidden() ? frame.getBounds() : content;
					noticePanel.setBackgroundColor(
							skip ? NOTICE_BACKGROUND : ComponentConstants.STANDARD_BACKGROUND_COLOR);
					drawNotice(g, anchor, skip ? "Skip these offers" : "No recommendation", message);
				}
			}
		} finally {
			g.dispose();
		}
		return null;
	}

	private void drawNotice(Graphics2D graphics, Rectangle anchor, String title, String message) {
		graphics.setFont(FONT);
		FontMetrics metrics = graphics.getFontMetrics();
		int maxWidth = Math.min(NOTICE_WIDTH, client.getCanvasWidth() - GAP * 2);
		List<String> lines = wrap(title, metrics, maxWidth - PADDING * 2);
		if (message != null) {
			lines.addAll(wrap(message, metrics, maxWidth - PADDING * 2));
		}
		int textWidth = 0;
		for (String line : lines) {
			textWidth = Math.max(textWidth, metrics.stringWidth(line));
		}
		int width = textWidth + PADDING * 2;
		int height = PADDING * 2 + metrics.getHeight() * lines.size();
		int x = Math.max(GAP, Math.min(anchor.x + (anchor.width - width) / 2, client.getCanvasWidth() - width - GAP));
		int y = anchor.y + anchor.height + GAP;
		if (y + height > client.getCanvasHeight() - GAP) {
			y = anchor.y - height - GAP;
			if (y < GAP) {
				x = anchor.x + anchor.width + GAP;
				if (x + width > client.getCanvasWidth() - GAP) {
					x = anchor.x - width - GAP;
				}
				if (x < GAP) {
					return;
				}
				y = Math.max(GAP, Math.min(anchor.y, client.getCanvasHeight() - height - GAP));
			}
		}
		noticePanel.setPreferredLocation(new Point(x, y));
		noticePanel.setPreferredSize(new Dimension(width, 0));
		noticePanel.getChildren().clear();
		for (String line : lines) {
			noticePanel.getChildren().add(LineComponent.builder().left(line).build());
		}
		noticePanel.render(graphics);
	}

	private static List<String> wrap(String text, FontMetrics metrics, int width) {
		List<String> lines = new ArrayList<>();
		String line = "";
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && metrics.stringWidth(next) > width) {
				lines.add(line);
				line = word;
			} else {
				line = next;
			}
		}
		lines.add(line);
		return lines;
	}
}
