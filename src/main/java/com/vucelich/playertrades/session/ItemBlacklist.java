package com.vucelich.playertrades.session;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ItemBlacklist {
	private final Set<String> ids;

	public ItemBlacklist(List<String> ids) {
		this.ids = new HashSet<>();
		for (String id : ids) {
			if (id != null && !id.isBlank()) {
				this.ids.add(id);
			}
		}
	}

	public boolean rejects(String itemId) {
		return itemId != null && ids.contains(itemId);
	}

	public boolean isEmpty() {
		return ids.isEmpty();
	}
}
