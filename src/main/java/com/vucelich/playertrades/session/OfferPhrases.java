package com.vucelich.playertrades.session;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OfferPhrases {
	private static final Pattern WTS = Pattern.compile("(?i)^wts\\b(.*)$");

	private OfferPhrases() {}

	/**
	 * @return the advertisement text after a leading {@code WTS}, or empty when the line is not an offer
	 */
	public static Optional<String> wtsBody(String message) {
		if (message == null) {
			return Optional.empty();
		}
		String trimmed = message.trim();
		if (trimmed.startsWith("/")) {
			return Optional.empty();
		}
		Matcher matcher = WTS.matcher(trimmed);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		return Optional.of(matcher.group(1).trim());
	}
}
