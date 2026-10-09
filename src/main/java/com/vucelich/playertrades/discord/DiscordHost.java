package com.vucelich.playertrades.discord;

import com.vucelich.playertrades.config.DiscordSettings;

/**
 * Opens a bridge from server config. A disabled config must not open a socket.
 */
@FunctionalInterface
public interface DiscordHost {
	DiscordBridge open(DiscordSettings settings);
}
