package com.vucelich.playertrades.escrow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonParser;

class EscrowJournalTest {
	private final UUID session = UUID.fromString("00000000-0000-0000-0000-000000000010");
	private final UUID alice = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private final UUID bob = UUID.fromString("00000000-0000-0000-0000-00000000000b");

	@Test
	void journalRoundTripKeepsTheCodecAndOrigin(@TempDir Path directory) throws Exception {
		EscrowJournal.Page page = page(session, alice, bob);
		EscrowJournal.write(directory, page);
		EscrowJournal.Page loaded = EscrowJournal.read(EscrowJournal.file(directory, session)).orElseThrow();
		assertEquals(session, loaded.sessionId());
		assertEquals(alice, loaded.sides().get(0).player());
		assertEquals("INVENTORY", loaded.sides().get(0).lines().get(0).kind());
		assertEquals(4, loaded.sides().get(0).lines().get(0).slot());
		assertEquals("minecraft:diamond", loaded.sides().get(0).lines().get(0).codec().getAsJsonObject().get("id").getAsString());
		assertEquals(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), loaded.sides().get(0).lines().get(0).sequence());
		assertEquals(1, EscrowJournal.readAll(directory).size());
		assertEquals(2, loaded.sides().size());
	}

	@Test
	void emptyPageDeletesTheSessionFile(@TempDir Path directory) throws Exception {
		EscrowJournal.write(directory, page(session, alice, bob));
		EscrowJournal.write(directory, new EscrowJournal.Page(session, List.of()));
		assertFalse(Files.exists(EscrowJournal.file(directory, session)));
	}

	@Test
	void offerWritesTheJournalThenClearsThenSaves() {
		List<String> events = new ArrayList<>();
		EscrowTake.offer(() -> events.add("journal"), () -> events.add("clear"), () -> events.add("save"));
		assertEquals(List.of("journal", "clear", "save"), events);
		events.clear();
		assertThrows(IllegalStateException.class, () -> EscrowTake.offer(() -> {
			events.add("journal");
			throw new IllegalStateException("disk");
		}, () -> events.add("clear"), () -> events.add("save")));
		assertEquals(List.of("journal"), events);
		events.clear();
		assertThrows(IllegalStateException.class, () -> EscrowTake.offer(() -> events.add("journal"), () -> {
			events.add("clear");
			throw new IllegalStateException("slot");
		}, () -> events.add("save")));
		assertEquals(List.of("journal", "clear"), events);
	}

	@Test
	void settleMovesThenSavesThenRewritesTheJournal() {
		List<String> events = new ArrayList<>();
		EscrowTake.settle(() -> events.add("move"), () -> events.add("save"), () -> events.add("rewrite"));
		assertEquals(List.of("move", "save", "rewrite"), events);
		events.clear();
		assertThrows(IllegalStateException.class, () -> EscrowTake.settle(() -> {
			events.add("move");
			throw new IllegalStateException("inventory");
		}, () -> events.add("save"), () -> events.add("rewrite")));
		assertEquals(List.of("move"), events);
		events.clear();
		assertThrows(IllegalStateException.class, () -> EscrowTake.settle(() -> events.add("move"), () -> {
			events.add("save");
			throw new IllegalStateException("disk");
		}, () -> events.add("rewrite")));
		assertEquals(List.of("move", "save"), events);
	}

	@Test
	void replayReturnsEachOwnerOnceAndLeavesTheOfflinePlayer(@TempDir Path directory) throws Exception {
		EscrowJournal.write(directory, page(session, alice, bob));
		EscrowReplay replay = new EscrowReplay();
		EscrowJournal.Page stored = EscrowJournal.readAll(directory).get(0);
		List<EscrowJournal.Side> first = replay.claim(stored, Set.of(alice));
		assertEquals(1, first.size());
		assertEquals(alice, first.get(0).player());
		assertTrue(replay.claim(stored, Set.of(alice)).isEmpty());
		EscrowJournal.write(directory, EscrowReplay.without(stored, Set.of(alice)));

		EscrowJournal.Page waiting = EscrowJournal.read(EscrowJournal.file(directory, session)).orElseThrow();
		assertEquals(List.of(bob), waiting.sides().stream().map(EscrowJournal.Side::player).toList());
		assertTrue(replay.claim(waiting, Set.of(alice)).isEmpty());
		assertEquals(bob, replay.claim(waiting, Set.of(bob)).get(0).player());
		EscrowJournal.write(directory, EscrowReplay.without(waiting, Set.of(bob)));
		assertTrue(EscrowJournal.readAll(directory).isEmpty());
		assertTrue(replay.claim(waiting, Set.of(bob)).isEmpty());
	}

	@Test
	void aBrokenFileIsQuarantined(@TempDir Path directory) throws Exception {
		Files.createDirectories(directory);
		Files.writeString(directory.resolve(session + ".json"), "{not json");
		assertTrue(EscrowJournal.readAll(directory).isEmpty());
		assertFalse(Files.exists(EscrowJournal.file(directory, session)));
		assertTrue(Files.isRegularFile(directory.resolve(EscrowJournal.QUARANTINE).resolve(session + ".json")));
	}

	private static EscrowJournal.Page page(UUID session, UUID alice, UUID bob) {
		return new EscrowJournal.Page(session, List.of(
				new EscrowJournal.Side(alice, List.of(new EscrowJournal.Line(JsonParser.parseString("{\"id\":\"minecraft:diamond\",\"count\":3}"), "INVENTORY", 4, -1, UUID.fromString("00000000-0000-0000-0000-0000000000a1")))),
				new EscrowJournal.Side(bob, List.of(new EscrowJournal.Line(JsonParser.parseString("{\"id\":\"minecraft:emerald\",\"count\":1}"), "ENDER", 2, -1, UUID.fromString("00000000-0000-0000-0000-0000000000b1"))))));
	}
}
