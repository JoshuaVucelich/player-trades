package com.vucelich.playertrades.config;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Server config. Parsing never includes the Discord token in an error string.
 */
public record TradeConfig(
		int countdownSeconds,
		int sessionTimeoutSeconds,
		int offerTimeoutSeconds,
		int cooldownSeconds,
		boolean requireSameWorld,
		int maxDistance,
		Overflow overflow,
		boolean allowEmptyTrades,
		List<String> blacklist,
		DiscordSettings discord) {
	public TradeConfig {
		blacklist = List.copyOf(blacklist);
	}

	public static TradeConfig defaults() {
		return new TradeConfig(3, 120, 300, 10, false, 0, Overflow.ENDER_THEN_DROP, false, List.of(), DiscordSettings.defaults());
	}

	public int countdownTicks() {
		return countdownSeconds * 20;
	}

	public long offerTtlMillis() {
		return offerTimeoutSeconds * 1000L;
	}

	public long sessionTimeoutMillis() {
		return sessionTimeoutSeconds * 1000L;
	}

	public long cooldownMillis() {
		return cooldownSeconds * 1000L;
	}

	public String toJson() {
		StringBuilder ids = new StringBuilder();
		for (int index = 0; index < blacklist.size(); index++) {
			if (index > 0) {
				ids.append(", ");
			}
			ids.append(quote(blacklist.get(index)));
		}
		return """
				{
				  "countdownSeconds": %d,
				  "sessionTimeoutSeconds": %d,
				  "offerTimeoutSeconds": %d,
				  "cooldownSeconds": %d,
				  "requireSameWorld": %s,
				  "maxDistance": %d,
				  "overflow": "%s",
				  "allowEmptyTrades": %s,
				  "blacklist": [%s],
				  "discord": {
				    "enabled": %s,
				    "token": %s,
				    "guildId": %s,
				    "tradesChannelId": %s,
				    "relayNegotiation": %s
				  }
				}
				""".formatted(
				countdownSeconds,
				sessionTimeoutSeconds,
				offerTimeoutSeconds,
				cooldownSeconds,
				requireSameWorld,
				maxDistance,
				overflow == Overflow.ABORT ? "abort" : "ender_then_drop",
				allowEmptyTrades,
				ids,
				discord.enabled(),
				quote(discord.token()),
				quote(discord.guildId()),
				quote(discord.tradesChannelId()),
				discord.relayNegotiation());
	}

	public static ConfigLoad parse(String json, TradeConfig previous) {
		TradeConfig fallback = previous == null ? defaults() : previous;
		if (json == null || json.isBlank()) {
			return ConfigLoad.keep(fallback, "player_trades.json is empty");
		}
		try {
			JsonElement root = JsonParser.parseString(json);
			if (!root.isJsonObject()) {
				return ConfigLoad.keep(fallback, "player_trades.json must be an object");
			}
			return ConfigLoad.ok(read(root.getAsJsonObject()));
		} catch (ConfigFormatException failure) {
			return ConfigLoad.keep(fallback, failure.getMessage());
		} catch (RuntimeException failure) {
			return ConfigLoad.keep(fallback, "player_trades.json is not valid JSON");
		}
	}

	private static TradeConfig read(JsonObject obj) {
		TradeConfig defaults = defaults();
		JsonObject discord = object(obj, "discord");
		DiscordSettings chat = discord == null
				? defaults.discord()
				: new DiscordSettings(
						bool(discord, "enabled", defaults.discord().enabled()),
						text(discord, "token", ""),
						text(discord, "guildId", ""),
						text(discord, "tradesChannelId", ""),
						bool(discord, "relayNegotiation", true));
		return new TradeConfig(
				integer(obj, "countdownSeconds", defaults.countdownSeconds(), 1),
				integer(obj, "sessionTimeoutSeconds", defaults.sessionTimeoutSeconds(), 0),
				integer(obj, "offerTimeoutSeconds", defaults.offerTimeoutSeconds(), 1),
				integer(obj, "cooldownSeconds", defaults.cooldownSeconds(), 0),
				bool(obj, "requireSameWorld", defaults.requireSameWorld()),
				integer(obj, "maxDistance", defaults.maxDistance(), 0),
				overflow(obj, defaults.overflow()),
				bool(obj, "allowEmptyTrades", defaults.allowEmptyTrades()),
				strings(obj, "blacklist"),
				chat);
	}

	private static Overflow overflow(JsonObject obj, Overflow fallback) {
		if (!obj.has("overflow") || obj.get("overflow").isJsonNull()) {
			return fallback;
		}
		JsonElement element = obj.get("overflow");
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			throw new ConfigFormatException("overflow must be abort or ender_then_drop");
		}
		Overflow parsed = Overflow.parse(element.getAsString());
		if (parsed == null) {
			throw new ConfigFormatException("overflow must be abort or ender_then_drop");
		}
		return parsed;
	}

	private static List<String> strings(JsonObject obj, String field) {
		if (!obj.has(field) || obj.get(field).isJsonNull()) {
			return List.of();
		}
		JsonElement element = obj.get(field);
		if (!element.isJsonArray()) {
			throw new ConfigFormatException(field + " must be a list of item ids");
		}
		List<String> values = new ArrayList<>();
		JsonArray array = element.getAsJsonArray();
		for (JsonElement entry : array) {
			if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
				throw new ConfigFormatException(field + " must be a list of item ids");
			}
			values.add(entry.getAsString().trim());
		}
		return values;
	}

	private static int integer(JsonObject obj, String field, int fallback, int min) {
		if (!obj.has(field) || obj.get(field).isJsonNull()) {
			return fallback;
		}
		JsonElement element = obj.get(field);
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			throw new ConfigFormatException(field + " must be a number");
		}
		int value = element.getAsInt();
		if (value < min) {
			throw new ConfigFormatException(field + " must be at least " + min);
		}
		return value;
	}

	private static boolean bool(JsonObject obj, String field, boolean fallback) {
		if (!obj.has(field) || obj.get(field).isJsonNull()) {
			return fallback;
		}
		JsonElement element = obj.get(field);
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			throw new ConfigFormatException(field + " must be true or false");
		}
		return element.getAsBoolean();
	}

	private static String text(JsonObject obj, String field, String fallback) {
		if (!obj.has(field) || obj.get(field).isJsonNull()) {
			return fallback;
		}
		JsonElement element = obj.get(field);
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			throw new ConfigFormatException(field + " must be a string");
		}
		return element.getAsString();
	}

	private static JsonObject object(JsonObject obj, String field) {
		if (!obj.has(field) || obj.get(field).isJsonNull()) {
			return null;
		}
		JsonElement element = obj.get(field);
		if (!element.isJsonObject()) {
			throw new ConfigFormatException(field + " must be an object");
		}
		return element.getAsJsonObject();
	}

	private static String quote(String value) {
		return new JsonPrimitive(value == null ? "" : value).toString();
	}

	public record ConfigLoad(TradeConfig config, boolean replaced, String error) {
		public static ConfigLoad ok(TradeConfig config) {
			return new ConfigLoad(config, true, null);
		}

		public static ConfigLoad keep(TradeConfig previous, String error) {
			return new ConfigLoad(previous, false, error);
		}
	}

	private static final class ConfigFormatException extends RuntimeException {
		private ConfigFormatException(String message) {
			super(message);
		}
	}
}
