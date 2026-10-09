package com.vucelich.playertrades.escrow;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Sequences stored with the player file. {@code escrowed} means the save no longer has those stacks
 * because the journal owns them. {@code returned} means the save already received them back.
 */
public record EscrowMarks(Set<UUID> escrowed, Set<UUID> returned) {
	public EscrowMarks {
		escrowed = escrowed == null ? Set.of() : Set.copyOf(escrowed);
		returned = returned == null ? Set.of() : Set.copyOf(returned);
	}

	public static EscrowMarks empty() {
		return new EscrowMarks(Set.of(), Set.of());
	}

	public EscrowMarks escrow(UUID sequence) {
		if (sequence == null || escrowed.contains(sequence)) {
			return this;
		}
		Set<UUID> next = new LinkedHashSet<>(escrowed);
		next.add(sequence);
		return new EscrowMarks(next, returned);
	}

	public EscrowMarks returned(UUID sequence) {
		if (sequence == null || returned.contains(sequence)) {
			return this;
		}
		Set<UUID> next = new LinkedHashSet<>(returned);
		next.add(sequence);
		return new EscrowMarks(escrowed, next);
	}
}
