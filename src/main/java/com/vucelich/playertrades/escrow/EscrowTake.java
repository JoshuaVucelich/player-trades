package com.vucelich.playertrades.escrow;

/**
 * The journal write runs before the source slot is cleared. If the write throws, the slot stays.
 */
public final class EscrowTake {
	private EscrowTake() {}

	public static void removeAfterJournal(Runnable journalWrite, Runnable clearSource) {
		journalWrite.run();
		clearSource.run();
	}
}
