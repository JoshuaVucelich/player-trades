package com.vucelich.playertrades.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class TradeRulesTest {
	private final UUID alice = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private final UUID bob = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

	@Test
	void idleCancelsOnlyAfterSilence() {
		TradeSession session = TradeSession.offer(alice, "Alice", "books", 0L);
		session.accept(bob, "Bob");
		session.touch(1_000L);
		assertFalse(session.idle(1_000L + 119_000L, 120_000L));
		assertTrue(session.idle(1_000L + 120_000L, 120_000L));
		session.touch(2_000L);
		assertFalse(session.idle(2_000L + 10L, 120_000L));
		assertFalse(session.idle(9_000L, 0L));
	}

	@Test
	void configuredCountdownIsWhatArmStarts() {
		TradeSession session = TradeSession.offer(alice, "Alice", "books", 0L);
		session.accept(bob, "Bob");
		session.applyRules(20, false);
		session.arm(Side.A, 1, 1);
		assertEquals(ArmResult.COUNTDOWN, session.arm(Side.B, 1, 1));
		assertEquals(20, session.countdownTicks());
		assertEquals(1, session.displaySeconds());
	}

	@Test
	void proximityFailureAtCommitReturnsToNegotiation() {
		TradeSession session = TradeSession.offer(alice, "Alice", "books", 0L);
		session.accept(bob, "Bob");
		session.arm(Side.A, 1, 1);
		session.arm(Side.B, 1, 1);
		assertTrue(session.tickCountdown() || session.state() == TradeState.COUNTDOWN);
		while (session.state() == TradeState.COUNTDOWN) {
			session.tickCountdown();
		}
		assertEquals(TradeState.COMMITTING, session.state());
		session.resumeNegotiation();
		assertEquals(TradeState.NEGOTIATING, session.state());
		assertFalse(session.ready(Side.A));
		assertFalse(session.ready(Side.B));
	}

	@Test
	void cooldownReportsWholeSecondsRemaining() {
		Cooldowns cooldowns = new Cooldowns();
		cooldowns.block(alice, 10_500L);
		assertEquals(500L, cooldowns.remainingMillis(alice, 10_000L));
		assertEquals(1L, Cooldowns.secondsRemaining(500L));
		assertEquals(0L, cooldowns.remainingMillis(alice, 10_500L));
	}
}
