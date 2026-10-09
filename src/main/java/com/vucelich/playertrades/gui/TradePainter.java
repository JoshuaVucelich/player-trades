package com.vucelich.playertrades.gui;

import java.util.List;

import com.vucelich.playertrades.escrow.ItemEscrow;
import com.vucelich.playertrades.session.TradeSession;
import com.vucelich.playertrades.session.TradeState;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class TradePainter {
	private TradePainter() {}

	public static void paint(
			TradeMenu menu,
			TradeSession session,
			ItemEscrow own,
			ItemEscrow partner,
			boolean selfReady,
			boolean partnerReady,
			List<ItemStack> sourceCells,
			int sourcePages) {
		Container display = menu.display();
		menu.setPageCount(sourcePages);
		for (int slot = 0; slot < display.getContainerSize(); slot++) {
			display.setItem(slot, ItemStack.EMPTY);
		}
		paintOffer(display, TradeLayout.PARTNER_OFFER, partner);
		display.setItem(TradeLayout.PARTNER_LAMP, TradeIcons.named(partnerReady ? Items.DYE.lime() : Items.DYE.gray(), partnerReady ? "Partner ready" : "Partner waiting"));
		paintOffer(display, TradeLayout.OWN_OFFER, own);
		display.setItem(TradeLayout.OWN_LAMP, TradeIcons.named(selfReady ? Items.DYE.lime() : Items.DYE.gray(), selfReady ? "You are ready" : "You are not ready"));
		for (int cell = 0; cell < TradeLayout.SOURCE_SLOTS && cell < sourceCells.size(); cell++) {
			ItemStack stack = sourceCells.get(cell);
			if (stack != null && !stack.isEmpty()) {
				display.setItem(TradeLayout.SOURCE_START + cell, stack);
			}
		}
		SourceTab tab = menu.tab();
		display.setItem(TradeLayout.PREV_PAGE, TradeIcons.named(Items.ARROW, "Previous page"));
		display.setItem(TradeLayout.TAB_INVENTORY, TradeIcons.named(Items.CHEST, "Inventory", tab == SourceTab.INVENTORY));
		display.setItem(TradeLayout.TAB_ENDER, TradeIcons.named(Items.ENDER_CHEST, "Ender chest", tab == SourceTab.ENDER));
		display.setItem(TradeLayout.TAB_SHULKER, TradeIcons.named(Items.SHULKER_BOX, "Shulkers", tab == SourceTab.PICKER || tab == SourceTab.CONTENTS));
		display.setItem(TradeLayout.NEXT_PAGE, TradeIcons.named(Items.ARROW, "Next page"));
		display.setItem(50, TradeIcons.named(Items.STAINED_GLASS_PANE.black(), " "));
		display.setItem(51, TradeIcons.named(Items.STAINED_GLASS_PANE.black(), " "));
		display.setItem(TradeLayout.CANCEL, TradeIcons.named(Items.BARRIER, "Cancel"));
		display.setItem(TradeLayout.READY, readyButton(session, selfReady));
	}

	private static void paintOffer(Container display, int start, ItemEscrow escrow) {
		for (int index = 0; index < TradeLayout.OFFER_SLOTS; index++) {
			if (index < escrow.size()) {
				display.setItem(start + index, escrow.view(index).copy());
			} else {
				display.setItem(start + index, TradeIcons.named(Items.STAINED_GLASS_PANE.lightGray(), "Empty offer slot"));
			}
		}
	}

	private static ItemStack readyButton(TradeSession session, boolean selfReady) {
		String name;
		if (session.state() == TradeState.COUNTDOWN) {
			name = "Ready " + session.displaySeconds();
		} else if (selfReady) {
			name = "Ready";
		} else {
			name = "Not ready";
		}
		return TradeIcons.named(selfReady || session.state() == TradeState.COUNTDOWN ? Items.DYE.lime() : Items.DYE.gray(), name);
	}
}
