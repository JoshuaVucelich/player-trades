package com.vucelich.playertrades.discord;

import com.vucelich.playertrades.config.DiscordSettings;

public final class ConnectionPlan {
	private ConnectionPlan() {}

	public static boolean shouldConnect(DiscordSettings settings) {
		return settings != null && settings.wantsConnection();
	}
}
