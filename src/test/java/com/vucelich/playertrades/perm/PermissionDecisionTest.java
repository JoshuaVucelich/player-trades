package com.vucelich.playertrades.perm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PermissionDecisionTest {
	@Test
	void withoutLuckPermsTradeIsOpenAndAdminNeedsOperator() {
		assertTrue(PermissionDecision.allows(PermissionChecker.TRADE, false, PermissionDecision.Answer.UNSET, false));
		assertFalse(PermissionDecision.allows(PermissionChecker.ADMIN_LOG, false, PermissionDecision.Answer.UNSET, false));
		assertTrue(PermissionDecision.allows(PermissionChecker.ADMIN_CANCEL, false, PermissionDecision.Answer.UNSET, true));
		assertTrue(PermissionDecision.allows(PermissionChecker.BYPASS_COOLDOWN, false, PermissionDecision.Answer.UNSET, true));
	}

	@Test
	void luckPermsDenyIsNotOverriddenByOperator() {
		assertFalse(PermissionDecision.allows(PermissionChecker.ADMIN_LOG, true, PermissionDecision.Answer.DENY, true));
		assertTrue(PermissionDecision.allows(PermissionChecker.ADMIN_LOG, true, PermissionDecision.Answer.ALLOW, false));
		assertTrue(PermissionDecision.allows(PermissionChecker.TRADE, true, PermissionDecision.Answer.UNSET, false));
		assertFalse(PermissionDecision.allows(PermissionChecker.ADMIN_RELOAD, true, PermissionDecision.Answer.UNSET, false));
	}
}
