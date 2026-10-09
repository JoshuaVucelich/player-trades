package com.vucelich.playertrades.session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory offers and sessions. A player has at most one offer and is in at most one session.
 */
public final class TradeDirectory {
	private final Map<UUID, TradeSession> offers = new HashMap<>();
	private final Map<UUID, TradeSession> sessions = new HashMap<>();

	public PostResult postOffer(UUID player, String name, String text, long nowMillis) {
		if (sessions.containsKey(player)) {
			return PostResult.rejected(OfferProblem.IN_SESSION);
		}
		boolean replaced = offers.containsKey(player);
		TradeSession session = TradeSession.offer(player, name, text, nowMillis);
		offers.put(player, session);
		return PostResult.posted(session, replaced);
	}

	public AcceptResult accept(UUID acceptor, String acceptorName, UUID offerer, long nowMillis) {
		if (acceptor.equals(offerer)) {
			return AcceptResult.rejected(AcceptProblem.SELF);
		}
		if (sessions.containsKey(acceptor) || sessions.containsKey(offerer)) {
			return AcceptResult.rejected(AcceptProblem.IN_SESSION);
		}
		TradeSession offer = offers.get(offerer);
		if (offer == null) {
			return AcceptResult.rejected(AcceptProblem.MISSING);
		}
		if (offer.expired(nowMillis)) {
			offers.remove(offerer, offer);
			return AcceptResult.rejected(AcceptProblem.EXPIRED);
		}
		offer.accept(acceptor, acceptorName);
		offers.remove(offerer, offer);
		sessions.put(offerer, offer);
		sessions.put(acceptor, offer);
		return AcceptResult.accepted(offer);
	}

	public Optional<TradeSession> tradeOf(UUID player) {
		return Optional.ofNullable(sessions.get(player));
	}

	public Optional<TradeSession> offerOf(UUID player) {
		return Optional.ofNullable(offers.get(player));
	}

	public boolean withdrawOffer(UUID player) {
		TradeSession offer = offers.remove(player);
		if (offer == null) {
			return false;
		}
		if (offer.state() == TradeState.OFFERED) {
			offer.cancel("withdraw");
		}
		return true;
	}

	public void unlink(TradeSession session) {
		sessions.remove(session.playerA(), session);
		if (session.playerB() != null) {
			sessions.remove(session.playerB(), session);
		}
	}

	public Collection<TradeSession> trades() {
		return new ArrayList<>(new LinkedHashSet<>(sessions.values()));
	}

	public List<TradeSession> offers() {
		return new ArrayList<>(offers.values());
	}

	public record PostResult(TradeSession session, boolean replaced, OfferProblem problem) {
		public static PostResult posted(TradeSession session, boolean replaced) {
			return new PostResult(session, replaced, null);
		}

		public static PostResult rejected(OfferProblem problem) {
			return new PostResult(null, false, problem);
		}

		public boolean ok() {
			return problem == null;
		}
	}

	public record AcceptResult(TradeSession session, AcceptProblem problem) {
		public static AcceptResult accepted(TradeSession session) {
			return new AcceptResult(session, null);
		}

		public static AcceptResult rejected(AcceptProblem problem) {
			return new AcceptResult(null, problem);
		}

		public boolean ok() {
			return problem == null;
		}
	}
}
