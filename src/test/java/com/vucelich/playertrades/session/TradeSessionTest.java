package com.vucelich.playertrades.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.vucelich.playertrades.TradeLimits;

class TradeSessionTest {
	private final UUID alice = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private final UUID bob = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Test
	void offerStartsOfferedAndAcceptEntersNegotiation() {
		TradeSession session = offered();
		assertEquals(TradeState.OFFERED, session.state());
		session.accept(bob, "Bob");
		assertEquals(TradeState.NEGOTIATING, session.state());
		assertEquals(1, session.generation());
		assertEquals(bob, session.playerB());
	}

	@Test
	void bothReadyStartsCountdownAndCapturesGeneration() {
		TradeSession session = negotiating();
		assertEquals(ArmResult.READY, session.arm(Side.A, 1, 1));
		assertEquals(TradeState.NEGOTIATING, session.state());
		assertEquals(ArmResult.COUNTDOWN, session.arm(Side.B, 1, 1));
		assertEquals(TradeState.COUNTDOWN, session.state());
		assertEquals(TradeLimits.COUNTDOWN_TICKS, session.countdownTicks());
		assertEquals(3, session.displaySeconds());
		assertTrue(session.ready(Side.A));
		assertTrue(session.ready(Side.B));
	}

	@Test
	void emptyEscrowRejectsReadyWithoutChangingState() {
		TradeSession session = negotiating();
		assertEquals(ArmResult.EMPTY, session.arm(Side.A, 0, 1));
		assertEquals(TradeState.NEGOTIATING, session.state());
		assertFalse(session.ready(Side.A));
	}

	@Test
	void escrowEditIncrementsGenerationAndAbortsCountdown() {
		TradeSession session = negotiating();
		session.onEscrowEdited();
		assertEquals(2, session.generation());
		assertEquals(TradeState.NEGOTIATING, session.state());

		session.arm(Side.A, 1, 1);
		session.arm(Side.B, 1, 1);
		int captured = session.generation();
		session.onEscrowEdited();
		assertEquals(TradeState.NEGOTIATING, session.state());
		assertEquals(captured + 1, session.generation());
		assertFalse(session.ready(Side.A));
		assertFalse(session.ready(Side.B));
		assertEquals(0, session.countdownTicks());
	}

	@Test
	void closingClearsReadyFlagsAndKeepsGeneration() {
		TradeSession session = countdown();
		int generation = session.generation();
		assertTrue(session.clearReadyFromClose());
		assertEquals(TradeState.NEGOTIATING, session.state());
		assertEquals(generation, session.generation());
		assertFalse(session.ready(Side.A));
		assertFalse(session.ready(Side.B));
	}

	@Test
	void sixtyTicksCommitOnceOnTheServerTickTheyHitZero() {
		TradeSession session = countdown();
		for (int tick = 0; tick < TradeLimits.COUNTDOWN_TICKS - 1; tick++) {
			assertFalse(session.tickCountdown());
			assertEquals(TradeState.COUNTDOWN, session.state());
		}
		assertTrue(session.tickCountdown());
		assertEquals(TradeState.COMMITTING, session.state());
		assertThrows(IllegalTradeTransitionException.class, session::tickCountdown);
		assertEquals(TradeState.COMMITTING, session.state());
	}

	@Test
	void generationMismatchBlocksCommit() {
		TradeSession session = countdown();
		session.setGenerationForTest(session.generation() + 1);
		assertThrows(IllegalTradeTransitionException.class, session::tickCountdown);
		assertEquals(TradeState.COUNTDOWN, session.state());
	}

	@Test
	void successAndNoSpaceLeaveTheCommitPath() {
		TradeSession committed = countdown();
		fastForward(committed);
		committed.complete();
		assertEquals(TradeState.COMPLETED, committed.state());
		assertThrows(IllegalTradeTransitionException.class, committed::complete);
		assertEquals(TradeState.COMPLETED, committed.state());

		TradeSession crowded = countdown();
		fastForward(crowded);
		crowded.rejectNoSpace();
		assertEquals(TradeState.NEGOTIATING, crowded.state());
		assertFalse(crowded.ready(Side.A));
		assertFalse(crowded.ready(Side.B));
	}

	@Test
	void cancelFromOpenStatesIsTerminal() {
		for (TradeState start : new TradeState[] {TradeState.OFFERED, TradeState.NEGOTIATING, TradeState.COUNTDOWN, TradeState.COMMITTING}) {
			TradeSession session = sessionIn(start);
			session.cancel("test");
			assertEquals(TradeState.CANCELLED, session.state());
			assertEquals("test", session.reason());
		}
	}

	@Test
	void illegalTransitionFromEachStateLeavesItUnchanged() {
		assertIllegal(offered(), "complete", TradeSession::complete);
		assertIllegal(negotiating(), "tick", session -> session.tickCountdown());
		assertIllegal(countdown(), "complete", TradeSession::complete);
		assertIllegal(committing(), "edit", TradeSession::onEscrowEdited);
		assertIllegal(completed(), "cancel", session -> session.cancel("again"));
		assertIllegal(cancelled(), "arm", session -> session.arm(Side.A, 1, 1));
	}

	@Test
	void togglingReadyDuringCountdownReturnsToNegotiation() {
		TradeSession session = countdown();
		assertEquals(ArmResult.UNREADY, session.arm(Side.A, 1, 1));
		assertEquals(TradeState.NEGOTIATING, session.state());
		assertFalse(session.ready(Side.A));
		assertFalse(session.ready(Side.B));
	}

	private static void assertIllegal(TradeSession session, String action, java.util.function.Consumer<TradeSession> call) {
		TradeState before = session.state();
		IllegalTradeTransitionException error = assertThrows(IllegalTradeTransitionException.class, () -> call.accept(session));
		assertEquals(before, session.state());
		assertEquals(before, error.from());
		assertEquals(action, error.action());
	}

	private TradeSession offered() {
		return TradeSession.offer(alice, "Alice", "diamonds", 1_000L);
	}

	private TradeSession negotiating() {
		TradeSession session = offered();
		session.accept(bob, "Bob");
		return session;
	}

	private TradeSession countdown() {
		TradeSession session = negotiating();
		assertEquals(ArmResult.READY, session.arm(Side.A, 1, 1));
		assertEquals(ArmResult.COUNTDOWN, session.arm(Side.B, 1, 1));
		return session;
	}

	private TradeSession committing() {
		TradeSession session = countdown();
		fastForward(session);
		return session;
	}

	private TradeSession completed() {
		TradeSession session = committing();
		session.complete();
		return session;
	}

	private TradeSession cancelled() {
		TradeSession session = negotiating();
		session.cancel("stop");
		return session;
	}

	private TradeSession sessionIn(TradeState state) {
		return switch (state) {
			case OFFERED -> offered();
			case NEGOTIATING -> negotiating();
			case COUNTDOWN -> countdown();
			case COMMITTING -> committing();
			case COMPLETED -> completed();
			case CANCELLED -> cancelled();
		};
	}

	private static boolean fastForward(TradeSession session) {
		boolean commit = false;
		for (int tick = 0; tick < TradeLimits.COUNTDOWN_TICKS; tick++) {
			commit = session.tickCountdown();
		}
		assertTrue(commit);
		assertEquals(TradeState.COMMITTING, session.state());
		return true;
	}
}
