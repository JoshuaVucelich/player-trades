package com.vucelich.playertrades.escrow;

import com.vucelich.playertrades.TradeLimits;

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
		for (ItemEscrow.Entry entry : escrow.drain()) {
			ItemStack stack = entry.stack();
			if (stack.isEmpty()) {
				continue;
			}
			if (!dropOnly) {
				Inventory inventory = player.getInventory();
				int origin = entry.origin();
				if (origin >= 0 && origin < storageLimit(inventory) && inventory.getItem(origin).isEmpty()) {
					inventory.setItem(origin, stack);
					continue;
				}
				stack = insertStorage(inventory, stack);
				if (!stack.isEmpty()) {
					stack = insertContainer(player.getEnderChestInventory(), stack, 27);
				}
			}
			if (!stack.isEmpty()) {
				drop(player, stack);
			}
		}
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
