package com.vucelich.playertrades.escrow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The player file and the journal are two copies of the same stacks. These walks crash between the steps.
 */
class EscrowSyncTest {
	private static final UUID line = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
	private static final UUID second = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

	@Test
	void crashAfterTheSlotClearsAndBeforeTheSaveDoesNotGiveTheStackAgain() {
		World world = World.offeredUntilSave();
		world.crash();
		assertTrue(world.diskHasStack);
		assertTrue(world.journal);
		assertFalse(world.replayGives(line));
		assertTrue(EscrowSync.stillInPlayerSave(line, world.diskMarks));
	}

	@Test
	void crashAfterTheOfferSaveReturnsTheJournalOnce() {
		World world = new World();
		world.offer();
		world.crash();
		assertFalse(world.diskHasStack);
		assertTrue(world.replayGives(line));
		world.giveFromJournal();
		assertTrue(world.memoryHasStack);
		assertFalse(world.replayGives(line));
	}

	@Test
	void crashAfterTheReturnMoveAndBeforeTheSaveGivesTheStackBack() {
		World world = new World();
		world.offer();
		world.crash();
		world.settleUntilSave();
		world.crash();
		assertFalse(world.diskHasStack);
		assertTrue(world.journal);
		assertTrue(world.replayGives(line));
	}

	@Test
	void crashAfterTheReturnSaveAndBeforeTheJournalRewriteDoesNotGiveTheStackAgain() {
		World world = new World();
		world.offer();
		world.crash();
		world.settleUntilRewrite();
		world.crash();
		assertTrue(world.diskHasStack);
		assertTrue(world.journal);
		assertFalse(world.replayGives(line));
		assertTrue(world.diskMarks.returned().contains(line));
	}

	@Test
	void aSavedLineIsNotReplayedWhenALaterLineStillIs() {
		EscrowMarks saved = EscrowMarks.empty().escrow(line).escrow(second).returned(line);
		assertFalse(EscrowSync.shouldReturn(line, saved));
		assertTrue(EscrowSync.shouldReturn(second, saved));
		assertFalse(EscrowSync.shouldReturn(line, saved.returned(line)));
	}

	@Test
	void aPartialGiveRestoresOnlyTheStacksThatWereNotPlaced() {
		assertEquals(java.util.List.of("b", "c"), PartialGive.remaining(java.util.List.of("a", "b", "c"), 1));
		assertEquals(java.util.List.of("a", "b", "c"), PartialGive.remaining(java.util.List.of("a", "b", "c"), 0));
		assertEquals(java.util.List.of(), PartialGive.remaining(java.util.List.of("a", "b", "c"), 3));
	}

	private static final class World {
		private boolean memoryHasStack = true;
		private boolean diskHasStack = true;
		private boolean journal;
		private EscrowMarks memoryMarks = EscrowMarks.empty();
		private EscrowMarks diskMarks = EscrowMarks.empty();

		private void offer() {
			EscrowTake.offer(this::writeJournal, this::clearSlot, this::saveRemoval);
		}

		private static World offeredUntilSave() {
			World world = new World();
			world.writeJournal();
			world.clearSlot();
			return world;
		}

		private void settleUntilSave() {
			moveBack();
		}

		private void settleUntilRewrite() {
			try {
				EscrowTake.settle(this::moveBack, this::saveReturn, () -> {
					throw new IllegalStateException("stop before the journal rewrite");
				});
			} catch (IllegalStateException stopped) {
				if (!"stop before the journal rewrite".equals(stopped.getMessage())) {
					throw stopped;
				}
			}
		}

		private void writeJournal() {
			journal = true;
		}

		private void clearSlot() {
			memoryHasStack = false;
		}

		private void saveRemoval() {
			memoryMarks = memoryMarks.escrow(line);
			save();
		}

		private void moveBack() {
			memoryHasStack = true;
		}

		private void saveReturn() {
			memoryMarks = memoryMarks.returned(line);
			save();
		}

		private void save() {
			diskHasStack = memoryHasStack;
			diskMarks = memoryMarks;
		}

		private void crash() {
			memoryHasStack = diskHasStack;
			memoryMarks = diskMarks;
		}

		private boolean replayGives(UUID sequence) {
			return journal && EscrowSync.shouldReturn(sequence, diskMarks);
		}

		private void giveFromJournal() {
			memoryHasStack = true;
			memoryMarks = memoryMarks.returned(line);
			save();
			journal = false;
		}
	}
}
