package com.vucelich.playertrades.gui;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class TradeIcons {
	private TradeIcons() {}

	public static ItemStack named(Item item, String name) {
		return named(item, name, false);
	}

	public static ItemStack named(Item item, String name, boolean glint) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("PlayerTrades", true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		if (glint) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return stack;
	}
}
