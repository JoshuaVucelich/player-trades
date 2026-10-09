package com.vucelich.playertrades.fabric;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class TradeCommands {
	private TradeCommands() {}

	public static void register(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("trade")
				.executes(context -> withPlayer(context.getSource(), (runtime, player) -> runtime.reopen(player)))
				.then(Commands.literal("offer")
						.executes(context -> withPlayer(context.getSource(), (runtime, player) -> runtime.postOffer(player, "")))
						.then(Commands.argument("text", StringArgumentType.greedyString())
								.executes(context -> withPlayer(context.getSource(), (runtime, player) ->
										runtime.postOffer(player, StringArgumentType.getString(context, "text"))))))
				.then(Commands.literal("accept")
						.then(Commands.argument("player", StringArgumentType.word())
								.executes(context -> withPlayer(context.getSource(), (runtime, player) ->
										runtime.accept(player, StringArgumentType.getString(context, "player"))))))
				.then(Commands.literal("cancel")
						.executes(context -> withPlayer(context.getSource(), (runtime, player) -> runtime.cancel(player)))));
	}

	private static int withPlayer(net.minecraft.commands.CommandSourceStack source, TradeCommand command) {
		ServerPlayer player = source.getPlayer();
		TradeRuntime runtime = TradeEvents.runtime();
		if (player == null || runtime == null) {
			source.sendFailure(Component.literal("Only a player on a running server can do that."));
			return 0;
		}
		return command.run(runtime, player);
	}

	@FunctionalInterface
	private interface TradeCommand {
		int run(TradeRuntime runtime, ServerPlayer player);
	}
}
