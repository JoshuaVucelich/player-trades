package com.vucelich.playertrades.fabric;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.vucelich.playertrades.PlayerTrades;
import com.vucelich.playertrades.escrow.ItemEscrow;
import com.vucelich.playertrades.session.TradeSession;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

public final class TradeLogWriter {
	private TradeLogWriter() {}

	public static String line(MinecraftServer server, TradeSession session, String result, String reason, String world, List<ItemEscrow.Entry> left, List<ItemEscrow.Entry> right) {
		JsonObject root = new JsonObject();
		root.addProperty("id", session.id().toString());
		root.addProperty("time", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
		root.addProperty("result", result);
		root.addProperty("reason", reason == null ? "" : reason);
		root.addProperty("world", world == null || world.isBlank() ? "minecraft:overworld" : world);
		JsonArray players = new JsonArray();
		players.add(person(session.playerA().toString(), session.nameA()));
		if (session.playerB() != null) {
			players.add(person(session.playerB().toString(), session.nameB()));
		}
		root.add("players", players);
		JsonArray offers = new JsonArray();
		offers.add(side(server, left));
		offers.add(side(server, right));
		root.add("offers", offers);
		return root.toString();
	}

	private static JsonObject person(String uuid, String name) {
		JsonObject person = new JsonObject();
		person.addProperty("uuid", uuid);
		person.addProperty("name", name);
		return person;
	}

	private static JsonArray side(MinecraftServer server, List<ItemEscrow.Entry> entries) {
		JsonArray array = new JsonArray();
		if (entries == null) {
			return array;
		}
		for (ItemEscrow.Entry entry : entries) {
			ItemStack stack = entry.stack();
			if (stack.isEmpty()) {
				continue;
			}
			JsonObject item = new JsonObject();
			item.addProperty("id", String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem())));
			item.addProperty("count", stack.getCount());
			Component custom = stack.get(DataComponents.CUSTOM_NAME);
			if (custom != null) {
				item.addProperty("customName", custom.getString());
			}
			try {
				var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
				ItemStack.CODEC.encodeStart(ops, stack).resultOrPartial(message -> PlayerTrades.LOGGER.error("Could not encode a trade stack: {}", message))
						.ifPresent(encoded -> item.add("codec", encoded));
			} catch (RuntimeException failure) {
				PlayerTrades.LOGGER.error("Could not encode a trade stack");
			}
			array.add(item);
		}
		return array;
	}
}
