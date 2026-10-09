package com.vucelich.playertrades.fabric;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.vucelich.playertrades.PlayerTrades;
import com.vucelich.playertrades.TradeLimits;
import com.vucelich.playertrades.escrow.ItemEscrow;
import com.vucelich.playertrades.escrow.ItemMover;
import com.vucelich.playertrades.gui.TradeActions;
import com.vucelich.playertrades.gui.TradeLayout;
import com.vucelich.playertrades.gui.TradeMenu;
import com.vucelich.playertrades.gui.TradePainter;
import com.vucelich.playertrades.session.AcceptProblem;
import com.vucelich.playertrades.session.ArmResult;
import com.vucelich.playertrades.session.OfferProblem;
import com.vucelich.playertrades.session.Side;
import com.vucelich.playertrades.session.TradeDirectory;
import com.vucelich.playertrades.session.TradeSession;
import com.vucelich.playertrades.swap.CommitOutcome;
import com.vucelich.playertrades.swap.IncomingStack;
import com.vucelich.playertrades.swap.PlayerStorage;
import com.vucelich.playertrades.swap.SwapCommit;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

public final class TradeRuntime implements TradeActions {
	private final MinecraftServer server;
	private final TradeDirectory directory = new TradeDirectory();
	private final Map<UUID, ItemEscrow> escrowA = new HashMap<>();
	private final Map<UUID, ItemEscrow> escrowB = new HashMap<>();
	private final Set<UUID> closed = new HashSet<>();

	public TradeRuntime(MinecraftServer server) {
		this.server = server;
	}

	public int postOffer(ServerPlayer player, String text) {
		long now = System.currentTimeMillis();
		TradeDirectory.PostResult result = directory.postOffer(player.getUUID(), player.getScoreboardName(), text, now);
		if (!result.ok()) {
			if (result.problem() == OfferProblem.IN_SESSION) {
				tell(player, "Finish or cancel your current trade before posting another offer.");
			}
			return 0;
		}
		broadcastOffer(result.session(), result.replaced());
		return 1;
	}

	public int accept(ServerPlayer acceptor, String name) {
		ServerPlayer offerer = findOnline(name);
		if (offerer == null) {
			tell(acceptor, "No player named " + name + " is online. Bedrock names include the Floodgate prefix.");
			return 0;
		}
		TradeDirectory.AcceptResult result = directory.accept(
				acceptor.getUUID(),
				acceptor.getScoreboardName(),
				offerer.getUUID(),
				System.currentTimeMillis());
		if (!result.ok()) {
			tell(acceptor, acceptMessage(result.problem(), offerer.getScoreboardName()));
			return 0;
		}
		TradeSession session = result.session();
		escrowA.put(session.id(), new ItemEscrow());
		escrowB.put(session.id(), new ItemEscrow());
		openMenu(offerer, session);
		openMenu(acceptor, session);
		refresh(offerer, true);
		refresh(acceptor, true);
		tell(offerer, acceptor.getScoreboardName() + " accepted your trade.");
		tell(acceptor, "Trade opened with " + offerer.getScoreboardName() + ".");
		return 1;
	}

	public int cancel(ServerPlayer player) {
		TradeSession offer = directory.offerOf(player.getUUID()).orElse(null);
		if (offer != null) {
			directory.withdrawOffer(player.getUUID());
			tell(player, "Your offer was withdrawn.");
			return 1;
		}
		TradeSession session = directory.tradeOf(player.getUUID()).orElse(null);
		if (session == null || !session.state().menuOpen()) {
			tell(player, "You do not have a trade to cancel.");
			return 0;
		}
		endSession(session, "player", null, false);
		return 1;
	}

	public int reopen(ServerPlayer player) {
		TradeSession session = directory.tradeOf(player.getUUID()).orElse(null);
		if (session == null || !session.state().menuOpen()) {
			if (directory.offerOf(player.getUUID()).isPresent()) {
				tell(player, "Your offer is still up. Someone else accepts it with /trade accept " + player.getScoreboardName() + ".");
			} else {
				tell(player, "You are not in a trade.");
			}
			return 0;
		}
		openMenu(player, session);
		refresh(player, true);
		return 1;
	}

