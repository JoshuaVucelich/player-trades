package com.vucelich.playertrades.fabric;

import com.vucelich.playertrades.PlayerTrades;
import net.fabricmc.api.DedicatedServerModInitializer;

public final class PlayerTradesFabric implements DedicatedServerModInitializer {
	@Override
	public void onInitializeServer() {
		PlayerTrades.init();
		TradeEvents.register();
	}
}
