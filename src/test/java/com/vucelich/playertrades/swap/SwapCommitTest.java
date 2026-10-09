package com.vucelich.playertrades.swap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SwapCommitTest {
	@Test
	void mergesBeforeFillingEmptySlots() {
		List<SlotState> slots = emptySlots();
		slots.set(0, new SlotState("diamond", 40, 64));
		var plan = SwapPlanner.plan(slots, List.of(new IncomingStack("diamond", 30, 64))).orElseThrow();
		assertEquals(List.of(new Placement(0, 0, 24), new Placement(0, 1, 6)), plan);
		assertEquals(40, slots.get(0).count());
	}

	@Test
	void noSpaceWritesNothing() {
		MemoryInventory left = MemoryInventory.full("cobble");
		MemoryInventory right = MemoryInventory.full("dirt");
		left.explode = true;
		CommitOutcome outcome = SwapCommit.commit(
				left,
				right,
				List.of(new IncomingStack("diamond", 64, 64)),
				List.of(new IncomingStack("emerald", 64, 64)));
		assertEquals(CommitOutcome.Kind.NO_SPACE, outcome.kind());
		assertTrue(outcome.leftShort());
		assertTrue(outcome.rightShort());
		assertEquals(0, left.writes);
		assertEquals(0, right.writes);
		assertEquals("cobble", left.keys[0]);
		assertEquals("dirt", right.keys[0]);
	}

	@Test
	void successfulSwapClearsEscrow() {
		MemoryInventory left = MemoryInventory.empty();
		MemoryInventory right = MemoryInventory.empty();
		List<String> leftEscrow = new ArrayList<>(List.of("diamond"));
		List<String> rightEscrow = new ArrayList<>(List.of("emerald"));
		List<String> leftCopy = List.copyOf(leftEscrow);
		List<String> rightCopy = List.copyOf(rightEscrow);
		CommitOutcome outcome = SwapCommit.commit(
				left,
				right,
				List.of(new IncomingStack("diamond", 10, 64)),
				List.of(new IncomingStack("emerald", 4, 64)));
		SwapCommit.settleEscrow(outcome, leftEscrow, rightEscrow, leftCopy, rightCopy);
		assertEquals(CommitOutcome.Kind.SUCCESS, outcome.kind());
		assertNull(outcome.failure());
		assertEquals("emerald", left.keys[0]);
		assertEquals(4, left.counts[0]);
		assertEquals("diamond", right.keys[0]);
		assertEquals(10, right.counts[0]);
		assertTrue(leftEscrow.isEmpty());
		assertTrue(rightEscrow.isEmpty());
	}

	@Test
	void secondInsertionFailureRollsBackBothSidesAndRestoresEscrow() {
		MemoryInventory left = MemoryInventory.empty();
		MemoryInventory right = MemoryInventory.empty();
		right.explodeAfterWrites = 1;
		List<String> leftEscrow = new ArrayList<>(List.of("diamond"));
		List<String> rightEscrow = new ArrayList<>(List.of("emerald"));
		List<String> leftCopy = List.copyOf(leftEscrow);
		List<String> rightCopy = List.copyOf(rightEscrow);

		CommitOutcome outcome = SwapCommit.commit(
				left,
				right,
				List.of(new IncomingStack("diamond", 70, 64)),
				List.of(new IncomingStack("emerald", 10, 64)));
		SwapCommit.settleEscrow(outcome, leftEscrow, rightEscrow, leftCopy, rightCopy);

		assertEquals(CommitOutcome.Kind.ROLLED_BACK, outcome.kind());
		assertEquals("write failed", outcome.failure().getMessage());
		assertTrue(left.isEmpty());
		assertTrue(right.isEmpty());
		assertEquals(List.of("diamond"), leftEscrow);
		assertEquals(List.of("emerald"), rightEscrow);
	}

	@Test
	void itemsAlreadyOutOfAFullInventoryStillFitInTheFreedSlot() {
		MemoryInventory left = MemoryInventory.full("dirt");
		left.keys[3] = "";
		left.counts[3] = 0;
		MemoryInventory right = MemoryInventory.empty();
		CommitOutcome outcome = SwapCommit.commit(
				left,
				right,
				List.of(new IncomingStack("diamond", 64, 64)),
				List.of(new IncomingStack("emerald", 64, 64)));
		assertEquals(CommitOutcome.Kind.SUCCESS, outcome.kind());
		assertEquals("emerald", left.keys[3]);
		assertEquals(64, left.counts[3]);
		assertEquals("diamond", right.keys[0]);
	}

	private static List<SlotState> emptySlots() {
		List<SlotState> slots = new ArrayList<>();
		for (int index = 0; index < 36; index++) {
			slots.add(SlotState.empty());
		}
		return slots;
	}

	private static final class MemoryInventory implements InventoryImage {
		final String[] keys = new String[36];
		final int[] counts = new int[36];
		final int[] max = new int[36];
		boolean explode;
		int explodeAfterWrites = Integer.MAX_VALUE;
		int writes;

		static MemoryInventory empty() {
			return new MemoryInventory();
		}

		static MemoryInventory full(String key) {
			MemoryInventory inventory = new MemoryInventory();
			for (int index = 0; index < inventory.keys.length; index++) {
				inventory.keys[index] = key;
				inventory.counts[index] = 64;
				inventory.max[index] = 64;
			}
			return inventory;
		}

		boolean isEmpty() {
			for (int count : counts) {
				if (count != 0) {
					return false;
				}
			}
			return true;
		}

		@Override
		public int size() {
			return keys.length;
		}

		@Override
		public SlotState slot(int index) {
			if (counts[index] <= 0) {
				return SlotState.empty();
			}
			return new SlotState(keys[index], counts[index], max[index]);
		}

		@Override
		public void apply(List<Placement> placements, List<IncomingStack> incoming) {
			if (explode) {
				throw new IllegalStateException("write failed");
			}
			for (Placement placement : placements) {
				if (writes >= explodeAfterWrites) {
					throw new IllegalStateException("write failed");
				}
				IncomingStack stack = incoming.get(placement.incomingIndex());
				keys[placement.slot()] = stack.mergeKey();
				counts[placement.slot()] += placement.count();
				max[placement.slot()] = stack.maxStack();
				writes++;
			}
		}

		@Override
		public InventoryImage copy() {
			MemoryInventory copy = new MemoryInventory();
			System.arraycopy(keys, 0, copy.keys, 0, keys.length);
			System.arraycopy(counts, 0, copy.counts, 0, counts.length);
			System.arraycopy(max, 0, copy.max, 0, max.length);
			return copy;
		}

		@Override
		public void restore(InventoryImage snapshot) {
			MemoryInventory saved = (MemoryInventory) snapshot;
			System.arraycopy(saved.keys, 0, keys, 0, keys.length);
			System.arraycopy(saved.counts, 0, counts, 0, counts.length);
			System.arraycopy(saved.max, 0, max, 0, max.length);
		}
	}
}