	public void tick() {
		for (TradeSession session : directory.trades()) {
			if (session.state() != com.vucelich.playertrades.session.TradeState.COUNTDOWN) {
				continue;
			}
			int before = session.displaySeconds();
			boolean commitNow;
			try {
				commitNow = session.tickCountdown();
			} catch (RuntimeException failure) {
				PlayerTrades.LOGGER.error("Countdown tick failed for {}", session.id(), failure);
				endSession(session, "rollback", null, false);
				continue;
			}
			if (commitNow) {
				commit(session);
			} else if (session.displaySeconds() != before) {
				refreshBoth(session, true);
			}
		}
	}

	public void onDisconnect(ServerPlayer player) {
		directory.withdrawOffer(player.getUUID());
		directory.tradeOf(player.getUUID()).ifPresent(session -> endSession(session, "disconnect", player, false));
	}

	public void onDeath(ServerPlayer player) {
		directory.withdrawOffer(player.getUUID());
		directory.tradeOf(player.getUUID()).ifPresent(session -> endSession(session, "death", player, true));
	}

	public void onDimensionChange(ServerPlayer player) {
		directory.tradeOf(player.getUUID()).ifPresent(session -> endSession(session, "dimension", null, false));
	}

	public void shutdown() {
		for (TradeSession session : new ArrayList<>(directory.trades())) {
			endSession(session, "shutdown", null, false);
		}
		for (TradeSession offer : directory.offers()) {
			directory.withdrawOffer(offer.playerA());
		}
	}

	@Override
	public void opened(TradeMenu menu) {
		ServerPlayer viewer = server.getPlayerList().getPlayer(menu.viewerId());
		TradeSession session = directory.tradeOf(menu.viewerId()).orElse(null);
		if (viewer == null || session == null) {
			return;
		}
		paint(menu, viewer, session, false);
	}

	@Override
	public void click(TradeMenu menu, ServerPlayer player, int slotId, ContainerInput input) {
		TradeSession session = directory.tradeOf(player.getUUID()).orElse(null);
		if (session == null || !session.state().menuOpen()) {
			return;
		}
		if (slotId == TradeLayout.CANCEL) {
			endSession(session, "player", null, false);
			return;
		}
		if (slotId == TradeLayout.READY) {
			arm(player, session);
			return;
		}
		if (slotId == TradeLayout.PREV_PAGE) {
			menu.setPage(menu.page() - 1);
			refresh(player, true);
			return;
		}
		if (slotId == TradeLayout.NEXT_PAGE) {
			menu.setPage(menu.page() + 1);
			refresh(player, true);
			return;
		}
		if (slotId == TradeLayout.TAB_ENDER) {
			tell(player, "Ender chest selection arrives in a later update.");
			return;
		}
		if (slotId == TradeLayout.TAB_SHULKER) {
			tell(player, "Shulker selection arrives in a later update.");
			return;
		}
		if (slotId >= TradeLayout.OWN_OFFER && slotId < TradeLayout.OWN_OFFER + TradeLayout.OFFER_SLOTS) {
			returnOfferStack(player, session, slotId - TradeLayout.OWN_OFFER);
			return;
		}
		int inventoryIndex = TradeLayout.inventoryIndex(slotId, menu.page());
		if (inventoryIndex >= 0) {
			takeFromInventory(player, session, inventoryIndex);
		}
	}

	@Override
	public void closed(TradeMenu menu, ServerPlayer player) {
		TradeSession session = directory.tradeOf(player.getUUID()).orElse(null);
		if (session == null || !session.state().menuOpen()) {
			return;
		}
		session.clearReadyFromClose();
		refreshBoth(session, true);
		tell(player, "Trade still open. /trade reopens this screen. /trade cancel returns your items.");
	}

	@Override
	public boolean stillOpen(TradeMenu menu) {
		return directory.tradeOf(menu.viewerId()).map(session -> session.state().menuOpen()).orElse(false);
	}

