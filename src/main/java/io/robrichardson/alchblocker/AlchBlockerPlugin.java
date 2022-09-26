package io.robrichardson.alchblocker;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@PluginDescriptor(
		name = "Alch Blocker"
)
public class AlchBlockerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private AlchBlockerConfig config;

	@Inject
	private ItemManager itemManager;

	List<Integer> hiddenItems = new ArrayList<>();

	@Provides
	AlchBlockerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AlchBlockerConfig.class);
	}

	@Subscribe()
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if(Objects.equals(event.getMenuOption(), "Cast") && (event.getMenuTarget().contains("High Level Alchemy") || event.getMenuTarget().contains("Low Level Alchemy"))) {
			hideBlockedItems();
		} else {
			showBlockedItems();
		}
	}

	private void hideBlockedItems() {
		Widget inventory = client.getWidget(WidgetInfo.INVENTORY);
		if (inventory == null) {
			return;
		}

		for (Widget inventoryItem : Objects.requireNonNull(inventory.getChildren())) {
			List<String> blockedItems = Text.fromCSV(config.blockedItems());
			for(String blockedItem : blockedItems) {
				if(inventoryItem.getName().toLowerCase().contains(blockedItem.toLowerCase())) {
					inventoryItem.setHidden(true);
					hiddenItems.add(inventoryItem.getId());
				}
			}
		}
	}

	private void showBlockedItems() {
		if(hiddenItems.isEmpty()) {
			return;
		}

		Widget inventory = client.getWidget(WidgetInfo.INVENTORY);
		if (inventory == null) {
			return;
		}

		for (Widget inventoryItem : Objects.requireNonNull(inventory.getChildren())) {
			if(hiddenItems.contains(inventoryItem.getId())) {
				inventoryItem.setHidden(false);
			}
		}

		hiddenItems.clear();
	}
}