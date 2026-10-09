package com.vucelich.playertrades.swap;

public record SlotState(String mergeKey, int count, int maxStack) {
	public static SlotState empty() {
		return new SlotState("", 0, 0);
	}

	public boolean isEmpty() {
		return count <= 0;
	}
}
