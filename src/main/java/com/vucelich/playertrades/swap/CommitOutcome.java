package com.vucelich.playertrades.swap;

public record CommitOutcome(Kind kind, boolean leftShort, boolean rightShort, RuntimeException failure) {
	public enum Kind {
		SUCCESS,
		NO_SPACE,
		ROLLED_BACK
	}

	public static CommitOutcome success() {
		return new CommitOutcome(Kind.SUCCESS, false, false, null);
	}

	public static CommitOutcome noSpace(boolean leftShort, boolean rightShort) {
		return new CommitOutcome(Kind.NO_SPACE, leftShort, rightShort, null);
	}

	public static CommitOutcome rolledBack(RuntimeException failure) {
		return new CommitOutcome(Kind.ROLLED_BACK, false, false, failure);
	}
}
