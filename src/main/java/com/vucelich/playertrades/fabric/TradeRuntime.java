package com.vucelich.playertrades.fabric;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.vucelich.playertrades.PlayerTrades;
import com.vucelich.playertrades.TradeLimits;
import com.vucelich.playertrades.config.DiscordSettings;
import com.vucelich.playertrades.config.TradeConfig;
import com.vucelich.playertrades.discord.DiscordBridge;
import com.vucelich.playertrades.discord.DiscordHost;
import com.vucelich.playertrades.discord.NoOpDiscordBridge;
import com.vucelich.playertrades.discord.OfferNotice;
import com.vucelich.playertrades.discord.RelayPolicy;
import com.vucelich.playertrades.escrow.EscrowJournal;
import com.vucelich.playertrades.escrow.EscrowReplay;
import com.vucelich.playertrades.escrow.EscrowTake;
import com.vucelich.playertrades.escrow.ItemEscrow;
import com.vucelich.playertrades.escrow.ItemMover;
import com.vucelich.playertrades.escrow.ItemOrigin;
import com.vucelich.playertrades.escrow.ShulkerBoxes;
import com.vucelich.playertrades.gui.TradeActions;
import com.vucelich.playertrades.gui.TradeLayout;
import com.vucelich.playertrades.gui.TradeMenu;
import com.vucelich.playertrades.gui.TradePainter;
import com.vucelich.playertrades.gui.TradeSources;
import com.vucelich.playertrades.log.TradeLog;
import com.vucelich.playertrades.perm.PermissionChecker;
import com.vucelich.playertrades.session.AcceptProblem;
import com.vucelich.playertrades.session.ArmResult;
import com.vucelich.playertrades.session.BlacklistWalk;
import com.vucelich.playertrades.session.Cooldowns;
import com.vucelich.playertrades.session.ItemBlacklist;
import com.vucelich.playertrades.session.OfferProblem;
import com.vucelich.playertrades.session.ProximityRules;
import com.vucelich.playertrades.session.Side;
import com.vucelich.playertrades.session.TradeDirectory;
import com.vucelich.playertrades.session.TradeSession;
import com.vucelich.playertrades.swap.CommitOutcome;
import com.vucelich.playertrades.swap.IncomingStack;
import com.vucelich.playertrades.swap.PlayerStorage;
import com.vucelich.playertrades.swap.SwapCommit;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;

public final class TradeRuntime implements TradeActions {
	private final MinecraftServer server;
	private final TradeDirectory directory = new TradeDirectory();
	private final Map<UUID, ItemEscrow> escrowA = new HashMap<>();
	private final Map<UUID, ItemEscrow> escrowB = new HashMap<>();
	private final Set<UUID> closed = new HashSet<>();
	private final Cooldowns cooldowns = new Cooldowns();
	private final Map<UUID, Long> proximityNotices = new HashMap<>();
	private TradeConfig config = TradeConfig.defaults();
	private ItemBlacklist blacklist = new ItemBlacklist(List.of());
	private PermissionChecker permissions = PermissionChecker.TRADE_ONLY;
	private Path tradeLog;
	private final Set<UUID> logged = new HashSet<>();
	private final Set<UUID> expiredNotices = new HashSet<>();
	private DiscordBridge bridge = new NoOpDiscordBridge();
	private DiscordHost discordHost = settings -> new NoOpDiscordBridge();
	private final Path journalDir;
	private final EscrowReplay replay = new EscrowReplay();

	public TradeRuntime(MinecraftServer server) {
		this(server, TradeConfig.defaults());
	}

	public TradeRuntime(MinecraftServer server, TradeConfig config) {
		this.server = server;
		this.journalDir = server.getWorldPath(LevelResource.DATA).resolve("player_trades").resolve("escrow");
		applyConfig(config);
	}

	public TradeConfig config() {
		return config;
	}

	public void setPermissions(PermissionChecker permissions) {
		this.permissions = permissions == null ? PermissionChecker.TRADE_ONLY : permissions;
	}

	public void setTradeLog(Path tradeLog) {
		this.tradeLog = tradeLog;
	}

	public void bindDiscord(DiscordHost host) {
		this.discordHost = host == null ? settings -> new NoOpDiscordBridge() : host;
		replaceBridge();
	}

	public void execute(Runnable task) {
		if (task != null) {
			server.executeIfPossible(task);
		}
	}

	public void deliverDiscord(UUID sessionId, String speaker, String text) {
		server.executeIfPossible(() -> deliverOnServer(sessionId, speaker, text));
	}

	public void relayChat(ServerPlayer player, String text) {
		if (player == null) {
			return;
		}
		TradeSession session = directory.tradeOf(player.getUUID()).orElse(null);
		boolean negotiating = session != null && session.state().menuOpen();
		if (!RelayPolicy.fromGame(config.discord().relayNegotiation(), negotiating, session != null, text)) {
			return;
		}
		try {
			bridge.openThread(session.id(), session.playerA(), session.nameA(), session.nameB());
			bridge.relayFromGame(session.id(), player.getScoreboardName(), text);
		} catch (RuntimeException failure) {
			PlayerTrades.LOGGER.warn("Discord relay failed");
		}
	}

