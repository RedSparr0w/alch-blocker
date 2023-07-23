package io.robrichardson.alchblocker;

import io.robrichardson.alchblocker.config.ListType;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;
import net.runelite.client.util.WildcardMatcher;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
		name = "Alch Blocker"
)
public class AlchBlockerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private AlchBlockerConfig config;

	Set<String> itemList = new HashSet<>();
	Set<Integer> hiddenItems = new HashSet<>();
	boolean isAlching = false;

	@Override
	protected void startUp() throws Exception {
		itemList = convertToListToSet();
	}

	@Override
	protected void shutDown() throws Exception {
		clientThread.invoke(this::showBlockedItems);
	}

	@Provides
	AlchBlockerConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(AlchBlockerConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!AlchBlockerConfig.GROUP.equals(event.getGroup())) return;
		itemList = convertToListToSet();
		if(isAlching) {
			clientThread.invoke(this::showBlockedItems);
			clientThread.invoke(this::hideBlockedItems);
		}
	}

	@Subscribe()
	public void onMenuOptionClicked(MenuOptionClicked event) {
		String menuTarget = Text.removeTags(event.getMenuTarget());
		isAlching = event.getMenuOption().contains("Alchemy") || (event.getMenuOption().equals("Cast") && menuTarget.contains("Level Alchemy"));
		// If item in our list of blocked items, don't allow the action
		if (isAlching && hiddenItems.contains(event.getItemId())) {
			event.consume();
		}
		if(!isAlching) {
			showBlockedItems();
		}
	}

	@Subscribe
	private void onScriptPostFired(ScriptPostFired event) {
		if (event.getScriptId() == ScriptID.INVENTORY_DRAWITEM && isAlching) {
			hideBlockedItems();
		}
	}

	@Subscribe
	private void onWidgetLoaded(WidgetLoaded event) {
		if (event.getGroupId() == WidgetID.EXPLORERS_RING_ALCH_GROUP_ID) {
			hideBlockedItems();
		}
	}

	@Subscribe
	public void onMenuOpened(final MenuOpened event)
	{
		// If the user has decided to disable the context menu, no need to process further
		if (!config.contextMenuEnabled()) {
			return;
		}

		final MenuEntry[] entries = event.getMenuEntries();
		for (int idx = entries.length - 1; idx >= 0; --idx)
		{
			final MenuEntry entry = entries[idx];
			final Widget w = entry.getWidget();

			if (w != null)
			{
				if (entry.getOption().contains("-Alchemy") || (entry.getOption().equals("Cast") && entry.getTarget().contains("Level Alchemy"))) {

					// Item already in block list, no need to add menu item
					if (
						(hiddenItems.contains(w.getItemId()) && config.listType() == ListType.BLACKLIST) ||
						(!hiddenItems.contains(w.getItemId()) && config.listType() == ListType.WHITELIST)
					) {
						return;
					}

					final String itemName = w.getName();

					client.createMenuEntry(idx)
						.setOption(config.listType() == ListType.BLACKLIST ? "Blacklist Alchemy" : "Whitelist Alchemy")
						.setTarget(itemName)
						.setType(MenuAction.RUNELITE)
						.onClick(e ->
						{
							configManager.setConfiguration(AlchBlockerConfig.GROUP, "itemList", config.itemList().concat("\n" + Text.removeTags(itemName)));
							showBlockedItems();
						});
				}
			}
		}
	}

	private void hideBlockedItems() {
		Widget inventory = client.getWidget(WidgetInfo.EXPLORERS_RING_ALCH_INVENTORY);
		if (inventory == null) {
			inventory = client.getWidget(WidgetInfo.INVENTORY);
			if (inventory == null) {
				return;
			}
		}

		for (Widget inventoryItem : Objects.requireNonNull(inventory.getChildren())) {
			String itemName = Text.removeTags(inventoryItem.getName()).toLowerCase();

			boolean isBlacklist = config.listType() != ListType.BLACKLIST;
			for(String blockedItem : itemList) {
				if(WildcardMatcher.matches(blockedItem, itemName)) {
					isBlacklist = config.listType() == ListType.BLACKLIST;
					break;
				}
			}

			if(isBlacklist) {
				inventoryItem.setOpacity(220);
				hiddenItems.add(inventoryItem.getItemId());
			}
		}
	}

	private void showBlockedItems() {
		if(hiddenItems.isEmpty()) {
			return;
		}

		Widget inventory = client.getWidget(WidgetInfo.EXPLORERS_RING_ALCH_INVENTORY);
		if (inventory == null) {
			inventory = client.getWidget(WidgetInfo.INVENTORY);
			if (inventory == null) {
				return;
			}
		}

		for (Widget inventoryItem : Objects.requireNonNull(inventory.getChildren())) {
			if(hiddenItems.contains(inventoryItem.getItemId())) {
				inventoryItem.setOpacity(0);
			}
		}

		hiddenItems.clear();

		// If we are still in the explorer ring interface, hide the items again
		if (WidgetInfo.EXPLORERS_RING_ALCH_INVENTORY.getId() == inventory.getId()) {
			hideBlockedItems();
		}
	}

	private Set<String> convertToListToSet() {
		Set<String> newItems = new HashSet<>();
		for (String listItem : config.itemList().split("\n")) {
			if (listItem.trim().equals("")) continue;

			if(listItem.contains(",")) {
				//For backwards compatibility, supports csv and line separated
				Set<String> csvSet = Text.fromCSV(listItem).stream()
						.map(String::toLowerCase)
						.collect(Collectors.toSet());
				newItems.addAll(csvSet);
			} else {
				newItems.add(listItem.toLowerCase().trim());
			}
		}

		return newItems;
	}
}
