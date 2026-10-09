package com.vucelich.playertrades.perm;

/**
 * LuckPerms wins when it has an answer. An unset node falls through to the vanilla rule.
 * A LuckPerms deny is not promoted to allow because the player is an operator.
 */
public final class PermissionDecision {
	public enum Answer {
		ALLOW,
		DENY,
		UNSET
	}

	private PermissionDecision() {}

	public static boolean allows(String node, boolean luckPermsInstalled, Answer luckPerms, boolean operator) {
		if (luckPermsInstalled && luckPerms != Answer.UNSET) {
			return luckPerms == Answer.ALLOW;
		}
		if (PermissionChecker.TRADE.equals(node)) {
			return true;
		}
		return operator;
	}
}
