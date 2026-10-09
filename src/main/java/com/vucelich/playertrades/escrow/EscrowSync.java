package com.vucelich.playertrades.escrow;

import java.util.UUID;

/**
 * Decides whether a journal line is still owed. The player save is the other half of the journal:
 * a line is returned only after that save has recorded the removal and has not recorded the return.
 */
public final class EscrowSync {
	private EscrowSync() {}

	public static boolean shouldReturn(UUID sequence, EscrowMarks saved) {
		if (sequence == null) {
			return true;
		}
		if (saved == null) {
			return false;
		}
		if (saved.returned().contains(sequence)) {
			return false;
		}
		return saved.escrowed().contains(sequence);
	}

	/** A journal line written before the player save still sits in that save. Replaying it would duplicate it. */
	public static boolean stillInPlayerSave(UUID sequence, EscrowMarks saved) {
		if (sequence == null || saved == null) {
			return false;
		}
		return !saved.escrowed().contains(sequence) && !saved.returned().contains(sequence);
	}
}
