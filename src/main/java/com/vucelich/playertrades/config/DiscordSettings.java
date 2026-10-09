package com.vucelich.playertrades.config;

public record DiscordSettings(boolean enabled, String token, String guildId, String tradesChannelId, boolean relayNegotiation) {
	public DiscordSettings {
		token = token == null ? "" : token;
		guildId = guildId == null ? "" : guildId;
		tradesChannelId = tradesChannelId == null ? "" : tradesChannelId;
	}

	public static DiscordSettings defaults() {
		return new DiscordSettings(false, "", "", "", true);
	}

	public boolean wantsConnection() {
		return enabled && !token.isBlank();
	}
}
