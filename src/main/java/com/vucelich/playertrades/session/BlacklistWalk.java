package com.vucelich.playertrades.session;

import java.util.List;

/**
 * Item id check that also walks shulker ({@code container}) and bundle ({@code bundle}) contents.
 */
public final class BlacklistWalk {
	public static final int MAX_DEPTH = 8;

	private BlacklistWalk() {}

	public record Held(String id, List<Held> container, List<Held> bundle) {
		public Held {
			container = container == null ? List.of() : List.copyOf(container);
			bundle = bundle == null ? List.of() : List.copyOf(bundle);
		}
	}

	public static boolean rejects(ItemBlacklist blacklist, Held held) {
		return rejects(blacklist, held, 0);
	}

	private static boolean rejects(ItemBlacklist blacklist, Held held, int depth) {
		if (blacklist == null || held == null || depth > MAX_DEPTH) {
			return false;
		}
		if (blacklist.rejects(held.id())) {
			return true;
		}
		for (Held inner : held.container()) {
			if (rejects(blacklist, inner, depth + 1)) {
				return true;
			}
		}
		for (Held inner : held.bundle()) {
			if (rejects(blacklist, inner, depth + 1)) {
				return true;
			}
		}
		return false;
	}
}
