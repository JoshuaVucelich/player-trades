package com.vucelich.playertrades.perm;

import java.util.UUID;

/**
 * LuckPerms is asked in M3. Until then only the trade node is granted, so cooldown and proximity apply to everyone.
 */
public interface PermissionChecker {
	String TRADE = "playertrades.trade";
	String BYPASS_COOLDOWN = "playertrades.bypass.cooldown";
	String BYPASS_PROXIMITY = "playertrades.bypass.proximity";
	String ADMIN_LOG = "playertrades.admin.log";
	String ADMIN_CANCEL = "playertrades.admin.cancel";
	String ADMIN_RELOAD = "playertrades.admin.reload";

	boolean allows(UUID player, String node);

	PermissionChecker TRADE_ONLY = (player, node) -> TRADE.equals(node);
}
