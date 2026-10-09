package com.vucelich.playertrades.discord;

import java.util.function.Consumer;

public final class InboundDispatch {
	private InboundDispatch() {}

	/**
	 * Hand the delivery to the server thread. This method does not run {@code delivery} itself.
	 */
	public static void onServerThread(Consumer<Runnable> serverThread, Runnable delivery) {
		if (serverThread == null || delivery == null) {
			return;
		}
		serverThread.accept(delivery);
	}
}
