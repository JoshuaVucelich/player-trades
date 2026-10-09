package com.vucelich.playertrades.escrow;

public record ItemOrigin(Kind kind, int slot, int inner) {
	public enum Kind {
		INVENTORY,
		ENDER,
		SHULKER_INVENTORY,
		SHULKER_ENDER
	}

	public static ItemOrigin inventory(int slot) {
		return new ItemOrigin(Kind.INVENTORY, slot, -1);
	}

	public static ItemOrigin ender(int slot) {
		return new ItemOrigin(Kind.ENDER, slot, -1);
	}

	public static ItemOrigin shulker(boolean enderChest, int boxSlot, int inner) {
		return new ItemOrigin(enderChest ? Kind.SHULKER_ENDER : Kind.SHULKER_INVENTORY, boxSlot, inner);
	}

	public boolean shulker() {
		return kind == Kind.SHULKER_INVENTORY || kind == Kind.SHULKER_ENDER;
	}

	public boolean sameBox(ItemOrigin other) {
		return other != null && shulker() && kind == other.kind && slot == other.slot;
	}
}
