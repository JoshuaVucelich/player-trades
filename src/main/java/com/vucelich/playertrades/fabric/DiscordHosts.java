package com.vucelich.playertrades.fabric;

import java.util.function.Consumer;

import com.vucelich.playertrades.PlayerTrades;
import com.vucelich.playertrades.config.DiscordSettings;
import com.vucelich.playertrades.discord.ConnectionPlan;
import com.vucelich.playertrades.discord.DiscordBridge;
import com.vucelich.playertrades.discord.DiscordInbox;
import com.vucelich.playertrades.discord.NoOpDiscordBridge;
import com.vucelich.playertrades.discord.jda.JdaDiscordBridge;

public final class DiscordHosts {
	private DiscordHosts() {}

	public static DiscordBridge open(DiscordSettings settings, DiscordInbox inbox, Consumer<Runnable> serverThread) {
		if (!ConnectionPlan.shouldConnect(settings)) {
			PlayerTrades.LOGGER.info("Discord bridge disabled");
			return new NoOpDiscordBridge();
		}
		return JdaDiscordBridge.connect(settings, inbox, serverThread);
	}
}
