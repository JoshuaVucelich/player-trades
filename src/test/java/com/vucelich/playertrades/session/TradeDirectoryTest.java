package com.vucelich.playertrades.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class TradeDirectoryTest {
	private final UUID alice = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private final UUID bob = UUID.fromString("00000000-0000-0000-0000-00000000000b");
	private final TradeDirectory directory = new TradeDirectory();

	@Test
	void newOfferReplacesThePreviousOne() {
		TradeDirectory.PostResult first = directory.postOffer(alice, "Alice", "iron", 0L);
		TradeDirectory.PostResult second = directory.postOffer(alice, "Alice", "gold", 10L);
		assertTrue(first.ok());
		assertFalse(first.replaced());
		assertTrue(second.ok());
		assertTrue(second.replaced());
		assertEquals("gold", directory.offerOf(alice).orElseThrow().offerText());
		assertEquals(1, directory.offers().size());
	}

	@Test
	void acceptConsumesTheOfferAndDoesNotRestoreItOnCancel() {
		directory.postOffer(alice, "Alice", "books", 0L);
		TradeDirectory.AcceptResult result = directory.accept(bob, "Bob", alice, 1L);
		assertTrue(result.ok());
		assertEquals(TradeState.NEGOTIATING, result.session().state());
		assertTrue(directory.offerOf(alice).isEmpty());
		assertEquals(result.session(), directory.tradeOf(bob).orElseThrow());
		assertEquals(result.session(), directory.tradeById(result.session().id()).orElseThrow());

		result.session().cancel("player");
		directory.unlink(result.session());
		assertTrue(directory.offerOf(alice).isEmpty());
		assertTrue(directory.tradeOf(alice).isEmpty());
		assertTrue(directory.tradeOf(bob).isEmpty());
	}

	@Test
	void selfExpiredAndBusyAcceptsFail() {
		directory.postOffer(alice, "Alice", "sand", 0L);
		assertEquals(AcceptProblem.SELF, directory.accept(alice, "Alice", alice, 1L).problem());

		assertEquals(AcceptProblem.EXPIRED, directory.accept(bob, "Bob", alice, 5 * 60 * 1000L).problem());
		assertTrue(directory.offerOf(alice).isEmpty());

		directory.postOffer(alice, "Alice", "sand", 0L);
		directory.accept(bob, "Bob", alice, 1L);
		directory.postOffer(UUID.randomUUID(), "Cara", "clay", 1L);
		UUID cara = directory.offers().get(0).playerA();
		assertEquals(AcceptProblem.IN_SESSION, directory.accept(alice, "Alice", cara, 2L).problem());
		assertEquals(OfferProblem.IN_SESSION, directory.postOffer(bob, "Bob", "again", 2L).problem());
	}

	@Test
	void missingOfferIsRejected() {
		assertEquals(AcceptProblem.MISSING, directory.accept(bob, "Bob", alice, 0L).problem());
	}

	@Test
	void wtsLinesKeepTheAdvertisementText() {
		assertEquals("diamond blocks", OfferPhrases.wtsBody("WTS diamond blocks").orElseThrow());
		assertEquals("", OfferPhrases.wtsBody("  wts  ").orElseThrow());
		assertTrue(OfferPhrases.wtsBody("selling diamonds").isEmpty());
		assertTrue(OfferPhrases.wtsBody("/trade offer hi").isEmpty());
		assertTrue(OfferPhrases.wtsBody("wtsy no").isEmpty());
	}
}