	private void arm(ServerPlayer player, TradeSession session) {
		int countA = escrow(session, Side.A).size();
		int countB = escrow(session, Side.B).size();
		ArmResult result = session.arm(session.sideOf(player.getUUID()), countA, countB);
		if (result == ArmResult.EMPTY) {
			tell(player, "Add at least one stack before you ready up.");
			return;
		}
		refreshBoth(session, true);
	}

	private void takeFromInventory(ServerPlayer player, TradeSession session, int inventoryIndex) {
		ItemEscrow escrow = escrow(session, session.sideOf(player.getUUID()));
		if (escrow.size() >= TradeLimits.MAX_OFFER_STACKS) {
			tell(player, "You can offer up to 8 stacks.");
			return;
		}
		ItemStack stack = player.getInventory().getItem(inventoryIndex);
		if (stack.isEmpty()) {
			return;
		}
		ItemStack taken = stack.copy();
		player.getInventory().setItem(inventoryIndex, ItemStack.EMPTY);
		escrow.add(taken, inventoryIndex);
		session.onEscrowEdited();
		refreshBoth(session, true);
	}

	private void returnOfferStack(ServerPlayer player, TradeSession session, int index) {
		ItemEscrow escrow = escrow(session, session.sideOf(player.getUUID()));
		if (index >= escrow.size()) {
			return;
		}
		ItemStack stack = escrow.view(index);
		if (!ItemMover.fitsInStorage(player.getInventory(), stack)) {
			tell(player, "Your inventory is full, so that stack stays in the offer.");
			return;
		}
		ItemEscrow.Entry entry = escrow.remove(index);
		int origin = entry.origin();
		if (origin >= 0 && origin < TradeLimits.STORAGE_SLOTS && player.getInventory().getItem(origin).isEmpty()) {
			player.getInventory().setItem(origin, entry.stack());
		} else {
			ItemStack leftover = ItemMover.insertStorage(player.getInventory(), entry.stack());
			if (!leftover.isEmpty()) {
				escrow.add(leftover, origin);
				tell(player, "Your inventory is full, so that stack stays in the offer.");
				refreshBoth(session, true);
				return;
			}
		}
		session.onEscrowEdited();
		refreshBoth(session, true);
	}

	private void commit(TradeSession session) {
		ServerPlayer left = server.getPlayerList().getPlayer(session.playerA());
		ServerPlayer right = server.getPlayerList().getPlayer(session.playerB());
		if (left == null || right == null) {
			endSession(session, "disconnect", null, false);
			return;
		}
		if (!left.isAlive() || !right.isAlive()) {
			endSession(session, "death", left.isAlive() ? right : left, true);
			return;
		}
		ItemEscrow leftEscrow = escrow(session, Side.A);
		ItemEscrow rightEscrow = escrow(session, Side.B);
		if (leftEscrow.isEmpty() || rightEscrow.isEmpty()) {
			session.rejectNoSpace();
			tell(left, "The trade needs items from both players.");
			tell(right, "The trade needs items from both players.");
			refreshBoth(session, true);
			return;
		}
		List<ItemEscrow.Entry> savedLeft = leftEscrow.copyEntries();
		List<ItemEscrow.Entry> savedRight = rightEscrow.copyEntries();
		List<IncomingStack> leftGives = incoming(leftEscrow);
		List<IncomingStack> rightGives = incoming(rightEscrow);
		boolean swapped = false;
		try {
			CommitOutcome outcome = SwapCommit.commit(
					PlayerStorage.live(left.getInventory()),
					PlayerStorage.live(right.getInventory()),
					leftGives,
					rightGives);
			switch (outcome.kind()) {
				case SUCCESS -> {
					swapped = true;
					leftEscrow.clear();
					rightEscrow.clear();
					session.complete();
					finish(session);
					PlayerTrades.LOGGER.info("Trade {} completed between {} and {}", session.id(), session.nameA(), session.nameB());
				}
				case NO_SPACE -> {
					session.rejectNoSpace();
					String who = shortNames(session, outcome);
					tell(left, "Trade paused. " + who + " does not have enough inventory space.");
					tell(right, "Trade paused. " + who + " does not have enough inventory space.");
					refreshBoth(session, true);
				}
				case ROLLED_BACK -> {
					leftEscrow.restore(savedLeft);
					rightEscrow.restore(savedRight);
					PlayerTrades.LOGGER.error("Trade {} rolled back", session.id(), outcome.failure());
					endSession(session, "rollback", null, false);
				}
			}
		} catch (RuntimeException failure) {
			if (swapped) {
				PlayerTrades.LOGGER.error("Trade {} moved items, then bookkeeping failed", session.id(), failure);
				leftEscrow.clear();
				rightEscrow.clear();
				if (session.state() == com.vucelich.playertrades.session.TradeState.COMMITTING) {
					session.complete();
				}
				finish(session);
				return;
			}
			PlayerTrades.LOGGER.error("Trade {} failed before the swap finished", session.id(), failure);
			if (session.state() == com.vucelich.playertrades.session.TradeState.COMMITTING) {
				leftEscrow.restore(savedLeft);
				rightEscrow.restore(savedRight);
				endSession(session, "rollback", null, false);
			}
		}
	}

