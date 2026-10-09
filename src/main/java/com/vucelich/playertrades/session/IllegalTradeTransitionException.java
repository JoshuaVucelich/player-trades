package com.vucelich.playertrades.session;

public final class IllegalTradeTransitionException extends IllegalStateException {
	private final TradeState from;
	private final String action;

	public IllegalTradeTransitionException(TradeState from, String action) {
		super("Illegal trade transition: " + action + " from " + from);
		this.from = from;
		this.action = action;
	}

	public TradeState from() {
		return from;
	}

	public String action() {
		return action;
	}
}
