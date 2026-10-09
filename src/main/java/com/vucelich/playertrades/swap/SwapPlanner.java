package com.vucelich.playertrades.swap;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Plans both sides of a swap against copies of the destination slots.
 * The input lists are not modified. Nothing is written until {@link SwapCommit} applies a plan.
 */
public final class SwapPlanner {
	private SwapPlanner() {}

	public static Optional<List<Placement>> plan(List<SlotState> destination, List<IncomingStack> incoming) {
		List<SlotState> slots = new ArrayList<>(destination);
		List<Placement> placements = new ArrayList<>();
		for (int incomingIndex = 0; incomingIndex < incoming.size(); incomingIndex++) {
			IncomingStack stack = incoming.get(incomingIndex);
			int left = stack.count();
			for (int slot = 0; slot < slots.size() && left > 0; slot++) {
				SlotState current = slots.get(slot);
				if (current.isEmpty() || !current.mergeKey().equals(stack.mergeKey()) || current.count() >= current.maxStack()) {
					continue;
				}
				int move = Math.min(current.maxStack() - current.count(), left);
				slots.set(slot, new SlotState(current.mergeKey(), current.count() + move, current.maxStack()));
				placements.add(new Placement(incomingIndex, slot, move));
				left -= move;
			}
			for (int slot = 0; slot < slots.size() && left > 0; slot++) {
				if (!slots.get(slot).isEmpty()) {
					continue;
				}
				int move = Math.min(stack.maxStack(), left);
				slots.set(slot, new SlotState(stack.mergeKey(), move, stack.maxStack()));
				placements.add(new Placement(incomingIndex, slot, move));
				left -= move;
			}
			if (left > 0) {
				return Optional.empty();
			}
		}
		return Optional.of(placements);
	}
}
