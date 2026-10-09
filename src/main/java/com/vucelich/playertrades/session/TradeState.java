package com.vucelich.playertrades.session;

public enum TradeState {
	OFFERED,
	NEGOTIATING,
	COUNTDOWN,
	COMMITTING,
	COMPLETED,
	CANCELLED;

	public boolean isTerminal() {
		return this == COMPLETED || this == CANCELLED;
	}

	public boolean menuOpen() {
		return this == NEGOTIATING || this == COUNTDOWN;
	}
}
