package com.vucelich.playertrades.swap;

import java.util.ArrayList;
import java.util.List;

import com.vucelich.playertrades.TradeLimits;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The 36 storage slots and 27 ender-chest slots. Armor and the offhand are never written.
 */
public final class PlayerStorage implements SplitImage {
	private final Inventory inventory;
	private final Container ender;
	private final ItemStack[] frozenStorage;
	private final ItemStack[] frozenEnder;
	private final List<ItemStack> pendingDrops = new ArrayList<>();

	private PlayerStorage(Inventory inventory, Container ender, ItemStack[] frozenStorage, ItemStack[] frozenEnder) {
		this.inventory = inventory;
		this.ender = ender;
		this.frozenStorage = frozenStorage;
		this.frozenEnder = frozenEnder;
	}

	public static PlayerStorage live(Inventory inventory, Container ender) {
		return new PlayerStorage(inventory, ender, null, null);
	}

	public static IncomingStack incoming(ItemStack stack) {
		return new IncomingStack(mergeKey(stack), stack.getCount(), Math.max(1, stack.getMaxStackSize()), stack);
	}

	@Override
	public int storageSize() {
		return Math.min(TradeLimits.STORAGE_SLOTS, inventory.getContainerSize());
	}

	@Override
	public int enderSize() {
		return Math.min(27, ender.getContainerSize());
	}

	@Override
	public SlotState storageSlot(int index) {
		return stateOf(readStorage(index));
	}

	@Override
	public SlotState enderSlot(int index) {
		return stateOf(readEnder(index));
	}

	@Override
	public void apply(List<RoutedPlacement> placements, List<IncomingStack> incoming) {
		if (frozenStorage != null) {
			throw new IllegalStateException("Cannot write a snapshot");
		}
		for (RoutedPlacement placement : placements) {
			ItemStack payload = (ItemStack) incoming.get(placement.incomingIndex()).payload();
			ItemStack piece = piece(payload, placement.count());
			switch (placement.bin()) {
				case STORAGE -> put(inventory, placement.slot(), piece);
				case ENDER -> put(ender, placement.slot(), piece);
				case DROP -> pendingDrops.add(piece);
			}
		}
	}

	@Override
	public SplitImage copy() {
		return new PlayerStorage(inventory, ender, snap(inventory, storageSize()), snap(ender, enderSize()));
	}

	@Override
	public void restore(SplitImage snapshot) {
		PlayerStorage saved = (PlayerStorage) snapshot;
		write(inventory, saved.frozenStorage);
		write(ender, saved.frozenEnder);
		pendingDrops.clear();
	}

	@Override
	public List<Object> drainDrops() {
		List<Object> drops = new ArrayList<>(pendingDrops);
		pendingDrops.clear();
		return drops;
	}

	private static ItemStack piece(ItemStack payload, int count) {
		if (count >= payload.getCount()) {
			return payload;
		}
		ItemStack piece = payload.copyWithCount(count);
		payload.setCount(payload.getCount() - count);
		return piece;
	}

	private static void put(Container container, int slot, ItemStack piece) {
		ItemStack destination = container.getItem(slot);
		if (destination.isEmpty()) {
			container.setItem(slot, piece);
		} else if (ItemStack.isSameItemSameComponents(destination, piece)) {
			destination.setCount(destination.getCount() + piece.getCount());
		} else {
			throw new IllegalStateException("Destination slot changed during the swap");
		}
	}

	private ItemStack readStorage(int index) {
		if (frozenStorage != null) {
			return frozenStorage[index];
		}
		return inventory.getItem(index);
	}

	private ItemStack readEnder(int index) {
		if (frozenEnder != null) {
			return frozenEnder[index];
		}
		return ender.getItem(index);
	}

	private static ItemStack[] snap(Container container, int size) {
		ItemStack[] saved = new ItemStack[size];
		for (int index = 0; index < size; index++) {
			ItemStack stack = container.getItem(index);
			saved[index] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
		}
		return saved;
	}

	private static void write(Container container, ItemStack[] saved) {
		for (int index = 0; index < saved.length; index++) {
			ItemStack stack = saved[index];
			container.setItem(index, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
		}
	}

	private static SlotState stateOf(ItemStack stack) {
		if (stack.isEmpty()) {
			return SlotState.empty();
		}
		return new SlotState(mergeKey(stack), stack.getCount(), Math.max(1, stack.getMaxStackSize()));
	}

	private static String mergeKey(ItemStack stack) {
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id + "/" + ItemStack.hashItemAndComponents(stack);
	}
}
