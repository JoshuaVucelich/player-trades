package com.vucelich.playertrades.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.vucelich.playertrades.config.DiscordSettings;

class DiscordBridgeTest {
	@Test
	void disabledConfigDoesNotAskForAConnection() {
		assertFalse(ConnectionPlan.shouldConnect(null));
		assertFalse(ConnectionPlan.shouldConnect(DiscordSettings.defaults()));
		assertFalse(ConnectionPlan.shouldConnect(new DiscordSettings(true, "  ", "", "", true)));
		assertFalse(ConnectionPlan.shouldConnect(new DiscordSettings(false, "super-secret-token-value", "1", "2", true)));
		assertTrue(ConnectionPlan.shouldConnect(new DiscordSettings(true, "super-secret-token-value", "1", "2", true)));
	}

	@Test
	void tokenIsRedactedAndABlankTokenIsLeftAlone() {
		assertEquals("login failed: [redacted]", TokenRedaction.redact("login failed: super-secret-token-value", "super-secret-token-value"));
		assertEquals("[redacted] and [redacted]", TokenRedaction.redact("super-secret-token-value and super-secret-token-value", "super-secret-token-value"));
		assertEquals("", TokenRedaction.redact(null, "super-secret-token-value"));
		assertEquals("Discord bridge disabled", TokenRedaction.redact("Discord bridge disabled", ""));
		assertEquals("Discord bridge disabled", TokenRedaction.redact("Discord bridge disabled", "   "));
		assertFalse(TokenRedaction.redact("header super-secret-token-value trailer", "super-secret-token-value").contains("super-secret-token-value"));
	}

	@Test
	void openOfferIncludesTheAcceptCommandAndClosedOfferDoesNot() {
		String open = OfferMessages.open(".Bob", "diamond sword");
		assertTrue(open.contains(".Bob"));
		assertTrue(open.contains("diamond sword"));
		assertTrue(open.contains("/trade accept .Bob"));
		for (OfferNotice notice : OfferNotice.values()) {
			String closed = OfferMessages.closed(".Bob", notice);
			assertTrue(closed.contains(".Bob"));
			assertFalse(closed.contains("/trade accept"), notice.name());
		}
		assertEquals("Alice is trading: open offer\n/trade accept Alice", OfferMessages.open("Alice", " "));
	}

	@Test
	void negotiationSkipsCommandsWtsAndOutsiders() {
		assertTrue(RelayPolicy.fromGame(true, true, true, "does the sword have mending?"));
		assertFalse(RelayPolicy.fromGame(false, true, true, "hello"));
		assertFalse(RelayPolicy.fromGame(true, false, true, "hello"));
		assertFalse(RelayPolicy.fromGame(true, true, false, "hello"));
		assertFalse(RelayPolicy.fromGame(true, true, true, "/trade cancel"));
		assertFalse(RelayPolicy.fromGame(true, true, true, "WTS diamonds"));
		assertFalse(RelayPolicy.fromDiscord("wts books"));
		assertFalse(RelayPolicy.fromDiscord("  "));
		assertTrue(RelayPolicy.fromDiscord("I can do 32"));
	}

	@Test
	void offlinePlayersAreNotASwapPath() {
		assertTrue(RelayPolicy.bothOnline(true, true, true));
		assertFalse(RelayPolicy.bothOnline(true, false, true));
		assertFalse(RelayPolicy.bothOnline(true, true, false));
		assertFalse(RelayPolicy.bothOnline(false, true, true));
	}

	@Test
	void inboundDispatchDoesNotRunTheDeliveryInline() {
		List<String> ran = new ArrayList<>();
		List<Runnable> queued = new ArrayList<>();
		InboundDispatch.onServerThread(queued::add, () -> ran.add("delivered"));
		assertTrue(ran.isEmpty());
		assertEquals(1, queued.size());
		queued.get(0).run();
		assertEquals(List.of("delivered"), ran);
		InboundDispatch.onServerThread(null, () -> ran.add("no"));
		assertEquals(List.of("delivered"), ran);
	}

	@Test
	void noOpBridgeIgnoresEveryCall() {
		DiscordBridge bridge = new NoOpDiscordBridge();
		UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
		bridge.postOffer(id, "Alice", "iron");
		bridge.updateOffer(id, "Alice", OfferNotice.CANCELLED);
		bridge.openThread(id, id, "Alice", "Bob");
		bridge.relayFromGame(id, "Alice", "hello");
		bridge.shutdown();
		bridge.shutdown();
	}

	@Test
	void bridgeApiDoesNotAcceptItems() {
		for (Method method : DiscordBridge.class.getDeclaredMethods()) {
			assertFalse(method.getName().toLowerCase().contains("commit"), method.getName());
			assertFalse(method.getName().toLowerCase().contains("item"), method.getName());
			for (Class<?> type : method.getParameterTypes()) {
				assertFalse(type.getName().contains("Item"), type.getName());
				assertFalse(type.getName().contains("minecraft"), type.getName());
			}
		}
	}

	@Test
	void discordSourcesDoNotTouchItemsOrTheLoader() throws Exception {
		Path root = Path.of("src/main/java/com/vucelich/playertrades/discord");
		try (Stream<Path> files = Files.walk(root)) {
			for (Path path : files.filter(file -> file.toString().endsWith(".java")).toList()) {
				String text = Files.readString(path);
				String relative = root.relativize(path).toString().replace('\\', '/');
				assertFalse(text.contains("ItemStack"), relative);
				assertFalse(text.contains("net.minecraft"), relative);
				assertFalse(text.contains("net.fabricmc"), relative);
				assertFalse(text.contains("ItemEscrow"), relative);
				assertFalse(text.contains("SwapCommit"), relative);
				if (relative.startsWith("jda/")) {
					assertTrue(text.contains("onServerThread"), relative);
				} else {
					assertFalse(text.contains("net.dv8tion"), relative);
				}
			}
		}
		String runtime = Files.readString(Path.of("src/main/java/com/vucelich/playertrades/fabric/TradeRuntime.java"));
		assertFalse(runtime.contains("net.dv8tion"));
		String example = Files.readString(Path.of("config/player_trades.example.json"));
		assertTrue(example.contains("\"token\": \"\""));
		assertFalse(example.contains("super-secret-token-value"));
	}
}
