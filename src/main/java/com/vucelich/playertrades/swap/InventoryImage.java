package com.vucelich.playertrades.swap;

import java.util.List;

public interface InventoryImage {
	int size();

	SlotState slot(int index);

	void apply(List<Placement> placements, List<IncomingStack> incoming);

	InventoryImage copy();

	void restore(InventoryImage snapshot);
}
