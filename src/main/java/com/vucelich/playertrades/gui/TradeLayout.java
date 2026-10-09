package com.vucelich.playertrades.gui;

public final class TradeLayout {
	public static final int PARTNER_OFFER = 0;
	public static final int PARTNER_LAMP = 8;
	public static final int OWN_OFFER = 9;
	public static final int OWN_LAMP = 17;
	public static final int SOURCE_START = 18;
	public static final int SOURCE_SLOTS = 27;
	public static final int PREV_PAGE = 45;
	public static final int TAB_INVENTORY = 46;
	public static final int TAB_ENDER = 47;
	public static final int TAB_SHULKER = 48;
	public static final int NEXT_PAGE = 49;
	public static final int CANCEL = 52;
	public static final int READY = 53;
	public static final int PLAYER_START = 54;
	public static final int OFFER_SLOTS = 8;
	public static final int PAGES = 2;

	private TradeLayout() {}

	public static int inventoryIndex(int menuSlot, int page) {
		if (menuSlot >= PLAYER_START && menuSlot <= PLAYER_START + 26) {
			return 9 + (menuSlot - PLAYER_START);
		}
		if (menuSlot >= PLAYER_START + 27 && menuSlot <= PLAYER_START + 35) {
			return menuSlot - (PLAYER_START + 27);
		}
		if (menuSlot >= SOURCE_START && menuSlot < SOURCE_START + SOURCE_SLOTS) {
			int cell = menuSlot - SOURCE_START;
			if (page <= 0) {
				return 9 + cell;
			}
			return cell < 9 ? cell : -1;
		}
		return -1;
	}

	public static int clampPage(int page) {
		if (page < 0) {
			return 0;
		}
		if (page >= PAGES) {
			return PAGES - 1;
		}
		return page;
	}
}
