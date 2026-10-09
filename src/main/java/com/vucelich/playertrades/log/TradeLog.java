package com.vucelich.playertrades.log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Append-only JSON lines. Opening the file never truncates it.
 */
public final class TradeLog {
	private TradeLog() {}

	public static void append(Path file, String jsonLine) throws IOException {
		Files.createDirectories(file.getParent());
		String line = jsonLine.endsWith("\n") ? jsonLine : jsonLine + "\n";
		Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
	}

	public static List<Entry> read(Path file) throws IOException {
		if (file == null || Files.notExists(file)) {
			return List.of();
		}
		List<Entry> entries = new ArrayList<>();
		for (String line : Files.readAllLines(file)) {
			if (line.isBlank()) {
				continue;
			}
			Entry entry = parse(line);
			if (entry != null) {
				entries.add(entry);
			}
		}
		return entries;
	}

	public static Entry parse(String line) {
		try {
			JsonElement element = JsonParser.parseString(line);
			if (!element.isJsonObject()) {
				return null;
			}
			JsonObject obj = element.getAsJsonObject();
			return new Entry(
					text(obj, "id"),
					text(obj, "time"),
					text(obj, "result"),
					text(obj, "reason"),
					text(obj, "world"),
					players(obj),
					offers(obj));
		} catch (RuntimeException failure) {
			return null;
		}
	}

	private static List<Person> players(JsonObject obj) {
		List<Person> people = new ArrayList<>();
		if (!obj.has("players") || !obj.get("players").isJsonArray()) {
			return people;
		}
		for (JsonElement element : obj.getAsJsonArray("players")) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject person = element.getAsJsonObject();
			people.add(new Person(text(person, "uuid"), text(person, "name")));
		}
		return people;
	}

	private static List<List<Stack>> offers(JsonObject obj) {
		List<List<Stack>> sides = new ArrayList<>();
		if (!obj.has("offers") || !obj.get("offers").isJsonArray()) {
			return sides;
		}
		for (JsonElement sideElement : obj.getAsJsonArray("offers")) {
			List<Stack> stacks = new ArrayList<>();
			if (sideElement.isJsonArray()) {
				for (JsonElement stackElement : sideElement.getAsJsonArray()) {
					if (!stackElement.isJsonObject()) {
						continue;
					}
					JsonObject stack = stackElement.getAsJsonObject();
					int count = stack.has("count") && stack.get("count").isJsonPrimitive() ? stack.get("count").getAsInt() : 0;
					String custom = stack.has("customName") && !stack.get("customName").isJsonNull() ? stack.get("customName").getAsString() : "";
					stacks.add(new Stack(text(stack, "id"), count, custom));
				}
			}
			sides.add(stacks);
		}
		return sides;
	}

	private static String text(JsonObject obj, String field) {
		if (!obj.has(field) || obj.get(field).isJsonNull()) {
			return "";
		}
		JsonElement element = obj.get(field);
		return element.isJsonPrimitive() ? element.getAsString() : "";
	}

	public record Person(String uuid, String name) {}

	public record Stack(String id, int count, String customName) {}

	public record Entry(String id, String time, String result, String reason, String world, List<Person> players, List<List<Stack>> offers) {
		public boolean involves(String name) {
			if (name == null || name.isBlank()) {
				return true;
			}
			for (Person person : players) {
				if (name.equals(person.name())) {
					return true;
				}
			}
			return false;
		}
	}
}
