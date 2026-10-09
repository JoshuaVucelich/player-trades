package com.vucelich.playertrades.config;

public enum Overflow {
	ABORT,
	ENDER_THEN_DROP;

	public static Overflow parse(String raw) {
		if (raw == null) {
			return null;
		}
		return switch (raw.trim()) {
			case "abort" -> ABORT;
			case "ender_then_drop" -> ENDER_THEN_DROP;
			default -> null;
		};
	}
}
