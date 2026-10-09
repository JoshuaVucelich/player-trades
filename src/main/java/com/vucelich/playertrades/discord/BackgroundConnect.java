package com.vucelich.playertrades.discord;

/**
 * Discord login waits on the gateway. That wait must not run on the server thread.
 */
public final class BackgroundConnect {
	public static final String THREAD_NAME = "player-trades-discord";

	private BackgroundConnect() {}

	public static Thread start(Runnable login) {
		Thread thread = new Thread(login, THREAD_NAME);
		thread.setDaemon(true);
		thread.start();
		return thread;
	}
}
