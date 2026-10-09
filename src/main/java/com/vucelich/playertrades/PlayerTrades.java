package com.vucelich.playertrades;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-agnostic entry for Player Trades.
 * Fabric wiring lives in {@code com.vucelich.playertrades.fabric}.
 * Fabric wiring lives in {@code fabric}. Session rules do not import a loader.
 */
public final class PlayerTrades {
	public static final String MOD_ID = "player_trades";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private PlayerTrades() {}

	public static void init() {
		LOGGER.info("Player Trades initialized (dedicated server)");
	}
}
