package com.vucelich.playertrades.discord;

import java.util.UUID;

public final class NoOpDiscordBridge implements DiscordBridge {
	@Override
	public void postOffer(UUID playerId, String playerName, String offerText) {}

	@Override
	public void updateOffer(UUID playerId, String playerName, OfferNotice notice) {}

	@Override
	public void openThread(UUID sessionId, UUID offererId, String nameA, String nameB) {}

	@Override
	public void relayFromGame(UUID sessionId, String speaker, String text) {}

	@Override
	public void shutdown() {}
}
