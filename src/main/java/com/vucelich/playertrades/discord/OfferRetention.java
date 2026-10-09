package com.vucelich.playertrades.discord;

/**
 * Closed offer posts are not reused, so the in-memory message map can drop them.
 */
public final class OfferRetention {
	private OfferRetention() {}

	public static boolean drop(OfferNotice notice) {
		return notice == OfferNotice.EXPIRED || notice == OfferNotice.CANCELLED || notice == OfferNotice.COMPLETED;
	}
}