	private void endSession(TradeSession session, String reason, ServerPlayer deadOrGone, boolean deathDrop) {
		if (!closed.add(session.id())) {
			return;
		}
		if (!session.state().isTerminal()) {
			session.cancel(reason);
		}
		directory.unlink(session);
		ServerPlayer left = server.getPlayerList().getPlayer(session.playerA());
		ServerPlayer right = session.playerB() == null ? null : server.getPlayerList().getPlayer(session.playerB());
		returnEscrow(session, Side.A, left, deadOrGone, deathDrop);
		returnEscrow(session, Side.B, right, deadOrGone, deathDrop);
		closeMenu(left);
		closeMenu(right);
		String message = cancelMessage(reason);
		if (left != null && left != deadOrGone) {
			tell(left, message);
		}
		if (right != null && right != deadOrGone) {
			tell(right, message);
		}
		escrowA.remove(session.id());
		escrowB.remove(session.id());
		PlayerTrades.LOGGER.info("Trade {} cancelled ({}) between {} and {}", session.id(), reason, session.nameA(), session.nameB());
	}

	private void finish(TradeSession session) {
		if (!closed.add(session.id())) {
			return;
		}
		directory.unlink(session);
		ServerPlayer left = server.getPlayerList().getPlayer(session.playerA());
		ServerPlayer right = server.getPlayerList().getPlayer(session.playerB());
		closeMenu(left);
		closeMenu(right);
		tell(left, "Trade complete.");
		tell(right, "Trade complete.");
		escrowA.remove(session.id());
		escrowB.remove(session.id());
	}

	private void returnEscrow(TradeSession session, Side side, ServerPlayer owner, ServerPlayer deadOrGone, boolean deathDrop) {
		ItemEscrow escrow = escrow(session, side);
		if (escrow.isEmpty()) {
			return;
		}
		UUID ownerId = side == Side.A ? session.playerA() : session.playerB();
		if (owner == null && deadOrGone != null && ownerId != null && ownerId.equals(deadOrGone.getUUID())) {
			owner = deadOrGone;
		}
		boolean dropOnly = (deathDrop && owner == deadOrGone) || (owner != null && !owner.isAlive());
		if (owner == null) {
			ServerPlayer anchor = server.getPlayerList().getPlayer(session.playerA());
			if (anchor == null && session.playerB() != null) {
				anchor = server.getPlayerList().getPlayer(session.playerB());
			}
			if (anchor != null) {
				for (ItemEscrow.Entry entry : escrow.drain()) {
					ItemMover.drop(anchor, entry.stack());
				}
				return;
			}
			var level = server.overworld();
			if (level == null) {
				PlayerTrades.LOGGER.error("No world left to drop {} escrow in trade {}", side, session.id());
				return;
			}
			var pos = level.getRespawnData().pos();
			for (ItemEscrow.Entry entry : escrow.drain()) {
				ItemMover.dropAt(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, entry.stack());
			}
			return;
		}
		ItemMover.giveBack(owner, escrow, dropOnly);
	}

