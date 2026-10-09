package com.vucelich.playertrades.swap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vucelich.playertrades.config.Overflow;

class OverflowPlannerTest {
	@Test
	void abortRefusesWhenStorageIsFull() {
		List<SlotState> storage = full("cobble");
		List<SlotState> ender = empty(27);
		assertTrue(OverflowPlanner.plan(storage, ender, List.of(new IncomingStack("diamond", 64, 64)), Overflow.ABORT).isEmpty());
		assertEquals("cobble", storage.get(0).mergeKey());
		assertEquals(64, storage.get(0).count());
	}

	@Test
	void enderThenDropUsesTheEnderChestBeforeADrop() {
		List<RoutedPlacement> intoEnder = OverflowPlanner.plan(
				full("cobble"),
				empty(27),
				List.of(new IncomingStack("diamond", 10, 64)),
				Overflow.ENDER_THEN_DROP).orElseThrow();
		assertEquals(Bin.ENDER, intoEnder.get(0).bin());
		assertEquals(10, intoEnder.get(0).count());
		List<RoutedPlacement> plan = OverflowPlanner.plan(
				full("cobble"),
				fullEnder("obsidian"),
				List.of(new IncomingStack("diamond", 10, 64)),
				Overflow.ENDER_THEN_DROP).orElseThrow();
		assertEquals(1, plan.size());
		assertEquals(Bin.DROP, plan.get(0).bin());
		assertEquals(10, plan.get(0).count());
	}

	@Test
	void twoFullInventoriesStillFitBecauseEscrowAlreadyLeft() {
		List<SlotState> left = full("dirt");
		left.set(4, SlotState.empty());
		List<RoutedPlacement> plan = OverflowPlanner.plan(
				left,
				empty(27),
				List.of(new IncomingStack("emerald", 64, 64)),
				Overflow.ABORT).orElseThrow();
		assertEquals(1, plan.size());
		assertEquals(Bin.STORAGE, plan.get(0).bin());
		assertEquals(4, plan.get(0).slot());
	}

	@Test
	void failedSecondWriteRestoresEnderAndDropsNothing() {
		MemorySplit left = MemorySplit.empty();
		MemorySplit right = MemorySplit.empty();
		right.explodeAfterWrites = 1;
		List<String> leftEscrow = new ArrayList<>(List.of("diamond"));
		List<String> rightEscrow = new ArrayList<>(List.of("emerald"));
		CommitOutcome outcome = SwapCommit.commit(
				left,
				right,
				List.of(new IncomingStack("diamond", 100, 64)),
				List.of(new IncomingStack("emerald", 10, 64)),
				Overflow.ENDER_THEN_DROP);
		SwapCommit.settleEscrow(outcome, leftEscrow, rightEscrow, List.copyOf(leftEscrow), List.copyOf(rightEscrow));
		assertEquals(CommitOutcome.Kind.ROLLED_BACK, outcome.kind());
		assertTrue(left.storageEmpty());
		assertTrue(right.storageEmpty());
		assertTrue(left.drops.isEmpty());
		assertTrue(right.drops.isEmpty());
		assertEquals(List.of("diamond"), leftEscrow);
		assertEquals(List.of("emerald"), rightEscrow);
	}

	private static List<SlotState> full(String key) {
		List<SlotState> slots = new ArrayList<>();
		for (int index = 0; index < 36; index++) {
			slots.add(new SlotState(key, 64, 64));
		}
		return slots;
	}

	private static List<SlotState> fullEnder(String key) {
		List<SlotState> slots = new ArrayList<>();
		for (int index = 0; index < 27; index++) {
			slots.add(new SlotState(key, 64, 64));
		}
		return slots;
	}

	private static List<SlotState> empty(int size) {
		List<SlotState> slots = new ArrayList<>();
		for (int index = 0; index < size; index++) {
			slots.add(SlotState.empty());
		}
		return slots;
	}

	private static final class MemorySplit implements SplitImage {
		final String[] storage = new String[36];
		final int[] storageCount = new int[36];
		final String[] ender = new String[27];
		final int[] enderCount = new int[27];
		final List<String> drops = new ArrayList<>();
		int writes;
		int explodeAfterWrites = Integer.MAX_VALUE;

		static MemorySplit empty() {
			return new MemorySplit();
		}

		@Override
		public int storageSize() {
			return 36;
		}

		@Override
		public int enderSize() {
			return 27;
		}

		@Override
		public SlotState storageSlot(int index) {
			return slot(storage[index], storageCount[index]);
		}

		@Override
		public SlotState enderSlot(int index) {
			return slot(ender[index], enderCount[index]);
		}

		@Override
		public void apply(List<RoutedPlacement> placements, List<IncomingStack> incoming) {
			for (RoutedPlacement placement : placements) {
				if (writes >= explodeAfterWrites) {
					throw new IllegalStateException("write failed");
				}
				writes++;
				IncomingStack stack = incoming.get(placement.incomingIndex());
				switch (placement.bin()) {
					case STORAGE -> {
						storage[placement.slot()] = stack.mergeKey();
						storageCount[placement.slot()] += placement.count();
					}
					case ENDER -> {
						ender[placement.slot()] = stack.mergeKey();
						enderCount[placement.slot()] += placement.count();
					}
					case DROP -> drops.add(stack.mergeKey() + ":" + placement.count());
				}
			}
		}

		@Override
		public SplitImage copy() {
			MemorySplit copy = new MemorySplit();
			System.arraycopy(storage, 0, copy.storage, 0, storage.length);
			System.arraycopy(storageCount, 0, copy.storageCount, 0, storageCount.length);
			System.arraycopy(ender, 0, copy.ender, 0, ender.length);
			System.arraycopy(enderCount, 0, copy.enderCount, 0, enderCount.length);
			return copy;
		}

		@Override
		public void restore(SplitImage snapshot) {
			MemorySplit saved = (MemorySplit) snapshot;
			System.arraycopy(saved.storage, 0, storage, 0, storage.length);
			System.arraycopy(saved.storageCount, 0, storageCount, 0, storageCount.length);
			System.arraycopy(saved.ender, 0, ender, 0, ender.length);
			System.arraycopy(saved.enderCount, 0, enderCount, 0, enderCount.length);
			drops.clear();
		}

		@Override
		public List<Object> drainDrops() {
			List<Object> copy = new ArrayList<>(drops);
			drops.clear();
			return copy;
		}

		boolean storageEmpty() {
			for (int count : storageCount) {
				if (count != 0) {
					return false;
				}
			}
			for (int count : enderCount) {
				if (count != 0) {
					return false;
				}
			}
			return true;
		}

		private static SlotState slot(String key, int count) {
			if (count <= 0) {
				return SlotState.empty();
			}
			return new SlotState(key, count, 64);
		}
	}
}
