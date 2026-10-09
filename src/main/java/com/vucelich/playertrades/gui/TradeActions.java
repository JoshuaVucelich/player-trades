package com.vucelich.playertrades.gui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;

public interface TradeActions {
	void opened(TradeMenu menu);

	void click(TradeMenu menu, ServerPlayer player, int slotId, ContainerInput input);

	void closed(TradeMenu menu, ServerPlayer player);

	boolean stillOpen(TradeMenu menu);
}
