package com.vucelich.playertrades.escrow;

import java.util.List;

/**
 * After a give stops part way through, only the stacks that were not placed go back into escrow.
 */
public final class PartialGive {
	private PartialGive() {}

	public static <T> List<T> remaining(List<T> stacks, int placed) {
		if (stacks == null || stacks.isEmpty()) {
			return List.of();
		}
		int start = Math.max(0, Math.min(placed, stacks.size()));
		return List.copyOf(stacks.subList(start, stacks.size()));
	}
}
