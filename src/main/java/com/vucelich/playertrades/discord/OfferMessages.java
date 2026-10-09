package com.vucelich.playertrades.discord;

public final class OfferMessages {
	private static final int LIMIT = 1900;

	private OfferMessages() {}

	public static String open(String name, String text) {
		String who = who(name);
		String body = text == null || text.isBlank() ? "open offer" : text.trim();
		return limit(who + " is trading: " + body + "\n/trade accept " + who);
	}

	public static String closed(String name, OfferNotice notice) {
		String who = who(name);
		String message = switch (notice) {
			case ACCEPTED -> who + "'s offer was accepted in game. This offer is closed.";
			case EXPIRED -> who + "'s offer expired.";
			case CANCELLED -> who + "'s offer was cancelled.";
			case COMPLETED -> who + "'s trade was completed in game.";
		};
		return limit(message);
	}

	public static String relay(String speaker, String text) {
		String who = who(speaker);
		String body = text == null ? "" : text.trim().replaceAll("\\s+", " ");
		return limit(who + ": " + body);
	}

	public static String threadName(String nameA, String nameB) {
		String name = who(nameA) + " and " + who(nameB);
		name = name.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
		if (name.isBlank()) {
			name = "trade";
		}
		if (name.length() > 100) {
			return name.substring(0, 100);
		}
		return name;
	}

	private static String who(String name) {
		if (name == null || name.isBlank()) {
			return "Someone";
		}
		return name.trim();
	}

	private static String limit(String message) {
		if (message.length() <= LIMIT) {
			return message;
		}
		return message.substring(0, LIMIT);
	}
}
