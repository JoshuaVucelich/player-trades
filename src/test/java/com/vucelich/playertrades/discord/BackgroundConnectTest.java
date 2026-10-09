package com.vucelich.playertrades.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class BackgroundConnectTest {
	@Test
	void loginRunsOnTheDiscordThread() throws Exception {
		AtomicReference<String> name = new AtomicReference<>();
		Thread thread = BackgroundConnect.start(() -> name.set(Thread.currentThread().getName()));
		thread.join(2000);
		assertEquals(BackgroundConnect.THREAD_NAME, name.get());
		assertNotEquals(Thread.currentThread().getName(), name.get());
		assertTrue(thread.isDaemon());
	}

	@Test
	void closedOffersAreDroppedAndOpenOnesStay() {
		assertTrue(OfferRetention.drop(OfferNotice.EXPIRED));
		assertTrue(OfferRetention.drop(OfferNotice.CANCELLED));
		assertTrue(OfferRetention.drop(OfferNotice.COMPLETED));
		assertFalse(OfferRetention.drop(OfferNotice.ACCEPTED));
		assertFalse(OfferRetention.drop(null));
	}
}
