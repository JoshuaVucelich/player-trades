package com.vucelich.playertrades.fabric;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.vucelich.playertrades.PlayerTrades;
import com.vucelich.playertrades.config.TradeConfig;

import net.fabricmc.loader.api.FabricLoader;

public final class FabricConfigs {
	private FabricConfigs() {}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("player_trades.json");
	}

	public static TradeConfig load() {
		Path path = path();
		TradeConfig defaults = TradeConfig.defaults();
		if (Files.notExists(path)) {
			write(path, defaults);
			PlayerTrades.LOGGER.info("Wrote default config/player_trades.json");
			return defaults;
		}
		try {
			TradeConfig.ConfigLoad load = TradeConfig.parse(Files.readString(path), defaults);
			if (!load.replaced()) {
				PlayerTrades.LOGGER.error("Keeping the previous config because player_trades.json could not be read: {}", load.error());
				return load.config();
			}
			return load.config();
		} catch (IOException failure) {
			PlayerTrades.LOGGER.error("Keeping the previous config because player_trades.json could not be read");
			return defaults;
		}
	}

	public static void write(Path path, TradeConfig config) {
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, config.toJson());
		} catch (IOException failure) {
			PlayerTrades.LOGGER.error("Could not write config/player_trades.json");
		}
	}
}
