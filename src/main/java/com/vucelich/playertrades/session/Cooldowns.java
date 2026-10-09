package com.vucelich.playertrades.session;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Cooldowns {
	private final Map<UUID, Long> until = new HashMap<>();

	public void block(UUID player, long untilMillis) {
		if (player != null && untilMillis > 0) {
			until.put(player, untilMillis);
		}
	}

	public long remainingMillis(UUID player, long nowMillis) {
		Long deadline = until.get(player);
		if (deadline == null) {
			return 0L;
		}
		long left = deadline - nowMillis;
		if (left <= 0L) {
			until.remove(player);
			return 0L;
		}
		return left;
	}

	public static long secondsRemaining(long remainingMillis) {
		return (remainingMillis + 999L) / 1000L;
	}
}
