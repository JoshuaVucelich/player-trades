package com.vucelich.playertrades.fabric;

import com.vucelich.playertrades.session.OfferPhrases;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

public final class TradeEvents {
	private static TradeRuntime runtime;

	private TradeEvents() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> TradeCommands.register(dispatcher));
		ServerLifecycleEvents.SERVER_STARTING.register(server -> runtime = new TradeRuntime(server));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (runtime != null) {
				runtime.shutdown();
			}
			runtime = null;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (runtime != null) {
				runtime.tick();
			}
		});
		ServerMessageEvents.CHAT_MESSAGE.register((message, player, bound) -> {
			if (runtime == null) {
				return;
			}
			OfferPhrases.wtsBody(message.signedContent()).ifPresent(text -> runtime.postOffer(player, text));
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (runtime != null) {
				runtime.onDisconnect(handler.getPlayer());
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (runtime != null && entity instanceof ServerPlayer player) {
				runtime.onDeath(player);
			}
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> {
			if (runtime != null) {
				runtime.onDimensionChange(player);
			}
		});
	}

	static TradeRuntime runtime() {
		return runtime;
	}
}
