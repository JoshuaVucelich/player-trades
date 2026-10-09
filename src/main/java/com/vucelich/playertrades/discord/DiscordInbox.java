package com.vucelich.playertrades.discord;

import java.util.UUID;

/**
 * Inbound Discord text. The implementation must run on the server thread and must only send chat.
 */
@FunctionalInterface
public interface DiscordInbox {
	void deliver(UUID sessionId, String speaker, String text);
}
