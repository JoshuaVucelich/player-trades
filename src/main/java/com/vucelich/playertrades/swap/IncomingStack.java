package com.vucelich.playertrades.swap;

public record IncomingStack(String mergeKey, int count, int maxStack, Object payload) {
	public IncomingStack {
		if (mergeKey == null || mergeKey.isEmpty()) {
			throw new IllegalArgumentException("mergeKey");
		}
		if (count <= 0) {
			throw new IllegalArgumentException("count");
		}
		if (maxStack <= 0) {
			throw new IllegalArgumentException("maxStack");
		}
	}

	public IncomingStack(String mergeKey, int count, int maxStack) {
		this(mergeKey, count, maxStack, null);
	}
}
