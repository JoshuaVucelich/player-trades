package com.vucelich.playertrades.escrow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Copies a container list before an edit so the caller's list and the removed stack do not alias.
 */
public final class ContainerCopies {
	private ContainerCopies() {}

	public static <T> Extraction<T> extract(List<T> live, int slot, T empty, Function<T, T> copy, Predicate<T> isEmpty) {
		List<T> slots = new ArrayList<>(live.size());
		for (T item : live) {
			slots.add(copy.apply(item));
		}
		T removed = slots.get(slot);
		if (isEmpty.test(removed)) {
			return null;
		}
		slots.set(slot, empty);
		return new Extraction<>(List.copyOf(slots), removed);
	}

	public record Extraction<T>(List<T> slots, T removed) {}
}
