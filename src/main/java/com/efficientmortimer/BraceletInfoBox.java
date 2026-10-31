package com.efficientmortimer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.util.AsyncBufferedImage;

final class BraceletInfoBox extends InfoBox
{
	BraceletInfoBox(BufferedImage image, EfficientMortimerPlugin plugin)
	{
		super(image, plugin);
		setBraceletImage(image);
	}

	void setBraceletImage(BufferedImage image)
	{
		if (image instanceof AsyncBufferedImage)
		{
			super.setImage(image);
			((AsyncBufferedImage) image).onLoaded(() -> super.setImage(withCheckmark(image)));
		}
		else
		{
			super.setImage(withCheckmark(image));
		}
	}

	private static BufferedImage withCheckmark(BufferedImage source)
	{
		if (source == null)
		{
			return null;
		}

		int width = source.getWidth();
		int height = source.getHeight();
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.drawImage(source, 0, 0, null);
		graphics.dispose();

		int size = Math.max(10, Math.min(14, Math.min(width, height) / 2));
		int x = width - size - 1;
		int y = height - size - 1;

		Path2D check = new Path2D.Double();
		check.moveTo(x + size * 0.12, y + size * 0.50);
		check.lineTo(x + size * 0.38, y + size * 0.77);
		check.lineTo(x + size * 0.88, y + size * 0.20);

		// Render only the checkmark at high resolution, then average pixel coverage for smooth edges.
		int scale = 16;
		BufferedImage highResolutionCheckmark = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_ARGB);
		Graphics2D checkGraphics = highResolutionCheckmark.createGraphics();
		checkGraphics.scale(scale, scale);
		checkGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		checkGraphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		checkGraphics.setColor(new Color(0, 200, 0));
		checkGraphics.setStroke(new BasicStroke(size / 3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		checkGraphics.draw(check);
		checkGraphics.dispose();

		BufferedImage checkmark = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		int samplesPerPixel = scale * scale;
		for (int pixelY = 0; pixelY < height; pixelY++)
		{
			for (int pixelX = 0; pixelX < width; pixelX++)
			{
				int alphaSum = 0;
				for (int sampleY = 0; sampleY < scale; sampleY++)
				{
					for (int sampleX = 0; sampleX < scale; sampleX++)
					{
						int highResolutionPixel = highResolutionCheckmark.getRGB(pixelX * scale + sampleX, pixelY * scale + sampleY);
						alphaSum += highResolutionPixel >>> 24;
					}
				}

				int alpha = (alphaSum + samplesPerPixel / 2) / samplesPerPixel;
				checkmark.setRGB(pixelX, pixelY, (alpha << 24) | 0x00C800);
			}
		}

		graphics = image.createGraphics();
		graphics.drawImage(checkmark, 0, 0, null);
		graphics.dispose();
		return image;
	}

	@Override
	public String getText()
	{
		return "";
	}

	@Override
	public Color getTextColor()
	{
		return Color.WHITE;
	}
}
