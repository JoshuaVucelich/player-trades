package com.vucelich.playertrades.swap;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.vucelich.playertrades.config.Overflow;

/**
 * Plans a delivery into storage, then the ender chest, then drops. The input lists are not modified.
 */
public final class OverflowPlanner {
	private OverflowPlanner() {}

	public static Optional<List<RoutedPlacement>> plan(
			List<SlotState> storage,
			List<SlotState> ender,
			List<IncomingStack> incoming,
			Overflow overflow) {
		List<SlotState> storageSlots = new ArrayList<>(storage);
		List<SlotState> enderSlots = new ArrayList<>(ender);
		List<RoutedPlacement> placements = new ArrayList<>();
		for (int incomingIndex = 0; incomingIndex < incoming.size(); incomingIndex++) {
			IncomingStack stack = incoming.get(incomingIndex);
			int left = fill(storageSlots, placements, incomingIndex, stack, stack.count(), Bin.STORAGE);
			if (left > 0 && overflow == Overflow.ENDER_THEN_DROP) {
				left = fill(enderSlots, placements, incomingIndex, stack, left, Bin.ENDER);
			}
			if (left > 0 && overflow == Overflow.ENDER_THEN_DROP) {
				while (left > 0) {
					int move = Math.min(stack.maxStack(), left);
					placements.add(new RoutedPlacement(incomingIndex, Bin.DROP, 0, move));
					left -= move;
				}
			}
			if (left > 0) {
				return Optional.empty();
			}
		}
		return Optional.of(placements);
	}

	private static int fill(List<SlotState> slots, List<RoutedPlacement> placements, int incomingIndex, IncomingStack stack, int left, Bin bin) {
		for (int slot = 0; slot < slots.size() && left > 0; slot++) {
			SlotState current = slots.get(slot);
			if (current.isEmpty() || !current.mergeKey().equals(stack.mergeKey()) || current.count() >= current.maxStack()) {
				continue;
			}
			int move = Math.min(current.maxStack() - current.count(), left);
			slots.set(slot, new SlotState(current.mergeKey(), current.count() + move, current.maxStack()));
			placements.add(new RoutedPlacement(incomingIndex, bin, slot, move));
			left -= move;
		}
		for (int slot = 0; slot < slots.size() && left > 0; slot++) {
			if (!slots.get(slot).isEmpty()) {
				continue;
			}
			int move = Math.min(stack.maxStack(), left);
			slots.set(slot, new SlotState(stack.mergeKey(), move, stack.maxStack()));
			placements.add(new RoutedPlacement(incomingIndex, bin, slot, move));
			left -= move;
		}
		return left;
	}
}