	public boolean permits(ServerPlayer player, String node) {
		return player != null && permissions.allows(player.getUUID(), node);
	}

	public String reload() {
		TradeConfig.ConfigLoad load = FabricConfigs.reload(config);
		if (!load.replaced()) {
			return "Config was not changed. " + load.error();
		}
		DiscordSettings before = config.discord();
		applyConfig(load.config());
		if (discordChanged(before, config.discord())) {
			replaceBridge();
		}
		return "Config reloaded.";
	}

	public int adminCancel(ServerPlayer actor, String name) {
		ServerPlayer target = findOnline(name);
		if (target == null) {
			tell(actor, "No player named " + name + " is online.");
			return 0;
		}
		TradeSession session = directory.tradeOf(target.getUUID()).orElse(null);
		if (session == null || session.playerB() == null) {
			tell(actor, target.getScoreboardName() + " is not in a trade.");
			return 0;
		}
		endSession(session, "admin", null, false);
		tell(actor, "Cancelled the trade for " + target.getScoreboardName() + ".");
		return 1;
	}

	public List<String> logPage(String playerFilter, int page) {
		List<TradeLog.Entry> rows = new ArrayList<>();
		try {
			for (TradeLog.Entry entry : TradeLog.read(tradeLog)) {
				if (entry.involves(playerFilter)) {
					rows.add(entry);
				}
			}
		} catch (IOException failure) {
			PlayerTrades.LOGGER.error("Could not read the trade log");
			return List.of("The trade log could not be read.");
		}
		java.util.Collections.reverse(rows);
		int pageSize = 8;
		int pages = Math.max(1, (rows.size() + pageSize - 1) / pageSize);
		int safePage = Math.max(1, page);
		if (rows.isEmpty()) {
			return List.of("No trades are logged" + (playerFilter == null || playerFilter.isBlank() ? "." : " for " + playerFilter + "."));
		}
		if (safePage > pages) {
			return List.of("There is no page " + safePage + ". The log has " + pages + ".");
		}
		List<String> lines = new ArrayList<>();
		lines.add("Trades, page " + safePage + " of " + pages + ".");
		int from = (safePage - 1) * pageSize;
		for (TradeLog.Entry entry : rows.subList(from, Math.min(rows.size(), from + pageSize))) {
			String names = entry.players().stream().map(TradeLog.Person::name).reduce((a, b) -> a + " and " + b).orElse("unknown");
			lines.add(entry.time() + " " + entry.id() + " " + names + " " + entry.result() + (entry.reason().isBlank() ? "" : " (" + entry.reason() + ")"));
		}
		return lines;
	}

	public List<String> logShow(String id) {
		try {
			for (TradeLog.Entry entry : TradeLog.read(tradeLog)) {
				if (!entry.id().equals(id)) {
					continue;
				}
				List<String> lines = new ArrayList<>();
				lines.add(entry.time() + " " + entry.result() + (entry.reason().isBlank() ? "" : " (" + entry.reason() + ")"));
				for (int side = 0; side < entry.offers().size(); side++) {
					String name = side < entry.players().size() ? entry.players().get(side).name() : "side " + (side + 1);
					List<TradeLog.Stack> stacks = entry.offers().get(side);
					if (stacks.isEmpty()) {
						lines.add(name + ": nothing");
						continue;
					}
					for (TradeLog.Stack stack : stacks) {
						String custom = stack.customName().isBlank() ? "" : " \"" + stack.customName() + "\"";
						lines.add(name + ": " + stack.count() + " " + stack.id() + custom);
					}
				}
				return lines;
			}
		} catch (IOException failure) {
			PlayerTrades.LOGGER.error("Could not read the trade log");
			return List.of("The trade log could not be read.");
		}
		return List.of("No trade with that id.");
	}

	public void applyConfig(TradeConfig config) {
		this.config = config == null ? TradeConfig.defaults() : config;
		this.blacklist = resolve(this.config.blacklist());
		abortBlacklisted();
	}

	public int postOffer(ServerPlayer player, String text) {
		if (!permit(player, PermissionChecker.TRADE) || cooling(player)) {
			return 0;
		}
		long now = System.currentTimeMillis();
		TradeDirectory.PostResult result = directory.postOffer(player.getUUID(), player.getScoreboardName(), text, now, config.offerTtlMillis());
		if (!result.ok()) {
			if (result.problem() == OfferProblem.IN_SESSION) {
				tell(player, "Finish or cancel your current trade before posting another offer.");
			}
			return 0;
		}
		broadcastOffer(result.session(), result.replaced());
		postToDiscord(player.getUUID(), player.getScoreboardName(), text);
		return 1;
	}

