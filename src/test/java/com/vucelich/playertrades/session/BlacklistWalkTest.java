package com.vucelich.playertrades.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class BlacklistWalkTest {
	private final ItemBlacklist bedrock = new ItemBlacklist(List.of("minecraft:bedrock"));

	@Test
	void containerAndBundleContentsAreChecked() {
		BlacklistWalk.Held box = new BlacklistWalk.Held("minecraft:shulker_box", List.of(new BlacklistWalk.Held("minecraft:bedrock", List.of(), List.of())), List.of());
		BlacklistWalk.Held bundle = new BlacklistWalk.Held("minecraft:bundle", List.of(), List.of(new BlacklistWalk.Held("minecraft:bedrock", List.of(), List.of())));
		BlacklistWalk.Held clean = new BlacklistWalk.Held("minecraft:shulker_box", List.of(new BlacklistWalk.Held("minecraft:diamond", List.of(), List.of())), List.of());
		assertTrue(BlacklistWalk.rejects(bedrock, box));
		assertTrue(BlacklistWalk.rejects(bedrock, bundle));
		assertFalse(BlacklistWalk.rejects(bedrock, clean));
		assertTrue(BlacklistWalk.rejects(bedrock, new BlacklistWalk.Held("minecraft:bedrock", List.of(), List.of())));
	}

	@Test
	void nestingStopsAtTheDepthCap() {
		BlacklistWalk.Held deep = new BlacklistWalk.Held("minecraft:bedrock", List.of(), List.of());
		for (int level = 0; level < BlacklistWalk.MAX_DEPTH; level++) {
			deep = new BlacklistWalk.Held("minecraft:shulker_box", List.of(deep), List.of());
		}
		assertTrue(BlacklistWalk.rejects(bedrock, deep));
		BlacklistWalk.Held tooDeep = new BlacklistWalk.Held("minecraft:shulker_box", List.of(deep), List.of());
		assertFalse(BlacklistWalk.rejects(bedrock, tooDeep));
	}

	@Test
	void aBundleInsideAShulkerIsStillSeen() {
		BlacklistWalk.Held nested = new BlacklistWalk.Held(
				"minecraft:shulker_box",
				List.of(new BlacklistWalk.Held("minecraft:bundle", List.of(), List.of(new BlacklistWalk.Held("minecraft:bedrock", List.of(), List.of())))),
				List.of());
		assertTrue(BlacklistWalk.rejects(bedrock, nested));
		assertFalse(BlacklistWalk.rejects(null, nested));
	}
}
