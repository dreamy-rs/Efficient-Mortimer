package com.efficientmortimer;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.util.Text;

final class MortimerOffers
{
	private static final int MORTIMER_MASTER_ID = 10;
	// The task-choice script creates ten children per offer and three for a locked row.
	private static final int CARD_CHILD_COUNT = 10;
	private static final int[] OFFER_VARBITS = {
		VarbitID.SLAYER_CHOOSE_TASK_1,
		VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_ID,
		VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_VALUE,
		VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_NEGATIVE,
		VarbitID.SLAYER_CHOOSE_TASK_2,
		VarbitID.SLAYER_CHOOSE_TASK_2_MODIFIER_ID,
		VarbitID.SLAYER_CHOOSE_TASK_2_MODIFIER_VALUE,
		VarbitID.SLAYER_CHOOSE_TASK_2_MODIFIER_NEGATIVE,
		VarbitID.SLAYER_CHOOSE_TASK_3,
		VarbitID.SLAYER_CHOOSE_TASK_3_MODIFIER_ID,
		VarbitID.SLAYER_CHOOSE_TASK_3_MODIFIER_VALUE,
		VarbitID.SLAYER_CHOOSE_TASK_3_MODIFIER_NEGATIVE
	};

	private final Client client;

	@Inject
	MortimerOffers(Client client)
	{
		this.client = client;
	}

	Snapshot read()
	{
		Widget content = content();
		if (content == null)
		{
			return null;
		}

		int[] state = readState();
		Widget[] children = content.getDynamicChildren();
		if (children == null || state[0] == 0 || state[4] == 0)
		{
			return Snapshot.incomplete(content, state, "Waiting for Mortimer's offers.");
		}

		int count = state[8] == 0 ? 2 : 3;
		int expectedChildren = count * CARD_CHILD_COUNT + (count == 2 ? 3 : 0);
		if (children.length != expectedChildren || (count == 2 && !lockedThirdChoice(children)))
		{
			return Snapshot.incomplete(content, state, "Unable to read Mortimer's offers.");
		}

		List<String> keys = new ArrayList<>(count);
		Widget[] cards = new Widget[count];
		String[] titles = new String[count];
		String[] modifiers = new String[count];
		for (int slot = 0; slot < count; slot++)
		{
			int child = slot * CARD_CHILD_COUNT;
			int variable = slot * 4;
			if (!visibleType(children[child], WidgetType.RECTANGLE)
				|| !visibleType(children[child + 1], WidgetType.TEXT)
				|| !visibleType(children[child + 2], WidgetType.TEXT)
				|| !visibleType(children[child + 6], WidgetType.TEXT)
				|| !plainText(children[child + 2]).startsWith("Amount: "))
			{
				return Snapshot.incomplete(content, state, "Unable to read Mortimer's offers.");
			}

			String taskName = taskName(state[variable]);
			String taskId = TaskCatalog.taskIdForName(taskName);
			String modifier = modifierId(state[variable + 1]);
			if (taskId == null || modifier == null || !TaskCatalog.isValidKey(taskId + "/" + modifier))
			{
				return Snapshot.incomplete(content, state, "Unrecognized Mortimer offer. Update Efficient Mortimer.");
			}

			String title = plainText(children[child + 1]);
			String modifierText = plainText(children[child + 6]);
			if (!taskName.equalsIgnoreCase(title)
				|| !modifierText(state[variable + 1], state[variable + 2], state[variable + 3]).equals(modifierText))
			{
				return Snapshot.incomplete(content, state, "Waiting for Mortimer's offers.");
			}

			keys.add(taskId + "/" + modifier);
			cards[slot] = children[child];
			titles[slot] = title;
			modifiers[slot] = modifierText;
		}
		return new Snapshot(content, state, keys, cards, titles, modifiers, null);
	}

	boolean isCurrent(Snapshot snapshot)
	{
		if (snapshot == null || content() != snapshot.content || !matchesState(snapshot.state))
		{
			return false;
		}
		for (int slot = 0; slot < snapshot.cards.length; slot++)
		{
			int child = slot * CARD_CHILD_COUNT;
			if (snapshot.content.getChild(child) != snapshot.cards[slot]
				|| snapshot.cards[slot].isHidden()
				|| !visibleType(snapshot.content.getChild(child + 1), WidgetType.TEXT)
				|| !visibleType(snapshot.content.getChild(child + 6), WidgetType.TEXT)
				|| !snapshot.titles[slot].equals(plainText(snapshot.content.getChild(child + 1)))
				|| !snapshot.modifiers[slot].equals(plainText(snapshot.content.getChild(child + 6))))
			{
				return false;
			}
		}
		return true;
	}

