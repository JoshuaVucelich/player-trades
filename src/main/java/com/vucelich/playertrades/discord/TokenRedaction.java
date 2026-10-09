package com.vucelich.playertrades.discord;

public final class TokenRedaction {
	public static final String REDACTED = "[redacted]";

	private TokenRedaction() {}

	public static String redact(String message, String token) {
		if (message == null || message.isEmpty()) {
			return "";
		}
		if (token == null || token.isBlank()) {
			return message;
		}
		return message.replace(token, REDACTED);
	}
}