	private static List<IncomingStack> incoming(ItemEscrow escrow) {
		List<IncomingStack> stacks = new ArrayList<>();
		for (int index = 0; index < escrow.size(); index++) {
			stacks.add(PlayerStorage.incoming(escrow.view(index)));
		}
		return stacks;
	}

	private static String shortNames(TradeSession session, CommitOutcome outcome) {
		List<String> names = new ArrayList<>();
		if (outcome.leftShort()) {
			names.add(session.nameA());
		}
		if (outcome.rightShort()) {
			names.add(session.nameB());
		}
		return String.join(" and ", names);
	}

	private void broadcastOffer(TradeSession offer, boolean replaced) {
		String name = offer.nameA();
		String text = offer.offerText().isBlank() ? "open offer" : offer.offerText();
		String prefix = replaced ? "Updated offer: " : "";
		MutableComponent line = Component.literal(prefix + name + " is trading: " + text + ". /trade accept " + name + " ");
		line.append(Component.literal("[Accept]")
				.withStyle(style -> style.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.RunCommand("/trade accept " + name))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Accept " + name + "'s trade")))));
		server.getPlayerList().broadcastSystemMessage(line, false);
	}

	private void openMenu(ServerPlayer player, TradeSession session) {
		String title = "Trade with " + session.otherName(player.getUUID());
		player.openMenu(new SimpleMenuProvider(
				(syncId, inventory, ignored) -> new TradeMenu(syncId, inventory, player.getUUID(), this),
				Component.literal(title)));
	}

	private void refreshBoth(TradeSession session, boolean broadcast) {
		refresh(server.getPlayerList().getPlayer(session.playerA()), broadcast);
		if (session.playerB() != null) {
			refresh(server.getPlayerList().getPlayer(session.playerB()), broadcast);
		}
	}

	private void refresh(ServerPlayer player, boolean broadcast) {
		if (player == null || !(player.containerMenu instanceof TradeMenu menu)) {
			return;
		}
		TradeSession session = directory.tradeOf(player.getUUID()).orElse(null);
		if (session == null) {
			return;
		}
		paint(menu, player, session, broadcast);
	}

	private void paint(TradeMenu menu, ServerPlayer viewer, TradeSession session, boolean broadcast) {
		Side side = session.sideOf(viewer.getUUID());
		Side other = side == Side.A ? Side.B : Side.A;
		TradePainter.paint(menu, viewer, session, escrow(session, side), escrow(session, other), session.ready(side), session.ready(other));
		if (broadcast) {
			menu.broadcastChanges();
		}
	}

	private ItemEscrow escrow(TradeSession session, Side side) {
		Map<UUID, ItemEscrow> map = side == Side.A ? escrowA : escrowB;
		return map.computeIfAbsent(session.id(), ignored -> new ItemEscrow());
	}

	private void closeMenu(ServerPlayer player) {
		if (player != null && player.containerMenu instanceof TradeMenu) {
			player.closeContainer();
		}
	}

	private ServerPlayer findOnline(String name) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.getScoreboardName().equals(name)) {
				return player;
			}
		}
		return null;
	}

	private static void tell(ServerPlayer player, String message) {
		if (player != null) {
			player.sendSystemMessage(Component.literal(message));
		}
	}

	private static String acceptMessage(AcceptProblem problem, String name) {
		return switch (problem) {
			case SELF -> "You cannot accept your own offer.";
			case MISSING -> name + " does not have an open trade offer.";
			case EXPIRED -> name + "'s offer has expired.";
			case IN_SESSION -> "One of you is already in a trade.";
		};
	}

	private static String cancelMessage(String reason) {
		return switch (reason) {
			case "disconnect" -> "The trade was cancelled because someone left. Your items were returned.";
			case "death" -> "The trade was cancelled because someone died. Items from the player who died were dropped.";
			case "dimension" -> "The trade was cancelled because someone changed dimensions. Your items were returned.";
			case "rollback" -> "The trade was cancelled because the swap could not finish safely. Your items were returned.";
			case "shutdown" -> "The trade was cancelled because the server is stopping. Your items were returned.";
			default -> "Trade cancelled. Your items were returned.";
		};
	}
}