	Rectangle getBounds(Snapshot snapshot, int slot)
	{
		if (!isCurrent(snapshot) || slot < 0 || slot >= snapshot.cards.length)
		{
			return null;
		}
		return new Rectangle(snapshot.cards[slot].getBounds());
	}

	Rectangle getContentBounds(Snapshot snapshot)
	{
		return isCurrent(snapshot) ? new Rectangle(snapshot.content.getBounds()) : null;
	}

	String currentTaskKey()
	{
		if (client.getVarbitValue(VarbitID.SLAYER_MASTER) != MORTIMER_MASTER_ID
			|| client.getVarpValue(VarPlayerID.SLAYER_COUNT) <= 0
			|| client.getVarpValue(VarPlayerID.SLAYER_TARGET) <= 0)
		{
			return null;
		}

		String taskId = TaskCatalog.taskIdForName(taskName(client.getVarpValue(VarPlayerID.SLAYER_TARGET)));
		String modifier = modifierId(client.getVarbitValue(VarbitID.SLAYER_MODIFIER_ID));
		return taskId == null || modifier == null ? null : taskId + Character.toString('/') + modifier;
	}

	private Widget content()
	{
		Widget content = client.getWidget(InterfaceID.SlayerTaskChoice.CONTENT);
		return content == null || content.isHidden()
			|| client.getVarbitValue(VarbitID.SLAYER_CHOOSE_ROLLED_MASTER) != MORTIMER_MASTER_ID
			? null : content;
	}

	private int[] readState()
	{
		int[] state = new int[OFFER_VARBITS.length];
		for (int i = 0; i < OFFER_VARBITS.length; i++)
		{
			state[i] = client.getVarbitValue(OFFER_VARBITS[i]);
		}
		return state;
	}

	private boolean matchesState(int[] state)
	{
		for (int i = 0; i < OFFER_VARBITS.length; i++)
		{
			if (client.getVarbitValue(OFFER_VARBITS[i]) != state[i])
			{
				return false;
			}
		}
		return true;
	}

	private String taskName(int taskId)
	{
		List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, taskId);
		if (rows == null || rows.size() != 1)
		{
			return null;
		}
		Object[] names = client.getDBTableField(rows.get(0), DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0);
		return names != null && names.length == 1 && names[0] instanceof String ? (String) names[0] : null;
	}

	private static boolean lockedThirdChoice(Widget[] children)
	{
		String message = plainText(children[21]);
		return visibleType(children[20], WidgetType.RECTANGLE)
			&& visibleType(children[21], WidgetType.TEXT)
			&& (message.equals("Complete 50 tasks with Mortimer to unlock a third choice.")
				|| message.equals("You and your Slayer partner must complete 50 tasks with Mortimer to unlock a third choice."));
	}

	private static boolean visibleType(Widget widget, int type)
	{
		return widget != null && !widget.isHidden() && widget.getType() == type;
	}

	private static String plainText(Widget widget)
	{
		return widget == null || widget.getText() == null ? ""
			: Text.removeTags(widget.getText()).replace('\u00a0', ' ').trim();
	}

	private static String modifierId(int id)
	{
		switch (id)
		{
			case 1: return "points";
			case 2: return "quantity";
			case 3: return "clue";
			case 4: return "unique";
			case 5: return "xp";
			default: return null;
		}
	}

	private static String modifierText(int id, int value, int negative)
	{
		String amount = (id == 2 && negative == 1 ? "-" : "+") + value;
		switch (id)
		{
			case 1: return amount + " Slayer points";
			case 2: return amount + " Assigned";
			case 3: return amount + "% Clue chance";
			case 4: return amount + "% Superior unique chance";
			case 5: return amount + "% Slayer XP";
			default: return "";
		}
	}

	static final class Snapshot
	{
		private final Widget content;
		private final int[] state;
		private final List<String> keys;
		private final Widget[] cards;
		private final String[] titles;
		private final String[] modifiers;
		private final String message;

		private Snapshot(Widget content, int[] state, List<String> keys, Widget[] cards,
			String[] titles, String[] modifiers, String message)
		{
			this.content = content;
			this.state = state;
			this.keys = Collections.unmodifiableList(keys);
			this.cards = cards;
			this.titles = titles;
			this.modifiers = modifiers;
			this.message = message;
		}

		private static Snapshot incomplete(Widget content, int[] state, String message)
		{
			return new Snapshot(content, state, Collections.emptyList(), new Widget[0],
				new String[0], new String[0], message);
		}

		boolean isComplete()
		{
			return message == null;
		}

		List<String> getKeys()
		{
			return keys;
		}

		String getMessage()
		{
			return message;
		}
	}
}

