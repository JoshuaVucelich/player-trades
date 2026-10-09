package com.vucelich.playertrades.escrow;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * Shulker edits always copy the box and the contents list. The stack still sitting in an inventory is not mutated.
 */
public final class ShulkerBoxes {
	public static final int SLOTS = 27;

	private ShulkerBoxes() {}

	public static boolean isBox(ItemStack stack) {
		return !stack.isEmpty()
				&& stack.getItem() instanceof BlockItem blockItem
				&& blockItem.getBlock() instanceof ShulkerBoxBlock;
	}

	public static List<ItemStack> copySlots(ItemStack box) {
		NonNullList<ItemStack> slots = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
		ItemContainerContents contents = box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		contents.copyInto(slots);
		List<ItemStack> copies = new ArrayList<>(SLOTS);
		for (int index = 0; index < SLOTS; index++) {
			ItemStack stack = slots.get(index);
			copies.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
		}
		return copies;
	}

	public static Extraction extract(ItemStack liveBox, int slot) {
		if (!isBox(liveBox) || slot < 0 || slot >= SLOTS) {
			return null;
		}
		ContainerCopies.Extraction<ItemStack> extracted = ContainerCopies.extract(
				copySlots(liveBox),
				slot,
				ItemStack.EMPTY,
				ItemStack::copy,
				ItemStack::isEmpty);
		if (extracted == null) {
			return null;
		}
		return new Extraction(write(liveBox, extracted.slots()), extracted.removed());
	}

	public static PlaceResult insert(ItemStack liveBox, ItemStack stack) {
		if (!isBox(liveBox) || stack.isEmpty()) {
			return new PlaceResult(liveBox, stack);
		}
		List<ItemStack> slots = copySlots(liveBox);
		SimpleContainer container = new SimpleContainer(SLOTS);
		for (int index = 0; index < SLOTS; index++) {
			container.setItem(index, slots.get(index));
		}
		ItemStack leftover = ItemMover.insertContainer(container, stack.copy(), SLOTS);
		List<ItemStack> written = new ArrayList<>(SLOTS);
		for (int index = 0; index < SLOTS; index++) {
			ItemStack stored = container.getItem(index);
			written.add(stored.isEmpty() ? ItemStack.EMPTY : stored.copy());
		}
		return new PlaceResult(write(liveBox, written), leftover);
	}

	private static ItemStack write(ItemStack liveBox, List<ItemStack> slots) {
		ItemStack updated = liveBox.copy();
		updated.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots));
		return updated;
	}

	public record Extraction(ItemStack box, ItemStack removed) {}

	public record PlaceResult(ItemStack box, ItemStack leftover) {}
}
