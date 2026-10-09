package com.vucelich.playertrades.escrow;

import com.vucelich.playertrades.TradeLimits;
import com.vucelich.playertrades.config.Overflow;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class ItemMover {
	private ItemMover() {}

	public static boolean fitsInStorage(Inventory inventory, ItemStack stack) {
		return freeRoom(inventory, stack, storageLimit(inventory)) >= stack.getCount();
	}

	public static ItemStack insertStorage(Inventory inventory, ItemStack stack) {
		return insert(inventory, stack, storageLimit(inventory));
	}

	public static ItemStack insertContainer(Container container, ItemStack stack, int limit) {
		return insert(container, stack, Math.min(limit, container.getContainerSize()));
	}

	public static void giveBack(ServerPlayer player, ItemEscrow escrow, boolean dropOnly) {
		java.util.List<ItemEscrow.Entry> pending = new java.util.ArrayList<>(escrow.drain());
		int placed = 0;
		try {
			for (ItemEscrow.Entry entry : pending) {
				ItemStack leftover = place(player, entry.origin(), entry.stack(), dropOnly, true);
				if (!leftover.isEmpty()) {
					drop(player, leftover);
				}
				placed++;
			}
		} catch (RuntimeException failure) {
			escrow.restore(PartialGive.remaining(pending, placed));
			throw failure;
		}
	}

	/**
	 * @return whatever could not be placed. Empty when the stack has a home, including a drop when drops are allowed.
	 */
	public static ItemStack place(ServerPlayer player, ItemOrigin origin, ItemStack stack, boolean dropOnly, boolean allowDrop) {
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		if (dropOnly) {
			drop(player, stack);
			return ItemStack.EMPTY;
		}
		stack = placeAtOrigin(player, origin, stack);
		if (!stack.isEmpty()) {
			stack = insertStorage(player.getInventory(), stack);
		}
		if (!stack.isEmpty()) {
			stack = insertContainer(player.getEnderChestInventory(), stack, 27);
		}
		if (!stack.isEmpty() && allowDrop) {
			drop(player, stack);
			return ItemStack.EMPTY;
		}
		return stack;
	}

	public static boolean allowDrop(Overflow overflow) {
		return overflow == Overflow.ENDER_THEN_DROP;
	}

	private static ItemStack placeAtOrigin(ServerPlayer player, ItemOrigin origin, ItemStack stack) {
		if (origin == null) {
			return stack;
		}
		Inventory inventory = player.getInventory();
		return switch (origin.kind()) {
			case INVENTORY -> {
				if (origin.slot() >= 0 && origin.slot() < storageLimit(inventory) && inventory.getItem(origin.slot()).isEmpty()) {
					inventory.setItem(origin.slot(), stack);
					yield ItemStack.EMPTY;
				}
				yield stack;
			}
			case ENDER -> insertContainer(player.getEnderChestInventory(), preferSlot(player.getEnderChestInventory(), origin.slot(), stack), 27);
			case SHULKER_INVENTORY -> insertIntoBox(inventory, origin.slot(), stack);
			case SHULKER_ENDER -> insertIntoBox(player.getEnderChestInventory(), origin.slot(), stack);
		};
	}

	private static ItemStack preferSlot(Container container, int slot, ItemStack stack) {
		if (slot >= 0 && slot < container.getContainerSize() && container.getItem(slot).isEmpty()) {
			container.setItem(slot, stack);
			return ItemStack.EMPTY;
		}
		return stack;
	}

	private static ItemStack insertIntoBox(Container container, int slot, ItemStack stack) {
		if (slot < 0 || slot >= container.getContainerSize()) {
			return stack;
		}
		ItemStack box = container.getItem(slot);
		if (!ShulkerBoxes.isBox(box)) {
			return stack;
		}
		ShulkerBoxes.PlaceResult placed = ShulkerBoxes.insert(box, stack);
		if (placed.box() != box) {
			container.setItem(slot, placed.box());
		}
		return placed.leftover().isEmpty() ? ItemStack.EMPTY : placed.leftover();
	}

	public static void drop(ServerPlayer player, ItemStack stack) {
		dropAt(player.level(), player.getX(), player.getY(), player.getZ(), stack);
	}

	public static void dropAt(ServerLevel level, double x, double y, double z, ItemStack stack) {
		if (stack.isEmpty() || level == null) {
			return;
		}
		level.addFreshEntity(new ItemEntity(level, x, y, z, stack));
	}

	private static int storageLimit(Inventory inventory) {
		return Math.min(TradeLimits.STORAGE_SLOTS, inventory.getContainerSize());
	}

	private static int freeRoom(Container container, ItemStack stack, int limit) {
		int max = Math.max(1, stack.getMaxStackSize());
		int room = 0;
		for (int slot = 0; slot < limit; slot++) {
			ItemStack existing = container.getItem(slot);
			if (existing.isEmpty()) {
				room += max;
			} else if (ItemStack.isSameItemSameComponents(existing, stack)) {
				room += Math.max(0, max - existing.getCount());
			}
		}
		return room;
	}

	private static ItemStack insert(Container container, ItemStack stack, int limit) {
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack remaining = stack;
		int max = Math.max(1, remaining.getMaxStackSize());
		for (int slot = 0; slot < limit && !remaining.isEmpty(); slot++) {
			ItemStack existing = container.getItem(slot);
			if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remaining)) {
				continue;
			}
			int room = max - existing.getCount();
			if (room <= 0) {
				continue;
			}
			int move = Math.min(room, remaining.getCount());
			existing.setCount(existing.getCount() + move);
			remaining = shrink(remaining, move);
		}
		for (int slot = 0; slot < limit && !remaining.isEmpty(); slot++) {
			if (!container.getItem(slot).isEmpty()) {
				continue;
			}
			int move = Math.min(max, remaining.getCount());
			container.setItem(slot, remaining.copyWithCount(move));
			remaining = shrink(remaining, move);
		}
		return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
	}

	private static ItemStack shrink(ItemStack stack, int amount) {
		int left = stack.getCount() - amount;
		if (left <= 0) {
			return ItemStack.EMPTY;
		}
		stack.setCount(left);
		return stack;
	}
}
