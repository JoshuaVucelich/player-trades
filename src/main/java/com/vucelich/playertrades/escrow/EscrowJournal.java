package com.vucelich.playertrades.escrow;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * One file per trade session under {@code world/data/player_trades/escrow}.
 * The file name is the session id, so a replay can return that session only once.
 */
public final class EscrowJournal {
	public static final String QUARANTINE = "quarantine";

	private EscrowJournal() {}

	public record Line(JsonElement codec, String kind, int slot, int inner, UUID sequence) {}

	public record Side(UUID player, List<Line> lines) {
		public Side {
			lines = lines == null ? List.of() : List.copyOf(lines);
		}
	}

	public record Page(UUID sessionId, List<Side> sides) {
		public Page {
			sides = sides == null ? List.of() : List.copyOf(sides);
		}

		public boolean empty() {
			for (Side side : sides) {
				if (!side.lines().isEmpty()) {
					return false;
				}
			}
			return true;
		}
	}

	public static void write(Path directory, Page page) throws IOException {
		if (page == null || page.sessionId() == null) {
			throw new IOException("Escrow journal is missing a session id");
		}
		Files.createDirectories(directory);
		if (page.empty()) {
			delete(directory, page.sessionId());
			return;
		}
		Path target = file(directory, page.sessionId());
		Path temporary = directory.resolve(page.sessionId() + ".json.tmp");
		Files.writeString(temporary, toJson(page).toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
		try {
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException unsupported) {
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
		}
		syncDirectory(directory);
	}

	public static void delete(Path directory, UUID sessionId) throws IOException {
		if (directory == null || sessionId == null) {
			return;
		}
		Files.deleteIfExists(file(directory, sessionId));
		Files.deleteIfExists(directory.resolve(sessionId + ".json.tmp"));
		syncDirectory(directory);
	}

	public static Optional<Page> read(Path file) {
		if (file == null || !Files.isRegularFile(file)) {
			return Optional.empty();
		}
		UUID sessionId = sessionId(file.getFileName().toString());
		if (sessionId == null) {
			return Optional.empty();
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(file));
			if (!parsed.isJsonObject()) {
				return Optional.empty();
			}
			return Optional.of(new Page(sessionId, sides(parsed.getAsJsonObject())));
		} catch (IOException | JsonSyntaxException | IllegalStateException failure) {
			return Optional.empty();
		}
	}

	public static List<Page> readAll(Path directory) throws IOException {
		List<Page> pages = new ArrayList<>();
		if (directory == null || !Files.isDirectory(directory)) {
			return pages;
		}
		try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
			for (Path file : files) {
				if (QUARANTINE.equals(file.getFileName().toString())) {
					continue;
				}
				Optional<Page> page = read(file);
				if (page.isPresent()) {
					pages.add(page.get());
				} else {
					quarantine(directory, file);
				}
			}
		}
		return pages;
	}

	public static void quarantine(Path directory, Path file) throws IOException {
		if (directory == null || file == null || !Files.isRegularFile(file)) {
			return;
		}
		Path quarantine = directory.resolve(QUARANTINE);
		Files.createDirectories(quarantine);
		Path target = quarantine.resolve(file.getFileName());
		if (Files.exists(target)) {
			target = quarantine.resolve(file.getFileName().toString() + "." + System.nanoTime());
		}
		Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
		syncDirectory(quarantine);
		syncDirectory(directory);
	}

	public static void syncDirectory(Path directory) throws IOException {
		if (directory == null || !Files.isDirectory(directory)) {
			return;
		}
		try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
			channel.force(true);
		}
	}

	public static Path file(Path directory, UUID sessionId) {
		return directory.resolve(sessionId + ".json");
	}

	private static JsonObject toJson(Page page) {
		JsonObject root = new JsonObject();
		root.addProperty("sessionId", page.sessionId().toString());
		JsonArray sides = new JsonArray();
		for (Side side : page.sides()) {
			if (side.player() == null || side.lines().isEmpty()) {
				continue;
			}
			JsonObject sideJson = new JsonObject();
			sideJson.addProperty("player", side.player().toString());
			JsonArray lines = new JsonArray();
			for (Line line : side.lines()) {
				JsonObject lineJson = new JsonObject();
				lineJson.add("codec", line.codec() == null ? new JsonObject() : line.codec());
				lineJson.addProperty("kind", line.kind() == null ? ItemOrigin.Kind.INVENTORY.name() : line.kind());
				lineJson.addProperty("slot", line.slot());
				lineJson.addProperty("inner", line.inner());
				if (line.sequence() != null) {
					lineJson.addProperty("sequence", line.sequence().toString());
				}
				lines.add(lineJson);
			}
			sideJson.add("lines", lines);
			sides.add(sideJson);
		}
		root.add("sides", sides);
		return root;
	}

	private static List<Side> sides(JsonObject root) {
		List<Side> sides = new ArrayList<>();
		JsonArray array = root.has("sides") && root.get("sides").isJsonArray() ? root.getAsJsonArray("sides") : new JsonArray();
		for (JsonElement element : array) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject side = element.getAsJsonObject();
			UUID player = uuid(text(side, "player"));
			if (player == null) {
				continue;
			}
			List<Line> lines = new ArrayList<>();
			if (side.has("lines") && side.get("lines").isJsonArray()) {
				for (JsonElement lineElement : side.getAsJsonArray("lines")) {
					if (!lineElement.isJsonObject()) {
						continue;
					}
					JsonObject line = lineElement.getAsJsonObject();
					JsonElement codec = line.has("codec") ? line.get("codec") : new JsonObject();
					lines.add(new Line(codec, text(line, "kind"), integer(line, "slot"), integer(line, "inner"), uuid(text(line, "sequence"))));
				}
			}
			if (!lines.isEmpty()) {
				sides.add(new Side(player, lines));
			}
		}
		return sides;
	}

	private static UUID sessionId(String fileName) {
		if (fileName == null || !fileName.endsWith(".json") || fileName.endsWith(".tmp")) {
			return null;
		}
		return uuid(fileName.substring(0, fileName.length() - ".json".length()));
	}

	private static UUID uuid(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(text);
		} catch (IllegalArgumentException bad) {
			return null;
		}
	}

	private static String text(JsonObject object, String field) {
		if (!object.has(field) || !object.get(field).isJsonPrimitive()) {
			return "";
		}
		return object.get(field).getAsString();
	}

	private static int integer(JsonObject object, String field) {
		if (!object.has(field) || !object.get(field).isJsonPrimitive()) {
			return -1;
		}
		try {
			return object.get(field).getAsInt();
		} catch (NumberFormatException | ClassCastException bad) {
			return -1;
		}
	}
}
