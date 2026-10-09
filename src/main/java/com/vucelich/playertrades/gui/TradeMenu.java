package com.vucelich.playertrades.gui;

import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * Vanilla 6-row chest. The client already knows {@link MenuType#GENERIC_9x6}, so Java and Geyser can draw it.
 */
public final class TradeMenu extends ChestMenu {
	private final UUID viewerId;
	private final TradeActions actions;
	private final SimpleContainer display;
	private SourceTab tab = SourceTab.INVENTORY;
	private int page;
	private int pageCount = TradeLayout.PAGES;
	private int focusSlot = -1;
	private boolean focusEnder;
	private boolean closeHandled;

	public TradeMenu(int syncId, Inventory playerInventory, UUID viewerId, TradeActions actions) {
		this(syncId, playerInventory, viewerId, actions, new SimpleContainer(54));
	}

	private TradeMenu(int syncId, Inventory playerInventory, UUID viewerId, TradeActions actions, SimpleContainer display) {
		super(MenuType.GENERIC_9x6, syncId, playerInventory, display, 6);
		this.viewerId = viewerId;
		this.actions = actions;
		this.display = display;
		actions.opened(this);
	}

	public UUID viewerId() {
		return viewerId;
	}

	public int page() {
		return page;
	}

	public SourceTab tab() {
		return tab;
	}

	public int focusSlot() {
		return focusSlot;
	}

	public boolean focusEnder() {
		return focusEnder;
	}

	public void showInventory() {
		tab = SourceTab.INVENTORY;
		page = 0;
	}

	public void showEnder() {
		tab = SourceTab.ENDER;
		page = 0;
	}

	public void showPicker() {
		tab = SourceTab.PICKER;
		page = 0;
	}

	public void showContents(int slot, boolean ender) {
		tab = SourceTab.CONTENTS;
		page = 0;
		focusSlot = slot;
		focusEnder = ender;
	}

	public void setPageCount(int pageCount) {
		this.pageCount = Math.max(1, pageCount);
		this.page = TradeLayout.clampPage(page, this.pageCount);
	}

	public void setPage(int page) {
		this.page = TradeLayout.clampPage(page, pageCount);
	}

	public Container display() {
		return display;
	}

	@Override
	public boolean stillValid(Player player) {
		return player.isAlive() && actions.stillOpen(this);
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput input, Player player) {
		if (!(player instanceof ServerPlayer serverPlayer) || slotId < 0) {
			return;
		}
		if (!getCarried().isEmpty()) {
			if (slotId >= TradeLayout.PLAYER_START) {
				super.clicked(slotId, button, input, player);
			}
			return;
		}
		boolean virtual = slotId < TradeLayout.PLAYER_START;
		if (input == ContainerInput.THROW || input == ContainerInput.SWAP || input == ContainerInput.QUICK_CRAFT
				|| input == ContainerInput.CLONE || input == ContainerInput.PICKUP_ALL) {
			if (virtual || input != ContainerInput.THROW && input != ContainerInput.SWAP) {
				return;
			}
			super.clicked(slotId, button, input, player);
			return;
		}
		if (input != ContainerInput.PICKUP && input != ContainerInput.QUICK_MOVE) {
			return;
		}
		actions.click(this, serverPlayer, slotId, input);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public void removed(Player player) {
		for (int slot = 0; slot < display.getContainerSize(); slot++) {
			display.setItem(slot, ItemStack.EMPTY);
		}
		super.removed(player);
		if (closeHandled || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		closeHandled = true;
		actions.closed(this, serverPlayer);
	}
}
