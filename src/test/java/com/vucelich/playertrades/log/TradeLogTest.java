package com.vucelich.playertrades.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TradeLogTest {
	@Test
	void appendDoesNotTruncateAndKeepsComponents() throws Exception {
		Path file = Files.createTempDirectory("player-trades-log").resolve("trades.jsonl");
		String first = "{\"id\":\"a\",\"time\":\"2026-10-09T16:00:00Z\",\"result\":\"CANCELLED\",\"reason\":\"timeout\",\"world\":\"minecraft:overworld\",\"players\":[{\"uuid\":\"1\",\"name\":\"Alice\"},{\"uuid\":\"2\",\"name\":\"Bob\"}],\"offers\":[[{\"id\":\"minecraft:diamond\",\"count\":64,\"customName\":\"Named\",\"codec\":{\"id\":\"minecraft:diamond\",\"count\":64}}],[]]}";
		String second = "{\"id\":\"b\",\"time\":\"2026-10-09T16:05:00Z\",\"result\":\"COMPLETED\",\"reason\":\"\",\"world\":\"minecraft:overworld\",\"players\":[{\"uuid\":\"1\",\"name\":\"Alice\"},{\"uuid\":\"2\",\"name\":\"Cara\"}],\"offers\":[[],[]]}";
		TradeLog.append(file, first);
		TradeLog.append(file, second);
		String stored = Files.readString(file);
		assertTrue(stored.startsWith(first));
		assertTrue(stored.contains(second));
		assertEquals(2, TradeLog.read(file).size());
		TradeLog.Entry newest = TradeLog.read(file).get(1);
		assertEquals("COMPLETED", newest.result());
		TradeLog.Entry timeout = TradeLog.read(file).get(0);
		assertEquals("Named", timeout.offers().get(0).get(0).customName());
		assertEquals("minecraft:diamond", timeout.offers().get(0).get(0).id());
		assertTrue(timeout.involves("Alice"));
		assertTrue(!timeout.involves("Cara"));
	}
}
