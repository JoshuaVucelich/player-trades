package com.vucelich.playertrades.discord;

import java.util.UUID;

/**
 * Text-only door to Discord. Implementations must not accept item stacks and must not commit a trade.
 */
public interface DiscordBridge {
	void postOffer(UUID playerId, String playerName, String offerText);

	void updateOffer(UUID playerId, String playerName, OfferNotice notice);

	void openThread(UUID sessionId, UUID offererId, String nameA, String nameB);

	void relayFromGame(UUID sessionId, String speaker, String text);

	void shutdown();
}
