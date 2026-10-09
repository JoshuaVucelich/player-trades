package com.vucelich.playertrades.fabric;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.vucelich.playertrades.PlayerTrades;
import com.vucelich.playertrades.escrow.EscrowJournal;
import com.vucelich.playertrades.escrow.ItemEscrow;
import com.vucelich.playertrades.escrow.ItemOrigin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

public final class EscrowCodecs {
	private EscrowCodecs() {}

	public static List<EscrowJournal.Line> encode(MinecraftServer server, List<ItemEscrow.Entry> entries) {
		List<EscrowJournal.Line> lines = new ArrayList<>();
		if (entries == null) {
			return lines;
		}
		var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
		for (ItemEscrow.Entry entry : entries) {
			ItemStack stack = entry.stack();
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			var encoded = ItemStack.CODEC.encodeStart(ops, stack);
			if (encoded.result().isEmpty()) {
				PlayerTrades.LOGGER.error("Could not encode an escrowed stack");
				throw new IllegalStateException("Could not encode an escrowed stack");
			}
			ItemOrigin origin = entry.origin() == null ? ItemOrigin.inventory(0) : entry.origin();
			lines.add(new EscrowJournal.Line(encoded.result().get(), origin.kind().name(), origin.slot(), origin.inner(), entry.sequence()));
		}
		return lines;
	}

	public static ItemEscrow decode(MinecraftServer server, EscrowJournal.Side side) {
		ItemEscrow escrow = new ItemEscrow();
		if (side == null) {
			return escrow;
		}
		var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
		for (EscrowJournal.Line line : side.lines()) {
			ItemStack stack = decodeStack(ops, line.codec());
			if (stack.isEmpty()) {
				throw new IllegalStateException("Could not decode an escrowed stack");
			}
			escrow.add(stack, origin(line), line.sequence());
		}
		return escrow;
	}

	private static ItemStack decodeStack(com.mojang.serialization.DynamicOps<JsonElement> ops, JsonElement codec) {
		if (codec == null || codec.isJsonNull()) {
			return ItemStack.EMPTY;
		}
		var parsed = ItemStack.CODEC.parse(ops, codec);
		return parsed.result().orElse(ItemStack.EMPTY);
	}

	private static ItemOrigin origin(EscrowJournal.Line line) {
		ItemOrigin.Kind kind;
		try {
			kind = ItemOrigin.Kind.valueOf(line.kind());
		} catch (IllegalArgumentException | NullPointerException bad) {
			kind = ItemOrigin.Kind.INVENTORY;
		}
		return new ItemOrigin(kind, line.slot(), line.inner());
	}
}
