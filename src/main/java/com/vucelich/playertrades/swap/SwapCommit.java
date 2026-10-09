package com.vucelich.playertrades.swap;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Simulates both insertions, then applies them. A failure on the second write restores both inventories.
 */
public final class SwapCommit {
	private SwapCommit() {}

	public static CommitOutcome commit(
			InventoryImage left,
			InventoryImage right,
			List<IncomingStack> leftGives,
			List<IncomingStack> rightGives) {
		Optional<List<Placement>> intoLeft = SwapPlanner.plan(read(left), rightGives);
		Optional<List<Placement>> intoRight = SwapPlanner.plan(read(right), leftGives);
		if (intoLeft.isEmpty() || intoRight.isEmpty()) {
			return CommitOutcome.noSpace(intoLeft.isEmpty(), intoRight.isEmpty());
		}
		InventoryImage preLeft = left.copy();
		InventoryImage preRight = right.copy();
		try {
			left.apply(intoLeft.get(), rightGives);
			right.apply(intoRight.get(), leftGives);
			return CommitOutcome.success();
		} catch (RuntimeException failure) {
			left.restore(preLeft);
			right.restore(preRight);
			return CommitOutcome.rolledBack(failure);
		}
	}

	public static <T> void settleEscrow(CommitOutcome outcome, List<T> left, List<T> right, List<T> leftCopy, List<T> rightCopy) {
		switch (outcome.kind()) {
			case SUCCESS -> {
				left.clear();
				right.clear();
			}
			case ROLLED_BACK -> {
				left.clear();
				left.addAll(leftCopy);
				right.clear();
				right.addAll(rightCopy);
			}
			case NO_SPACE -> {
				// Escrow stays where it was. Neither inventory was written.
			}
		}
	}

	private static List<SlotState> read(InventoryImage image) {
		List<SlotState> slots = new ArrayList<>(image.size());
		for (int index = 0; index < image.size(); index++) {
			slots.add(image.slot(index));
		}
		return slots;
	}
}
