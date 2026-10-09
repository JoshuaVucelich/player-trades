package com.vucelich.playertrades.gui;

import java.util.ArrayList;
import java.util.List;

import com.vucelich.playertrades.escrow.ShulkerBoxes;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class TradeSources {
	public record ListedBox(boolean ender, int slot, ItemStack icon) {}

	private TradeSources() {}

	public static List<ItemStack> cells(ServerPlayer player, TradeMenu menu) {
		return switch (menu.tab()) {
			case INVENTORY -> inventoryPage(player.getInventory(), menu.page());
			case ENDER -> copies(player.getEnderChestInventory(), 0, ShulkerBoxes.SLOTS);
			case PICKER -> pickerPage(boxes(player), menu.page());
			case CONTENTS -> contents(player, menu.focusEnder(), menu.focusSlot());
		};
	}

	public static int pages(ServerPlayer player, TradeMenu menu) {
		return switch (menu.tab()) {
			case INVENTORY -> TradeLayout.PAGES;
			case PICKER -> Math.max(1, (boxes(player).size() + TradeLayout.SOURCE_SLOTS - 1) / TradeLayout.SOURCE_SLOTS);
			default -> 1;
		};
	}

	public static List<ListedBox> boxes(ServerPlayer player) {
		List<ListedBox> found = new ArrayList<>();
		collect(found, player.getInventory(), false, 36);
		collect(found, player.getEnderChestInventory(), true, ShulkerBoxes.SLOTS);
		return found;
	}

	public static ListedBox boxAt(ServerPlayer player, int page, int cell) {
		List<ListedBox> found = boxes(player);
		int index = page * TradeLayout.SOURCE_SLOTS + cell;
		if (index < 0 || index >= found.size()) {
			return null;
		}
		return found.get(index);
	}

	private static void collect(List<ListedBox> found, Container container, boolean ender, int limit) {
		int size = Math.min(limit, container.getContainerSize());
		for (int slot = 0; slot < size; slot++) {
			ItemStack stack = container.getItem(slot);
			if (!ShulkerBoxes.isBox(stack)) {
				continue;
			}
			ItemStack icon = stack.copy();
			String where = ender ? "Ender chest" : "Inventory";
			icon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(where + " slot " + (slot + 1)));
			found.add(new ListedBox(ender, slot, icon));
		}
	}

	private static List<ItemStack> inventoryPage(Container inventory, int page) {
		List<ItemStack> cells = new ArrayList<>();
		for (int cell = 0; cell < TradeLayout.SOURCE_SLOTS; cell++) {
			int index = TradeLayout.inventoryIndex(TradeLayout.SOURCE_START + cell, page);
			if (index < 0 || index >= inventory.getContainerSize()) {
				cells.add(ItemStack.EMPTY);
				continue;
			}
			ItemStack stack = inventory.getItem(index);
			cells.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
		}
		return cells;
	}

	private static List<ItemStack> copies(Container container, int start, int count) {
		List<ItemStack> cells = new ArrayList<>();
		for (int index = 0; index < count; index++) {
			int slot = start + index;
			if (slot >= container.getContainerSize()) {
				cells.add(ItemStack.EMPTY);
				continue;
			}
			ItemStack stack = container.getItem(slot);
			cells.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
		}
		return cells;
	}

	private static List<ItemStack> pickerPage(List<ListedBox> boxes, int page) {
		List<ItemStack> cells = new ArrayList<>();
		int start = page * TradeLayout.SOURCE_SLOTS;
		for (int cell = 0; cell < TradeLayout.SOURCE_SLOTS; cell++) {
			int index = start + cell;
			cells.add(index < boxes.size() ? boxes.get(index).icon() : ItemStack.EMPTY);
		}
		return cells;
	}

	private static List<ItemStack> contents(ServerPlayer player, boolean ender, int slot) {
		Container container = ender ? player.getEnderChestInventory() : player.getInventory();
		if (slot < 0 || slot >= container.getContainerSize() || !ShulkerBoxes.isBox(container.getItem(slot))) {
			return emptyPage();
		}
		return ShulkerBoxes.copySlots(container.getItem(slot));
	}

	private static List<ItemStack> emptyPage() {
		List<ItemStack> cells = new ArrayList<>();
		for (int index = 0; index < TradeLayout.SOURCE_SLOTS; index++) {
			cells.add(ItemStack.EMPTY);
		}
		return cells;
	}
}
