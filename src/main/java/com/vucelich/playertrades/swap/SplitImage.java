package com.vucelich.playertrades.swap;

import java.util.List;

/**
 * Storage slots plus the ender chest. Armor and offhand are not part of this image.
 */
public interface SplitImage {
	int storageSize();

	int enderSize();

	SlotState storageSlot(int index);

	SlotState enderSlot(int index);

	void apply(List<RoutedPlacement> placements, List<IncomingStack> incoming);

	SplitImage copy();

	void restore(SplitImage snapshot);

	List<Object> drainDrops();
}
