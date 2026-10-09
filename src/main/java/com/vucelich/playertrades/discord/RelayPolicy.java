package com.vucelich.playertrades.discord;

import com.vucelich.playertrades.session.OfferPhrases;

public final class RelayPolicy {
	private RelayPolicy() {}

	public static boolean fromGame(boolean relayEnabled, boolean negotiating, boolean participant, String text) {
		if (!relayEnabled || !negotiating || !participant) {
			return false;
		}
		return speakable(text);
	}

	public static boolean fromDiscord(String text) {
		return speakable(text);
	}

	/**
	 * Discord text is delivered only while the session is open and both players are online.
	 * A false result must not create escrow and must not commit.
	 */
	public static boolean bothOnline(boolean sessionOpen, boolean leftOnline, boolean rightOnline) {
		return sessionOpen && leftOnline && rightOnline;
	}

	private static boolean speakable(String text) {
		if (text == null) {
			return false;
		}
		String trimmed = text.trim();
		if (trimmed.isEmpty() || trimmed.startsWith("/")) {
			return false;
		}
		return OfferPhrases.wtsBody(trimmed).isEmpty();
	}
}
