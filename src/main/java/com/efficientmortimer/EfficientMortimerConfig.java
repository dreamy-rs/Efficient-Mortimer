package com.efficientmortimer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(EfficientMortimerConfig.GROUP)
public interface EfficientMortimerConfig extends Config {
	String GROUP = "efficientmortimer";

	@ConfigSection(name = "Custom import", description = "", position = 3)
	String customImport = "customImport";

	@ConfigItem(keyName = "listMode", name = "List", description = "", position = 2)
	default ListMode listMode() {
		return ListMode.MAIN;
	}

	@ConfigItem(keyName = "showBraceletBankHighlights", name = "Bracelet bank highlights", description = "Highlight Expeditious/Slaughter bracelets in bank when recommended", position = 0)
	default boolean showBraceletBankHighlights() {
		return true;
	}

	@ConfigItem(keyName = "showRepeatInfoBox", name = "Repeat task infobox", description = "Show the Repeat infobox when recommended", position = 1)
	default boolean showRepeatInfoBox() {
		return true;
	}

	@ConfigItem(keyName = "customList", name = "<html><div style='padding-bottom: 6px'>"
			+ "<table width='225' cellpadding='8' cellspacing='0' bgcolor='#3b3b3b'>"
			+ "<tr><td><div style='width: 145px; color: #dedede'>"
			+ "For Ironman plans, go to ehp.gg/slayer. Optionally import your stats and bank for a more accurate solve. "
			+ "Click 'Run Solver', then 'Copy for RuneLite'."
			+ "<p style='margin-top: 6px'>Select 'Custom', paste below, then click outside the field to apply.</p>"
			+ "</div></td></tr></table></div></html>", description = "", section = customImport, position = 0)
	default String customList() {
		return "";
	}
}
