package com.vucelich.playertrades.session;

import java.util.UUID;

import com.vucelich.playertrades.TradeLimits;

/**
 * One advertisement or the two-player trade that follows it.
 * Item stacks live outside this class so the transitions can be tested without Minecraft.
 */
public final class TradeSession {
	private final UUID id = UUID.randomUUID();
	private final UUID playerA;
	private final String nameA;
	private final String offerText;
	private final long expiresAtMillis;
	private UUID playerB;
	private String nameB = "";
	private TradeState state = TradeState.OFFERED;
	private int generation;
	private int capturedGeneration;
	private int countdownTicks;
	private boolean readyA;
	private boolean readyB;
	private String reason = "";
	private int countdownLength = TradeLimits.COUNTDOWN_TICKS;
	private boolean allowEmptyTrades;
	private long lastActivityMillis;

	private TradeSession(UUID playerA, String nameA, String offerText, long createdAtMillis, long ttlMillis) {
		this.playerA = playerA;
		this.nameA = nameA;
		this.offerText = offerText == null ? "" : offerText.trim();
		this.expiresAtMillis = createdAtMillis + ttlMillis;
	}

	public static TradeSession offer(UUID playerA, String nameA, String offerText, long nowMillis) {
		return offer(playerA, nameA, offerText, nowMillis, TradeLimits.OFFER_TTL_MILLIS);
	}

	public static TradeSession offer(UUID playerA, String nameA, String offerText, long nowMillis, long ttlMillis) {
		return new TradeSession(playerA, nameA, offerText, nowMillis, ttlMillis);
	}

	public UUID id() {
		return id;
	}

	public UUID playerA() {
		return playerA;
	}

	public UUID playerB() {
		return playerB;
	}

	public String nameA() {
		return nameA;
	}

	public String nameB() {
		return nameB;
	}

	public String offerText() {
		return offerText;
	}

	public long expiresAtMillis() {
		return expiresAtMillis;
	}

	public TradeState state() {
		return state;
	}

	public int generation() {
		return generation;
	}

	public int countdownTicks() {
		return countdownTicks;
	}

	public String reason() {
		return reason;
	}

	public boolean expired(long nowMillis) {
		return nowMillis >= expiresAtMillis;
	}

	public boolean ready(Side side) {
		return side == Side.A ? readyA : readyB;
	}

	public Side sideOf(UUID player) {
		if (player.equals(playerA)) {
			return Side.A;
		}
		if (playerB != null && player.equals(playerB)) {
			return Side.B;
		}
		throw new IllegalArgumentException("Player is not in this trade");
	}

	public UUID other(UUID player) {
		Side side = sideOf(player);
		return side == Side.A ? playerB : playerA;
	}

	public String nameOf(UUID player) {
		return sideOf(player) == Side.A ? nameA : nameB;
	}

	public String otherName(UUID player) {
		return sideOf(player) == Side.A ? nameB : nameA;
	}

	public int displaySeconds() {
		if (state != TradeState.COUNTDOWN || countdownTicks <= 0) {
			return 0;
		}
		return (countdownTicks + 19) / 20;
	}

	public void accept(UUID playerB, String nameB) {
		if (state != TradeState.OFFERED) {
			throw new IllegalTradeTransitionException(state, "accept");
		}
		if (playerB.equals(playerA)) {
			throw new IllegalArgumentException("A player cannot accept their own offer");
		}
		this.playerB = playerB;
		this.nameB = nameB;
		this.generation = 1;
		this.lastActivityMillis = System.currentTimeMillis();
		this.state = TradeState.NEGOTIATING;
	}

	public void applyRules(int countdownTicks, boolean allowEmptyTrades) {
		this.countdownLength = Math.max(1, countdownTicks);
		this.allowEmptyTrades = allowEmptyTrades;
	}

	public void touch(long nowMillis) {
		this.lastActivityMillis = nowMillis;
	}

	public boolean idle(long nowMillis, long timeoutMillis) {
		if (timeoutMillis <= 0 || !state.menuOpen() || lastActivityMillis <= 0) {
			return false;
		}
		return nowMillis - lastActivityMillis >= timeoutMillis;
	}

	public void resumeNegotiation() {
		if (state != TradeState.COMMITTING) {
			throw new IllegalTradeTransitionException(state, "hold");
		}
		readyA = false;
		readyB = false;
		countdownTicks = 0;
		state = TradeState.NEGOTIATING;
	}

	public ArmResult arm(Side side, int countA, int countB) {
		if (state != TradeState.NEGOTIATING && state != TradeState.COUNTDOWN) {
			throw new IllegalTradeTransitionException(state, "arm");
		}
		int own = side == Side.A ? countA : countB;
		if (own <= 0 && !allowEmptyTrades) {
			return ArmResult.EMPTY;
		}
		if (state == TradeState.COUNTDOWN) {
			clearReadyFromClose();
			return ArmResult.UNREADY;
		}
		if (ready(side)) {
			if (side == Side.A) {
				readyA = false;
			} else {
				readyB = false;
			}
			return ArmResult.UNREADY;
		}
		if (side == Side.A) {
			readyA = true;
		} else {
			readyB = true;
		}
		if (readyA && readyB && (allowEmptyTrades || (countA > 0 && countB > 0))) {
			state = TradeState.COUNTDOWN;
			countdownTicks = countdownLength;
			capturedGeneration = generation;
			return ArmResult.COUNTDOWN;
		}
		return ArmResult.READY;
	}

	/**
	 * @return true when the caller must commit before returning to the game loop
	 */
	public boolean tickCountdown() {
		if (state != TradeState.COUNTDOWN) {
			throw new IllegalTradeTransitionException(state, "tick");
		}
		if (generation != capturedGeneration) {
			throw new IllegalTradeTransitionException(state, "tick");
		}
		countdownTicks--;
		if (countdownTicks > 0) {
			return false;
		}
		countdownTicks = 0;
		state = TradeState.COMMITTING;
		return true;
	}

	public void onEscrowEdited() {
		if (state != TradeState.NEGOTIATING && state != TradeState.COUNTDOWN) {
			throw new IllegalTradeTransitionException(state, "edit");
		}
		generation++;
		readyA = false;
		readyB = false;
		countdownTicks = 0;
		capturedGeneration = 0;
		state = TradeState.NEGOTIATING;
	}

	public boolean clearReadyFromClose() {
		if (!state.menuOpen()) {
			return false;
		}
		boolean changed = state == TradeState.COUNTDOWN || readyA || readyB;
		state = TradeState.NEGOTIATING;
		countdownTicks = 0;
		capturedGeneration = 0;
		readyA = false;
		readyB = false;
		return changed;
	}

	public void complete() {
		if (state != TradeState.COMMITTING) {
			throw new IllegalTradeTransitionException(state, "complete");
		}
		readyA = false;
		readyB = false;
		state = TradeState.COMPLETED;
	}

	public void rejectNoSpace() {
		if (state != TradeState.COMMITTING) {
			throw new IllegalTradeTransitionException(state, "no-space");
		}
		readyA = false;
		readyB = false;
		countdownTicks = 0;
		state = TradeState.NEGOTIATING;
	}

	public void cancel(String reason) {
		if (state.isTerminal()) {
			throw new IllegalTradeTransitionException(state, "cancel");
		}
		this.reason = reason == null ? "" : reason;
		readyA = false;
		readyB = false;
		countdownTicks = 0;
		state = TradeState.CANCELLED;
	}

	void setGenerationForTest(int generation) {
		this.generation = generation;
	}
}
