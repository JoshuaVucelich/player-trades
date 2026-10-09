package com.vucelich.playertrades.session;

public final class ProximityRules {
	public enum Problem {
		OK,
		DIFFERENT_WORLD,
		TOO_FAR
	}

	private ProximityRules() {}

	public static Problem check(boolean sameDimension, double distance, boolean requireSameWorld, double maxDistance) {
		if (requireSameWorld && !sameDimension) {
			return Problem.DIFFERENT_WORLD;
		}
		if (maxDistance > 0 && sameDimension && distance > maxDistance) {
			return Problem.TOO_FAR;
		}
		return Problem.OK;
	}
}