	public int accept(ServerPlayer acceptor, String name) {
		if (!permit(acceptor, PermissionChecker.TRADE) || cooling(acceptor)) {
			return 0;
		}
		ServerPlayer offerer = findOnline(name);
		if (offerer == null) {
			tell(acceptor, "No player named " + name + " is online. Bedrock names include the Floodgate prefix.");
			return 0;
		}
		if (cooling(offerer)) {
			tell(acceptor, offerer.getScoreboardName() + " cannot trade again yet.");
			return 0;
		}
		ProximityRules.Problem near = proximity(offerer, acceptor);
		if (near != ProximityRules.Problem.OK) {
			tell(acceptor, proximityText(near));
			tell(offerer, proximityText(near));
			return 0;
		}
		TradeDirectory.AcceptResult result = directory.accept(
				acceptor.getUUID(),
				acceptor.getScoreboardName(),
				offerer.getUUID(),
				System.currentTimeMillis());
		if (!result.ok()) {
			if (result.problem() == AcceptProblem.EXPIRED) {
				notice(offerer.getUUID(), offerer.getScoreboardName(), OfferNotice.EXPIRED);
			}
			tell(acceptor, acceptMessage(result.problem(), offerer.getScoreboardName()));
			return 0;
		}
		TradeSession session = result.session();
		notice(offerer.getUUID(), offerer.getScoreboardName(), OfferNotice.ACCEPTED);
		if (config.discord().relayNegotiation()) {
			try {
				bridge.openThread(session.id(), offerer.getUUID(), session.nameA(), session.nameB());
			} catch (RuntimeException failure) {
				PlayerTrades.LOGGER.warn("Discord thread was not opened");
			}
		}
		session.applyRules(config.countdownTicks(), config.allowEmptyTrades());
		session.touch(System.currentTimeMillis());
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
			withdrawAdvertisement(player.getUUID(), OfferNotice.CANCELLED);
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
		long now = System.currentTimeMillis();
		expireOffers(now);
		for (TradeSession session : directory.trades()) {
			if (session.idle(now, config.sessionTimeoutMillis())) {
				endSession(session, "timeout", null, false);
				continue;
			}
			if (session.state() != com.vucelich.playertrades.session.TradeState.COUNTDOWN) {
				continue;
			}
			if (session.countdownTicks() % 20 == 0) {
				ProximityRules.Problem near = proximity(session);
				if (near != ProximityRules.Problem.OK) {
					noteProximity(session, near);
					continue;
				}
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
		withdrawAdvertisement(player.getUUID(), OfferNotice.CANCELLED);
		directory.tradeOf(player.getUUID()).ifPresent(session -> endSession(session, "disconnect", player, false));
	}

	public void onDeath(ServerPlayer player) {
		withdrawAdvertisement(player.getUUID(), OfferNotice.CANCELLED);
		directory.tradeOf(player.getUUID()).ifPresent(session -> endSession(session, "death", player, true));
	}

	public void replayOnline() {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			onJoin(player);
		}
	}

	public void onJoin(ServerPlayer player) {
		if (player == null) {
			return;
		}
		List<EscrowJournal.Page> pages;
		try {
			pages = EscrowJournal.readAll(journalDir);
		} catch (IOException failure) {
			PlayerTrades.LOGGER.error("Could not read escrow journals for {}", player.getUUID(), failure);
			return;
		}
		boolean returned = false;
		for (EscrowJournal.Page page : pages) {
			if (page.sessionId() == null || liveEscrow(page.sessionId()) || replay.alreadyGiven(page.sessionId(), player.getUUID())) {
				continue;
			}
			List<EscrowJournal.Side> sides = sidesFor(page, player.getUUID());
			if (sides.isEmpty()) {
				continue;
			}
			try {
				for (EscrowJournal.Side side : sides) {
					ItemMover.giveBack(player, EscrowCodecs.decode(server, side), false);
				}
			} catch (RuntimeException failure) {
				PlayerTrades.LOGGER.error("Could not return escrowed items from trade {} to {}", page.sessionId(), player.getUUID(), failure);
				continue;
			}
			replay.claim(page, Set.of(player.getUUID()));
			try {
				EscrowJournal.write(journalDir, EscrowReplay.without(page, Set.of(player.getUUID())));
			} catch (IOException failure) {
				PlayerTrades.LOGGER.error("Returned escrow for {} in trade {} but the journal was not updated", player.getUUID(), page.sessionId(), failure);
			}
			returned = true;
		}
		if (returned) {
			tell(player, "Items from an unfinished trade were returned.");
		}
	}

	public void onDimensionChange(ServerPlayer player) {
		directory.tradeOf(player.getUUID()).ifPresent(session -> endSession(session, "dimension", null, false));
	}

	public void shutdown() {
		for (TradeSession session : new ArrayList<>(directory.trades())) {
			endSession(session, "shutdown", null, false);
		}
		long now = System.currentTimeMillis();
		for (TradeSession offer : new ArrayList<>(directory.offers())) {
			OfferNotice notice = offer.expired(now) ? OfferNotice.EXPIRED : OfferNotice.CANCELLED;
			withdrawAdvertisement(offer.playerA(), notice);
		}
		bridge.shutdown();
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
		if (slotId == TradeLayout.TAB_INVENTORY) {
			menu.showInventory();
			refresh(player, true);
			return;
		}
		if (slotId == TradeLayout.TAB_ENDER) {
			menu.showEnder();
			refresh(player, true);
			return;
		}
		if (slotId == TradeLayout.TAB_SHULKER) {
			menu.showPicker();
			refresh(player, true);
			return;
		}
		if (slotId >= TradeLayout.OWN_OFFER && slotId < TradeLayout.OWN_OFFER + TradeLayout.OFFER_SLOTS) {
			returnOfferStack(player, session, slotId - TradeLayout.OWN_OFFER);
			return;
		}
		if (slotId >= TradeLayout.PLAYER_START) {
			int inventoryIndex = TradeLayout.inventoryIndex(slotId, menu.page());
			if (inventoryIndex >= 0) {
				takeFromInventory(player, session, inventoryIndex);
			}
			return;
		}
		if (slotId >= TradeLayout.SOURCE_START && slotId < TradeLayout.SOURCE_START + TradeLayout.SOURCE_SLOTS) {
			clickSource(menu, player, session, slotId - TradeLayout.SOURCE_START);
		}
	}

	@Override
	public void closed(TradeMenu menu, ServerPlayer player) {
		TradeSession session = directory.tradeOf(player.getUUID()).orElse(null);
		if (session == null || !session.state().menuOpen()) {
			return;
		}
		boolean apart = proximity(session) != ProximityRules.Problem.OK;
		session.clearReadyFromClose();
		refreshBoth(session, true);
		if (!apart) {
			tell(player, "Trade still open. /trade reopens this screen. /trade cancel returns your items.");
		}
	}

	@Override
	public boolean stillOpen(TradeMenu menu) {
		TradeSession session = directory.tradeOf(menu.viewerId()).orElse(null);
		if (session == null || !session.state().menuOpen()) {
			return false;
		}
		ProximityRules.Problem near = proximity(session);
		if (near != ProximityRules.Problem.OK) {
			noteProximity(session, near);
			return false;
		}
		return true;
	}

	private void arm(ServerPlayer player, TradeSession session) {
		session.touch(System.currentTimeMillis());
		ProximityRules.Problem near = proximity(session);
		if (near != ProximityRules.Problem.OK) {
			noteProximity(session, near);
			return;
		}
		int countA = escrow(session, Side.A).size();
		int countB = escrow(session, Side.B).size();
		ArmResult result = session.arm(session.sideOf(player.getUUID()), countA, countB);
		if (result == ArmResult.EMPTY) {
			tell(player, "Add at least one stack before you ready up.");
			return;
		}
		refreshBoth(session, true);
	}

	private void clickSource(TradeMenu menu, ServerPlayer player, TradeSession session, int cell) {
		switch (menu.tab()) {
			case INVENTORY -> {
				int inventoryIndex = TradeLayout.inventoryIndex(TradeLayout.SOURCE_START + cell, menu.page());
				if (inventoryIndex >= 0) {
					takeFromInventory(player, session, inventoryIndex);
				}
			}
			case ENDER -> takeFromEnder(player, session, cell);
			case PICKER -> {
				TradeSources.ListedBox box = TradeSources.boxAt(player, menu.page(), cell);
				if (box != null) {
					menu.showContents(box.slot(), box.ender());
					refresh(player, true);
				}
			}
			case CONTENTS -> takeInner(player, session, menu.focusEnder(), menu.focusSlot(), cell);
		}
	}

	private void takeFromInventory(ServerPlayer player, TradeSession session, int inventoryIndex) {
		takeFrom(player, session, player.getInventory(), inventoryIndex, ItemOrigin.inventory(inventoryIndex));
	}

	private void takeFromEnder(ServerPlayer player, TradeSession session, int slot) {
		takeFrom(player, session, player.getEnderChestInventory(), slot, ItemOrigin.ender(slot));
	}

	private void takeFrom(ServerPlayer player, TradeSession session, Container container, int slot, ItemOrigin origin) {
		ItemEscrow held = escrow(session, session.sideOf(player.getUUID()));
		if (slot < 0 || slot >= container.getContainerSize()) {
			return;
		}
		ItemStack stack = container.getItem(slot);
		if (stack.isEmpty()) {
			return;
		}
		if (blacklisted(stack)) {
			tell(player, "That item is blacklisted.");
			return;
		}
		if (ShulkerBoxes.isBox(stack) && held.holdsContentsOf(ItemOrigin.shulker(origin.kind() == ItemOrigin.Kind.ENDER, slot, 0))) {
			tell(player, "Put this shulker's items back into it before offering the box.");
			return;
		}
		if (held.size() >= TradeLimits.MAX_OFFER_STACKS) {
			tell(player, "You can offer up to 8 stacks.");
			return;
		}
		ItemStack taken = stack.copy();
		try {
			EscrowTake.removeAfterJournal(
					() -> persistOffer(session, session.sideOf(player.getUUID()), held, taken, origin),
					() -> container.setItem(slot, ItemStack.EMPTY));
		} catch (RuntimeException failure) {
			PlayerTrades.LOGGER.error("Could not journal escrow for {}", session.id(), failure);
			tell(player, "That stack could not be escrowed.");
			return;
		}
		held.add(taken, origin);
		session.onEscrowEdited();
		session.touch(System.currentTimeMillis());
		refreshBoth(session, true);
	}

	private void takeInner(ServerPlayer player, TradeSession session, boolean ender, int boxSlot, int inner) {
		ItemEscrow held = escrow(session, session.sideOf(player.getUUID()));
		Container container = ender ? player.getEnderChestInventory() : player.getInventory();
		if (boxSlot < 0 || boxSlot >= container.getContainerSize()) {
			tell(player, "That shulker is no longer there.");
			return;
		}
		ItemStack box = container.getItem(boxSlot);
		ShulkerBoxes.Extraction extraction = ShulkerBoxes.extract(box, inner);
		if (extraction == null) {
			return;
		}
		if (blacklisted(extraction.removed())) {
			tell(player, "That item is blacklisted.");
			return;
		}
		if (held.size() >= TradeLimits.MAX_OFFER_STACKS) {
			tell(player, "You can offer up to 8 stacks.");
			return;
		}
		ItemStack removed = extraction.removed().copy();
		ItemOrigin origin = ItemOrigin.shulker(ender, boxSlot, inner);
		try {
			EscrowTake.removeAfterJournal(
					() -> persistOffer(session, session.sideOf(player.getUUID()), held, removed, origin),
					() -> container.setItem(boxSlot, extraction.box()));
		} catch (RuntimeException failure) {
			PlayerTrades.LOGGER.error("Could not journal escrow for {}", session.id(), failure);
			tell(player, "That stack could not be escrowed.");
			return;
		}
		held.add(removed, origin);
		session.onEscrowEdited();
		session.touch(System.currentTimeMillis());
		refreshBoth(session, true);
	}

	private void returnOfferStack(ServerPlayer player, TradeSession session, int index) {
		ItemEscrow held = escrow(session, session.sideOf(player.getUUID()));
		if (index >= held.size()) {
			return;
		}
		ItemEscrow.Entry entry = held.remove(index);
		int before = entry.stack().getCount();
		ItemStack leftover = ItemMover.place(player, entry.origin(), entry.stack(), false, ItemMover.allowDrop(config.overflow()));
		if (!leftover.isEmpty()) {
			held.add(leftover, entry.origin());
			if (leftover.getCount() == before) {
				tell(player, "Your inventory is full, so that stack stays in the offer.");
				rememberJournal(session);
				refreshBoth(session, true);
				return;
			}
		}
		session.onEscrowEdited();
		session.touch(System.currentTimeMillis());
		rememberJournal(session);
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
		ProximityRules.Problem near = proximity(left, right);
		if (near != ProximityRules.Problem.OK) {
			session.resumeNegotiation();
			tell(left, proximityText(near));
			tell(right, proximityText(near));
			refreshBoth(session, true);
			return;
		}
		ItemEscrow leftEscrow = escrow(session, Side.A);
		ItemEscrow rightEscrow = escrow(session, Side.B);
		if (blacklisted(leftEscrow) || blacklisted(rightEscrow)) {
			endSession(session, "blacklist", null, false);
			return;
		}
		if (!config.allowEmptyTrades() && (leftEscrow.isEmpty() || rightEscrow.isEmpty())) {
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
		PlayerStorage leftBags = PlayerStorage.live(left.getInventory(), left.getEnderChestInventory());
		PlayerStorage rightBags = PlayerStorage.live(right.getInventory(), right.getEnderChestInventory());
		boolean swapped = false;
		try {
			CommitOutcome outcome = SwapCommit.commit(leftBags, rightBags, leftGives, rightGives, config.overflow());
			switch (outcome.kind()) {
				case SUCCESS -> {
					swapped = true;
					spawnDrops(left, leftBags);
					spawnDrops(right, rightBags);
					logTerminal(session, "COMPLETED", "");
					deleteJournal(session.id());
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
				deleteJournal(session.id());
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
		logTerminal(session, "CANCELLED", reason);
		rememberCooldown(session);
		directory.unlink(session);
		ServerPlayer left = server.getPlayerList().getPlayer(session.playerA());
		ServerPlayer right = session.playerB() == null ? null : server.getPlayerList().getPlayer(session.playerB());
		settleEscrow(session, Side.A, left, deadOrGone, deathDrop);
		settleEscrow(session, Side.B, right, deadOrGone, deathDrop);
		rememberJournal(session);
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
		notice(session.playerA(), session.nameA(), OfferNotice.CANCELLED);
		PlayerTrades.LOGGER.info("Trade {} cancelled ({}) between {} and {}", session.id(), reason, session.nameA(), session.nameB());
	}

	private void finish(TradeSession session) {
		if (!closed.add(session.id())) {
			return;
		}
		rememberCooldown(session);
		directory.unlink(session);
		ServerPlayer left = server.getPlayerList().getPlayer(session.playerA());
		ServerPlayer right = server.getPlayerList().getPlayer(session.playerB());
		closeMenu(left);
		closeMenu(right);
		tell(left, "Trade complete.");
		tell(right, "Trade complete.");
		escrowA.remove(session.id());
		escrowB.remove(session.id());
		notice(session.playerA(), session.nameA(), OfferNotice.COMPLETED);
	}

	private void settleEscrow(TradeSession session, Side side, ServerPlayer owner, ServerPlayer deadOrGone, boolean deathDrop) {
		ItemEscrow held = escrow(session, side);
		if (held.isEmpty()) {
			return;
		}
		UUID ownerId = side == Side.A ? session.playerA() : session.playerB();
		if (owner == null && deadOrGone != null && ownerId != null && ownerId.equals(deadOrGone.getUUID())) {
			owner = deadOrGone;
		}
		if (owner == null) {
			return;
		}
		List<ItemEscrow.Entry> saved = held.copyEntries();
		boolean dying = (deathDrop && owner == deadOrGone) || !owner.isAlive();
		try {
			ItemMover.giveBack(owner, held, dying && !keepsInventory(owner));
		} catch (RuntimeException failure) {
			if (held.isEmpty()) {
				held.restore(saved);
			}
			PlayerTrades.LOGGER.error("Could not return {} escrow in trade {}", side, session.id(), failure);
		}
	}

	private static boolean keepsInventory(ServerPlayer player) {
		if (player.level() == null) {
			return false;
		}
		return Boolean.TRUE.equals(player.level().getGameRules().get(GameRules.KEEP_INVENTORY));
	}

	private void persistOffer(TradeSession session, Side side, ItemEscrow held, ItemStack extra, ItemOrigin origin) {
		List<ItemEscrow.Entry> projected = new ArrayList<>(held.copyEntries());
		projected.add(new ItemEscrow.Entry(extra.copy(), origin));
		writeJournal(session, side, projected);
	}

	private void rememberJournal(TradeSession session) {
		try {
			writeJournal(session, null, null);
		} catch (RuntimeException failure) {
			PlayerTrades.LOGGER.error("Could not update the escrow journal for {}", session.id(), failure);
		}
	}

	private void writeJournal(TradeSession session, Side projectedSide, List<ItemEscrow.Entry> projected) {
		List<EscrowJournal.Side> sides = new ArrayList<>();
		appendSide(sides, session, Side.A, projectedSide == Side.A ? projected : peek(session, Side.A));
		appendSide(sides, session, Side.B, projectedSide == Side.B ? projected : peek(session, Side.B));
		try {
			EscrowJournal.write(journalDir, new EscrowJournal.Page(session.id(), sides));
		} catch (IOException failure) {
			throw new IllegalStateException("Could not write the escrow journal", failure);
		}
	}

	private void appendSide(List<EscrowJournal.Side> sides, TradeSession session, Side side, List<ItemEscrow.Entry> entries) {
		UUID player = side == Side.A ? session.playerA() : session.playerB();
		if (player == null) {
			if (entries != null && !entries.isEmpty()) {
				throw new IllegalStateException("Escrow has no owner");
			}
			return;
		}
		List<EscrowJournal.Line> lines = EscrowCodecs.encode(server, entries);
		if (!lines.isEmpty()) {
			sides.add(new EscrowJournal.Side(player, lines));
		}
	}

	private List<ItemEscrow.Entry> peek(TradeSession session, Side side) {
		ItemEscrow stored = (side == Side.A ? escrowA : escrowB).get(session.id());
		if (stored == null) {
			return List.of();
		}
		return stored.copyEntries();
	}

	private void deleteJournal(UUID sessionId) {
		try {
			EscrowJournal.delete(journalDir, sessionId);
		} catch (IOException failure) {
			PlayerTrades.LOGGER.error("Could not delete the escrow journal for {}", sessionId, failure);
		}
	}

	private boolean liveEscrow(UUID sessionId) {
		return escrowA.containsKey(sessionId) || escrowB.containsKey(sessionId) || directory.tradeById(sessionId).isPresent();
	}

	private static List<EscrowJournal.Side> sidesFor(EscrowJournal.Page page, UUID player) {
		List<EscrowJournal.Side> sides = new ArrayList<>();
		for (EscrowJournal.Side side : page.sides()) {
			if (player.equals(side.player()) && !side.lines().isEmpty()) {
				sides.add(side);
			}
		}
		return sides;
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

	private void replaceBridge() {
		DiscordBridge previous = this.bridge;
		this.bridge = new NoOpDiscordBridge();
		if (previous != null) {
			try {
				previous.shutdown();
			} catch (Throwable failure) {
				PlayerTrades.LOGGER.warn("Discord bridge did not shut down cleanly");
			}
		}
		DiscordBridge next;
		try {
			next = discordHost.open(config.discord());
		} catch (Throwable failure) {
			PlayerTrades.LOGGER.warn("Discord bridge was not started");
			next = new NoOpDiscordBridge();
		}
		this.bridge = next == null ? new NoOpDiscordBridge() : next;
	}

	private static boolean discordChanged(DiscordSettings before, DiscordSettings after) {
		if (before == null || after == null) {
			return true;
		}
		return before.enabled() != after.enabled()
				|| !before.token().equals(after.token())
				|| !before.guildId().equals(after.guildId())
				|| !before.tradesChannelId().equals(after.tradesChannelId());
	}

	private void postToDiscord(UUID playerId, String name, String text) {
		try {
			bridge.postOffer(playerId, name, text);
		} catch (RuntimeException failure) {
			PlayerTrades.LOGGER.warn("Discord offer post failed");
		}
	}

	private void notice(UUID playerId, String name, OfferNotice notice) {
		try {
			bridge.updateOffer(playerId, name, notice);
		} catch (RuntimeException failure) {
			PlayerTrades.LOGGER.warn("Discord offer update failed");
		}
	}

	private void withdrawAdvertisement(UUID playerId, OfferNotice notice) {
		TradeSession offer = directory.offerOf(playerId).orElse(null);
		if (offer == null || !directory.withdrawOffer(playerId)) {
			return;
		}
		this.notice(offer.playerA(), offer.nameA(), notice);
	}

	private void expireOffers(long now) {
		for (TradeSession offer : directory.offers()) {
			if (offer.expired(now) && expiredNotices.add(offer.id())) {
				notice(offer.playerA(), offer.nameA(), OfferNotice.EXPIRED);
			}
		}
	}

	private void deliverOnServer(UUID sessionId, String speaker, String text) {
		if (!RelayPolicy.fromDiscord(text)) {
			return;
		}
		TradeSession session = directory.tradeById(sessionId).orElse(null);
		boolean open = session != null && session.state().menuOpen() && session.playerB() != null;
		ServerPlayer left = session == null ? null : server.getPlayerList().getPlayer(session.playerA());
		ServerPlayer right = !open || session.playerB() == null ? null : server.getPlayerList().getPlayer(session.playerB());
		if (!RelayPolicy.bothOnline(open, left != null, right != null)) {
			return;
		}
		String who = speaker == null || speaker.isBlank() ? "Discord" : speaker.trim();
		String body = text.trim();
		if (body.length() > 300) {
			body = body.substring(0, 300);
		}
		tell(left, "[Discord] " + who + ": " + body);
		tell(right, "[Discord] " + who + ": " + body);
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
		TradePainter.paint(
				menu,
				session,
				escrow(session, side),
				escrow(session, other),
				session.ready(side),
				session.ready(other),
				TradeSources.cells(viewer, menu),
				TradeSources.pages(viewer, menu));
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
			case "timeout" -> "The trade timed out. Your items were returned.";
			case "blacklist" -> "The trade was cancelled because an offered item is blacklisted. Your items were returned.";
			default -> "Trade cancelled. Your items were returned.";
		};
	}

	private void logTerminal(TradeSession session, String result, String reason) {
		if (tradeLog == null || session.playerB() == null || !logged.add(session.id())) {
			return;
		}
		ServerPlayer left = server.getPlayerList().getPlayer(session.playerA());
		ServerPlayer right = server.getPlayerList().getPlayer(session.playerB());
		String world = "minecraft:overworld";
		if (left != null) {
			world = left.level().dimension().identifier().toString();
		} else if (right != null) {
			world = right.level().dimension().identifier().toString();
		}
		String line = TradeLogWriter.line(
				server,
				session,
				result,
				reason,
				world,
				escrow(session, Side.A).copyEntries(),
				escrow(session, Side.B).copyEntries());
		try {
			TradeLog.append(tradeLog, line);
		} catch (IOException failure) {
			PlayerTrades.LOGGER.error("Could not append the trade log");
		}
	}

	private void rememberCooldown(TradeSession session) {
		if (session.playerB() == null || config.cooldownSeconds() <= 0) {
			return;
		}
		long until = System.currentTimeMillis() + config.cooldownMillis();
		cooldowns.block(session.playerA(), until);
		cooldowns.block(session.playerB(), until);
	}

	private boolean cooling(ServerPlayer player) {
		if (permissions.allows(player.getUUID(), PermissionChecker.BYPASS_COOLDOWN)) {
			return false;
		}
		long remaining = cooldowns.remainingMillis(player.getUUID(), System.currentTimeMillis());
		if (remaining <= 0) {
			return false;
		}
		tell(player, "You can trade again in " + Cooldowns.secondsRemaining(remaining) + " seconds.");
		return true;
	}

	private boolean permit(ServerPlayer player, String node) {
		if (permissions.allows(player.getUUID(), node)) {
			return true;
		}
		tell(player, "You cannot use that.");
		return false;
	}

	private void noteProximity(TradeSession session, ProximityRules.Problem problem) {
		long now = System.currentTimeMillis();
		Long last = proximityNotices.get(session.id());
		boolean repeat = last != null && now - last < 1000L;
		if (session.state().menuOpen()) {
			session.clearReadyFromClose();
			refreshBoth(session, true);
		}
		if (repeat) {
			return;
		}
		proximityNotices.put(session.id(), now);
		String text = proximityText(problem);
		tell(server.getPlayerList().getPlayer(session.playerA()), text);
		if (session.playerB() != null) {
			tell(server.getPlayerList().getPlayer(session.playerB()), text);
		}
	}

	private ProximityRules.Problem proximity(TradeSession session) {
		ServerPlayer left = server.getPlayerList().getPlayer(session.playerA());
		ServerPlayer right = session.playerB() == null ? null : server.getPlayerList().getPlayer(session.playerB());
		if (left == null || right == null) {
			return ProximityRules.Problem.OK;
		}
		return proximity(left, right);
	}

	private ProximityRules.Problem proximity(ServerPlayer left, ServerPlayer right) {
		if (permissions.allows(left.getUUID(), PermissionChecker.BYPASS_PROXIMITY)
				|| permissions.allows(right.getUUID(), PermissionChecker.BYPASS_PROXIMITY)) {
			return ProximityRules.Problem.OK;
		}
		boolean same = left.level().dimension().equals(right.level().dimension());
		double distance = same ? left.distanceTo(right) : 0;
		return ProximityRules.check(same, distance, config.requireSameWorld(), config.maxDistance());
	}

	private String proximityText(ProximityRules.Problem problem) {
		return switch (problem) {
			case DIFFERENT_WORLD -> "You need to be in the same world to trade.";
			case TOO_FAR -> "You need to be within " + config.maxDistance() + " blocks to trade.";
			case OK -> "";
		};
	}

	private void abortBlacklisted() {
		for (TradeSession session : new ArrayList<>(directory.trades())) {
			if (blacklisted(escrow(session, Side.A)) || blacklisted(escrow(session, Side.B))) {
				endSession(session, "blacklist", null, false);
			}
		}
	}

	private boolean blacklisted(ItemEscrow escrow) {
		for (int index = 0; index < escrow.size(); index++) {
			if (blacklisted(escrow.view(index))) {
				return true;
			}
		}
		return false;
	}

	private boolean blacklisted(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return BlacklistWalk.rejects(blacklist, describe(stack, 0));
	}

	private BlacklistWalk.Held describe(ItemStack stack, int depth) {
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		String key = id == null ? "" : id.toString();
		if (depth > BlacklistWalk.MAX_DEPTH) {
			return new BlacklistWalk.Held(key, List.of(), List.of());
		}
		return new BlacklistWalk.Held(key, nested(stack.get(DataComponents.CONTAINER), depth), nested(stack.get(DataComponents.BUNDLE_CONTENTS), depth));
	}

	private List<BlacklistWalk.Held> nested(ItemContainerContents contents, int depth) {
		return nestedCopies(contents == null ? null : contents.itemCopies(), depth);
	}

	private List<BlacklistWalk.Held> nested(BundleContents contents, int depth) {
		return nestedCopies(contents == null ? null : contents.itemCopies(), depth);
	}

	private List<BlacklistWalk.Held> nestedCopies(java.util.stream.Stream<ItemStack> copies, int depth) {
		if (copies == null) {
			return List.of();
		}
		List<BlacklistWalk.Held> children = new ArrayList<>();
		copies.forEach(inner -> {
			if (inner != null && !inner.isEmpty()) {
				children.add(describe(inner, depth + 1));
			}
		});
		return children;
	}

	private ItemBlacklist resolve(List<String> raw) {
		List<String> accepted = new ArrayList<>();
		for (String entry : raw) {
			Identifier id = Identifier.tryParse(entry);
			if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
				PlayerTrades.LOGGER.warn("Ignoring unknown blacklist id {}", entry);
				continue;
			}
			accepted.add(id.toString());
		}
		return new ItemBlacklist(accepted);
	}

	private void spawnDrops(ServerPlayer player, PlayerStorage bags) {
		for (Object drop : bags.drainDrops()) {
			if (drop instanceof ItemStack stack && !stack.isEmpty()) {
				spawnOne(player, stack);
			}
		}
	}

	private void spawnOne(ServerPlayer player, ItemStack stack) {
		try {
			ItemMover.drop(player, stack);
			return;
		} catch (RuntimeException failure) {
			PlayerTrades.LOGGER.error("Could not drop a trade stack at the player, retrying at spawn", failure);
		}
		try {
			var level = server.overworld();
			if (level == null) {
				PlayerTrades.LOGGER.error("No world left for a trade drop of {}", stack);
				return;
			}
			var pos = level.getRespawnData().pos();
			ItemMover.dropAt(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, stack);
		} catch (RuntimeException failure) {
			PlayerTrades.LOGGER.error("Could not drop a trade stack at spawn: {}", stack, failure);
		}
	}
}
