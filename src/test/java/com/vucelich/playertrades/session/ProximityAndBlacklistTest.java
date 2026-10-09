package com.vucelich.playertrades.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ProximityAndBlacklistTest {
	@Test
	void defaultsImposeNoLimit() {
		assertEquals(ProximityRules.Problem.OK, ProximityRules.check(false, 10_000, false, 0));
		assertEquals(ProximityRules.Problem.OK, ProximityRules.check(true, 10_000, false, 0));
	}

	@Test
	void sameWorldAndDistanceFailOnTheirOwnRules() {
		assertEquals(ProximityRules.Problem.DIFFERENT_WORLD, ProximityRules.check(false, 1, true, 0));
		assertEquals(ProximityRules.Problem.OK, ProximityRules.check(false, 1_000, false, 5));
		assertEquals(ProximityRules.Problem.TOO_FAR, ProximityRules.check(true, 5.1, false, 5));
		assertEquals(ProximityRules.Problem.OK, ProximityRules.check(true, 5, false, 5));
	}

	@Test
	void blacklistMatchesItemIdsOnly() {
		ItemBlacklist list = new ItemBlacklist(List.of("minecraft:bedrock", "minecraft:barrier"));
		assertTrue(list.rejects("minecraft:bedrock"));
		assertFalse(list.rejects("minecraft:diamond"));
		assertFalse(list.rejects(null));
	}
}
