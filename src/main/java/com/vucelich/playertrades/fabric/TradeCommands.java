package com.vucelich.playertrades.fabric;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.vucelich.playertrades.perm.PermissionChecker;

import net.minecraft.commands.CommandSourceStack;
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
						.executes(context -> withPlayer(context.getSource(), (runtime, player) -> runtime.cancel(player))))
				.then(Commands.literal("log")
						.requires(source -> allowed(source, PermissionChecker.ADMIN_LOG))
						.executes(context -> withPlayer(context.getSource(), (runtime, player) -> say(player, runtime.logPage(null, 1))))
						.then(Commands.literal("show")
								.then(Commands.argument("id", StringArgumentType.word())
										.executes(context -> withPlayer(context.getSource(), (runtime, player) ->
												say(player, runtime.logShow(StringArgumentType.getString(context, "id")))))))
						.then(Commands.argument("selector", StringArgumentType.word())
								.executes(context -> withPlayer(context.getSource(), (runtime, player) ->
										say(player, page(runtime, StringArgumentType.getString(context, "selector"), 1))))
								.then(Commands.argument("page", IntegerArgumentType.integer(1))
										.executes(context -> withPlayer(context.getSource(), (runtime, player) ->
												say(player, page(runtime, StringArgumentType.getString(context, "selector"), IntegerArgumentType.getInteger(context, "page"))))))))
				.then(Commands.literal("admin")
						.then(Commands.literal("cancel")
								.requires(source -> allowed(source, PermissionChecker.ADMIN_CANCEL))
								.then(Commands.argument("player", StringArgumentType.word())
										.executes(context -> withPlayer(context.getSource(), (runtime, player) ->
												runtime.adminCancel(player, StringArgumentType.getString(context, "player"))))))
						.then(Commands.literal("reload")
								.requires(source -> allowed(source, PermissionChecker.ADMIN_RELOAD))
								.executes(context -> withPlayer(context.getSource(), (runtime, player) -> {
									runtimeTell(player, runtime.reload());
									return 1;
								})))));
	}

	private static boolean allowed(CommandSourceStack source, String node) {
		TradeRuntime runtime = TradeEvents.runtime();
		ServerPlayer player = source.getPlayer();
		return runtime != null && player != null && runtime.permits(player, node);
	}

	private static java.util.List<String> page(TradeRuntime runtime, String selector, int explicitPage) {
		if (selector.chars().allMatch(Character::isDigit)) {
			return runtime.logPage(null, Integer.parseInt(selector));
		}
		return runtime.logPage(selector, explicitPage);
	}

	private static int say(ServerPlayer player, java.util.List<String> lines) {
		for (String line : lines) {
			player.sendSystemMessage(Component.literal(line));
		}
		return 1;
	}

	private static void runtimeTell(ServerPlayer player, String message) {
		player.sendSystemMessage(Component.literal(message));
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
