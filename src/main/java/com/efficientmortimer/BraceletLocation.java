package com.efficientmortimer;

import java.util.Locale;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

final class BraceletLocation
{
	private static final int BANK_AREA_RADIUS = 12;
	private static final int MORTIMER_RADIUS = 10;

	private BraceletLocation()
	{
	}

	static boolean isAtMortimer(Client client)
	{
		if (client.getLocalPlayer() == null)
		{
			return false;
		}

		return isNearMortimer(client, client.getLocalPlayer().getWorldLocation());
	}

	static boolean isAtBank(Client client)
	{
		if (isBankOpen(client))
		{
			return true;
		}

		if (client.getLocalPlayer() == null)
		{
			return false;
		}

		return isNearBank(client, client.getLocalPlayer().getWorldLocation());
	}

	private static boolean isBankOpen(Client client)
	{
		Widget bankRoot = client.getWidget(InterfaceID.BANKMAIN);
		Widget bankItems = client.getWidget(InterfaceID.Bankmain.ITEMS);
		Widget sharedBankItems = client.getWidget(InterfaceID.SharedBank.ITEMS);
		return isVisible(bankRoot) || isVisible(bankItems) || isVisible(sharedBankItems);
	}

	private static boolean isVisible(Widget widget)
	{
		return widget != null && !widget.isHidden();
	}

	private static boolean isNearMortimer(Client client, WorldPoint playerLocation)
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return false;
		}

		Iterable<? extends NPC> npcs = worldView.npcs();
		if (npcs == null)
		{
			return false;
		}

		for (NPC npc : npcs)
		{
			if (npc != null && "Mortimer".equalsIgnoreCase(npc.getName())
				&& npc.getWorldLocation() != null
				&& npc.getWorldLocation().distanceTo(playerLocation) <= MORTIMER_RADIUS)
			{
				return true;
			}
		}

		return false;
	}

	private static boolean isNearBank(Client client, WorldPoint playerLocation)
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return false;
		}

		Scene scene = worldView.getScene();
		if (scene == null)
		{
			return false;
		}

		Tile[][][] tiles = scene.getTiles();
		int plane = worldView.getPlane();
		if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null)
		{
			return false;
		}

		for (Tile[] row : tiles[plane])
		{
			if (row == null)
			{
				continue;
			}

			for (Tile tile : row)
			{
				if (tile == null || tile.getWorldLocation() == null
					|| tile.getWorldLocation().distanceTo(playerLocation) > BANK_AREA_RADIUS)
				{
					continue;
				}

				GameObject[] objects = tile.getGameObjects();
				if (objects == null)
				{
					continue;
				}

				for (GameObject object : objects)
				{
					if (object == null)
					{
						continue;
					}

					ObjectComposition composition = client.getObjectDefinition(object.getId());
					String name = composition == null ? null : composition.getName();
					if (name != null && name.toLowerCase(Locale.ROOT).contains("bank"))
					{
						return true;
					}
				}
			}
		}

		return false;
	}
}
