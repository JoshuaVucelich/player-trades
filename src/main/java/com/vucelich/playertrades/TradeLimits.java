package com.vucelich.playertrades;

/**
 * Defaults until M2 loads {@code config/player_trades.json}.
 */
public final class TradeLimits {
	public static final int COUNTDOWN_TICKS = 60;
	public static final long OFFER_TTL_MILLIS = 5 * 60 * 1000L;
	public static final int MAX_OFFER_STACKS = 8;
	public static final int STORAGE_SLOTS = 36;

	private TradeLimits() {}
}
