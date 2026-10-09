package com.vucelich.playertrades.fabric;

import java.util.UUID;

import com.vucelich.playertrades.perm.PermissionChecker;
import com.vucelich.playertrades.perm.PermissionDecision;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * Asks LuckPerms by reflection when that mod is installed. There is no compile dependency on it.
 * Floodgate UUIDs use the same check as Java UUIDs.
 */
public final class FabricPermissions implements PermissionChecker {
	private final MinecraftServer server;
	private final boolean luckPerms;

	public FabricPermissions(MinecraftServer server) {
		this.server = server;
		boolean present;
		try {
			Class.forName("net.luckperms.api.LuckPermsProvider");
			present = true;
		} catch (ClassNotFoundException notInstalled) {
			present = false;
		}
		this.luckPerms = present;
	}

	@Override
	public boolean allows(UUID player, String node) {
		PermissionDecision.Answer answer = PermissionDecision.Answer.UNSET;
		if (luckPerms) {
			answer = luckPerms(player, node);
		}
		return PermissionDecision.allows(node, luckPerms, answer, operator(player));
	}

	private boolean operator(UUID player) {
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		return online != null && online.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	private static PermissionDecision.Answer luckPerms(UUID player, String node) {
		try {
			Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
			Object api = provider.getMethod("get").invoke(null);
			Object users = api.getClass().getMethod("getUserManager").invoke(api);
			Object user = users.getClass().getMethod("getUser", UUID.class).invoke(users, player);
			if (user == null) {
				return PermissionDecision.Answer.UNSET;
			}
			Object cached = user.getClass().getMethod("getCachedData").invoke(user);
			Object data = cached.getClass().getMethod("getPermissionData").invoke(cached);
			Object result = data.getClass().getMethod("checkPermission", String.class).invoke(data, node);
			return switch (String.valueOf(result)) {
				case "TRUE" -> PermissionDecision.Answer.ALLOW;
				case "FALSE" -> PermissionDecision.Answer.DENY;
				default -> PermissionDecision.Answer.UNSET;
			};
		} catch (ReflectiveOperationException failure) {
			return PermissionDecision.Answer.UNSET;
		}
	}
}
