package com.efficientmortimer;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.overlay.infobox.InfoBox;

final class RepeatInfoBox extends InfoBox
{
	RepeatInfoBox(BufferedImage image, EfficientMortimerPlugin plugin)
	{
		super(image, plugin);
		setTooltip("Repeat task active");
	}

	@Override
	public String getText()
	{
		return "Repeat";
	}

	@Override
	public Color getTextColor()
	{
		return Color.GREEN;
	}
}
