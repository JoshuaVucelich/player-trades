package com.vucelich.playertrades.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TradeConfigTest {
	@Test
	void defaultsMatchTheDesignAndRoundTrip() {
		TradeConfig defaults = TradeConfig.defaults();
		assertEquals(3, defaults.countdownSeconds());
		assertEquals(120, defaults.sessionTimeoutSeconds());
		assertEquals(300, defaults.offerTimeoutSeconds());
		assertEquals(10, defaults.cooldownSeconds());
		assertFalse(defaults.requireSameWorld());
		assertEquals(0, defaults.maxDistance());
		assertEquals(Overflow.ENDER_THEN_DROP, defaults.overflow());
		assertFalse(defaults.allowEmptyTrades());
		assertTrue(defaults.blacklist().isEmpty());
		assertFalse(defaults.discord().enabled());
		assertEquals("", defaults.discord().token());

		TradeConfig.ConfigLoad loaded = TradeConfig.parse(defaults.toJson(), null);
		assertTrue(loaded.replaced());
		assertEquals(defaults, loaded.config());
		assertFalse(defaults.toJson().contains("super-secret"));
	}

	@Test
	void invalidJsonKeepsThePreviousConfigAndHidesTheToken() {
		TradeConfig previous = new TradeConfig(
				4, 30, 60, 0, true, 12, Overflow.ABORT, false, java.util.List.of("minecraft:bedrock"),
				new DiscordSettings(true, "super-secret-token-value", "guild", "channel", false));
		String broken = "{ \"countdownSeconds\": 0, \"discord\": { \"token\": \"super-secret-token-value\" } }";
		TradeConfig.ConfigLoad loaded = TradeConfig.parse(broken, previous);
		assertFalse(loaded.replaced());
		assertEquals(previous, loaded.config());
		assertFalse(loaded.error().contains("super-secret-token-value"));

		TradeConfig.ConfigLoad garbage = TradeConfig.parse("{ not json super-secret-token-value", previous);
		assertFalse(garbage.replaced());
		assertEquals(previous, garbage.config());
		assertFalse(garbage.error().contains("super-secret-token-value"));
	}

	@Test
	void missingFieldsUseDefaults() {
		TradeConfig.ConfigLoad loaded = TradeConfig.parse("{\"maxDistance\": 8}", TradeConfig.defaults());
		assertTrue(loaded.replaced());
		assertEquals(8, loaded.config().maxDistance());
		assertEquals(3, loaded.config().countdownSeconds());
		assertEquals("", loaded.config().discord().token());
	}
}
