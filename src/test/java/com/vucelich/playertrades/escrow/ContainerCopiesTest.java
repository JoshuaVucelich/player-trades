package com.vucelich.playertrades.escrow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ContainerCopiesTest {
	@Test
	void extractCopiesTheListAndTheRemovedStack() {
		List<Stack> live = new ArrayList<>();
		live.add(new Stack("diamond", 3, "Named"));
		live.add(new Stack("", 0, ""));
		ContainerCopies.Extraction<Stack> extracted = ContainerCopies.extract(live, 0, new Stack("", 0, ""), Stack::copy, Stack::isEmpty);

		extracted.removed().count = 1;
		assertEquals(3, live.get(0).count);
		assertEquals("Named", live.get(0).name);
		assertEquals(2, extracted.slots().size());
		assertNotSame(live.get(0), extracted.removed());
		assertEquals(0, extracted.slots().get(0).count);
	}

	private static final class Stack {
		final String id;
		int count;
		final String name;

		Stack(String id, int count, String name) {
			this.id = id;
			this.count = count;
			this.name = name;
		}

		Stack copy() {
			return new Stack(id, count, name);
		}

		boolean isEmpty() {
			return count <= 0;
		}
	}
}
