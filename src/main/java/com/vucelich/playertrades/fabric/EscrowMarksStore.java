package com.vucelich.playertrades.fabric;

import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.vucelich.playertrades.PlayerTrades;
import com.vucelich.playertrades.escrow.EscrowMarks;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * The journal sequence lives in the player file, next to the inventory, so a replay can see
 * whether that save already applied the line.
 */
public final class EscrowMarksStore {
	private static final Codec<EscrowMarks> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC_SET.optionalFieldOf("escrowed", Set.<UUID>of()).forGetter(EscrowMarks::escrowed),
			UUIDUtil.CODEC_SET.optionalFieldOf("returned", Set.<UUID>of()).forGetter(EscrowMarks::returned))
			.apply(instance, EscrowMarks::new));

	private static AttachmentType<EscrowMarks> type;

	private EscrowMarksStore() {}

	public static void register() {
		if (type != null) {
			return;
		}
		type = AttachmentRegistry.create(
				Identifier.fromNamespaceAndPath(PlayerTrades.MOD_ID, "escrow_marks"),
				builder -> builder.persistent(CODEC).copyOnDeath());
	}

	public static EscrowMarks read(ServerPlayer player) {
		if (player == null || type == null) {
			return EscrowMarks.empty();
		}
		EscrowMarks marks = ((AttachmentTarget) player).getAttached(type);
		return marks == null ? EscrowMarks.empty() : marks;
	}

	public static void write(ServerPlayer player, EscrowMarks marks) {
		if (player == null || type == null) {
			return;
		}
		((AttachmentTarget) player).setAttached(type, marks == null ? EscrowMarks.empty() : marks);
	}
}
