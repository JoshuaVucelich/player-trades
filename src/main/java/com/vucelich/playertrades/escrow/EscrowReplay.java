package com.vucelich.playertrades.escrow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Hands each session's stacks to an online owner once. The session id plus the player id is the key,
 * so a start replay and a later join cannot both return the same stacks.
 */
public final class EscrowReplay {
	private final Set<String> given = new HashSet<>();

	public List<EscrowJournal.Side> claim(EscrowJournal.Page page, Set<UUID> online) {
		if (page == null || page.sessionId() == null || online == null || online.isEmpty()) {
			return List.of();
		}
		List<EscrowJournal.Side> ready = new ArrayList<>();
		for (EscrowJournal.Side side : page.sides()) {
			if (side.player() == null || side.lines().isEmpty() || !online.contains(side.player())) {
				continue;
			}
			if (given.add(key(page.sessionId(), side.player()))) {
				ready.add(side);
			}
		}
		return ready;
	}

	public boolean alreadyGiven(UUID sessionId, UUID player) {
		return sessionId != null && player != null && given.contains(key(sessionId, player));
	}

	public void claimLine(UUID sessionId, UUID sequence) {
		if (sessionId != null && sequence != null) {
			given.add(lineKey(sessionId, sequence));
		}
	}

	public boolean lineGiven(UUID sessionId, UUID sequence) {
		return sessionId != null && sequence != null && given.contains(lineKey(sessionId, sequence));
	}

	public static EscrowJournal.Page without(EscrowJournal.Page page, Set<UUID> delivered) {
		List<EscrowJournal.Side> remaining = new ArrayList<>();
		for (EscrowJournal.Side side : page.sides()) {
			if (side.player() != null && (delivered == null || !delivered.contains(side.player()))) {
				remaining.add(side);
			}
		}
		return new EscrowJournal.Page(page.sessionId(), remaining);
	}

	private static String key(UUID sessionId, UUID player) {
		return sessionId + "/" + player;
	}

	private static String lineKey(UUID sessionId, UUID sequence) {
		return "line/" + sessionId + "/" + sequence;
	}
}
