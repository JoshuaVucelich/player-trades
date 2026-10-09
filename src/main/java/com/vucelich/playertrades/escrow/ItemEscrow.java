package com.vucelich.playertrades.escrow;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;

public final class ItemEscrow {
	public record Entry(ItemStack stack, ItemOrigin origin, java.util.UUID sequence) {}

	private final List<Entry> entries = new ArrayList<>();

	public int size() {
		return entries.size();
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	public ItemStack view(int index) {
		return entries.get(index).stack();
	}

	public Entry entry(int index) {
		return entries.get(index);
	}

	public void add(ItemStack stack, ItemOrigin origin, java.util.UUID sequence) {
		entries.add(new Entry(stack, origin, sequence));
	}

	public Entry remove(int index) {
		return entries.remove(index);
	}

	public boolean holdsContentsOf(ItemOrigin box) {
		for (Entry entry : entries) {
			if (box.sameBox(entry.origin())) {
				return true;
			}
		}
		return false;
	}

	public List<Entry> drain() {
		List<Entry> copy = List.copyOf(entries);
		entries.clear();
		return copy;
	}

	public List<Entry> copyEntries() {
		List<Entry> copies = new ArrayList<>();
		for (Entry entry : entries) {
			copies.add(new Entry(entry.stack().copy(), entry.origin(), entry.sequence()));
		}
		return copies;
	}

	public void restore(List<Entry> saved) {
		entries.clear();
		for (Entry entry : saved) {
			entries.add(new Entry(entry.stack().copy(), entry.origin(), entry.sequence()));
		}
	}

	public void clear() {
		entries.clear();
	}
}
