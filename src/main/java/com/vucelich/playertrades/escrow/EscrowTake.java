package com.vucelich.playertrades.escrow;

/**
 * Offer: journal, then clear the slot, then save the player. The slot stays if the journal throws.
 * Return or commit: move the items, then save, then rewrite the journal. A crash keeps one copy.
 */
public final class EscrowTake {
	private EscrowTake() {}

	public static void offer(Runnable journalWrite, Runnable clearSource, Runnable savePlayer) {
		journalWrite.run();
		clearSource.run();
		savePlayer.run();
	}

	public static void settle(Runnable moveItems, Runnable savePlayers, Runnable rewriteJournal) {
		moveItems.run();
		savePlayers.run();
		rewriteJournal.run();
	}
}
