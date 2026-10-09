package com.vucelich.playertrades.swap;

import java.util.List;

import com.vucelich.playertrades.TradeLimits;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The 36 storage slots of a player inventory, used as a swap destination.
 */
public final class PlayerStorage implements InventoryImage {
	private final Inventory inventory;
	private final ItemStack[] frozen;

	private PlayerStorage(Inventory inventory, ItemStack[] frozen) {
		this.inventory = inventory;
		this.frozen = frozen;
	}

	public static PlayerStorage live(Inventory inventory) {
		return new PlayerStorage(inventory, null);
	}

	public static IncomingStack incoming(ItemStack stack) {
		return new IncomingStack(mergeKey(stack), stack.getCount(), Math.max(1, stack.getMaxStackSize()), stack);
	}

	@Override
	public int size() {
		return Math.min(TradeLimits.STORAGE_SLOTS, inventory.getContainerSize());
	}

	@Override
	public SlotState slot(int index) {
		ItemStack stack = read(index);
		if (stack.isEmpty()) {
			return SlotState.empty();
		}
		return new SlotState(mergeKey(stack), stack.getCount(), Math.max(1, stack.getMaxStackSize()));
	}

	@Override
	public void apply(List<Placement> placements, List<IncomingStack> incoming) {
		if (frozen != null) {
			throw new IllegalStateException("Cannot write a snapshot");
		}
		for (Placement placement : placements) {
			ItemStack payload = (ItemStack) incoming.get(placement.incomingIndex()).payload();
			ItemStack piece = payload.copyWithCount(placement.count());
			payload.setCount(payload.getCount() - placement.count());
			ItemStack destination = inventory.getItem(placement.slot());
			if (destination.isEmpty()) {
				inventory.setItem(placement.slot(), piece);
			} else if (ItemStack.isSameItemSameComponents(destination, piece)) {
				destination.setCount(destination.getCount() + piece.getCount());
			} else {
				throw new IllegalStateException("Destination slot changed during the swap");
			}
		}
	}

	@Override
	public InventoryImage copy() {
		ItemStack[] saved = new ItemStack[size()];
		for (int index = 0; index < saved.length; index++) {
			ItemStack stack = inventory.getItem(index);
			saved[index] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
		}
		return new PlayerStorage(inventory, saved);
	}

	@Override
	public void restore(InventoryImage snapshot) {
		ItemStack[] saved = ((PlayerStorage) snapshot).frozen;
		for (int index = 0; index < size(); index++) {
			ItemStack stack = saved[index];
			inventory.setItem(index, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
		}
	}

	private ItemStack read(int index) {
		if (frozen != null) {
			return frozen[index];
		}
		return inventory.getItem(index);
	}

	private static String mergeKey(ItemStack stack) {
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id + "/" + ItemStack.hashItemAndComponents(stack);
	}
}
