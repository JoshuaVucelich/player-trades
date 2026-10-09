package com.vucelich.playertrades.discord.jda;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.vucelich.playertrades.PlayerTrades;
import com.vucelich.playertrades.config.DiscordSettings;
import com.vucelich.playertrades.discord.DiscordBridge;
import com.vucelich.playertrades.discord.DiscordInbox;
import com.vucelich.playertrades.discord.InboundDispatch;
import com.vucelich.playertrades.discord.OfferMessages;
import com.vucelich.playertrades.discord.OfferNotice;
import com.vucelich.playertrades.discord.RelayPolicy;
import com.vucelich.playertrades.discord.TokenRedaction;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

/**
 * Posts offer text and copies negotiation chat. This class has no item API and cannot commit a trade.
 * Inbound text is handed to the server thread before any player is touched.
 */
public final class JdaDiscordBridge extends ListenerAdapter implements DiscordBridge {
	private static final EnumSet<Message.MentionType> NO_PINGS = EnumSet.noneOf(Message.MentionType.class);
	private static final Duration LOGIN_WAIT = Duration.ofSeconds(20);
	private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(5);

	private final DiscordSettings settings;
	private final DiscordInbox inbox;
	private final Consumer<Runnable> serverThread;
	private final String token;
	private final Map<UUID, TrackedOffer> offers = new ConcurrentHashMap<>();
	private final Map<UUID, ThreadChannel> threads = new ConcurrentHashMap<>();
	private final Map<String, UUID> sessionsByThread = new ConcurrentHashMap<>();
	private final Map<UUID, List<String>> waitingRelays = new ConcurrentHashMap<>();
	private final Set<UUID> openingThreads = ConcurrentHashMap.newKeySet();
	private final AtomicBoolean channelWarned = new AtomicBoolean();
	private volatile JDA jda;

	private JdaDiscordBridge(DiscordSettings settings, DiscordInbox inbox, Consumer<Runnable> serverThread) {
		this.settings = settings;
		this.inbox = inbox;
		this.serverThread = serverThread;
		this.token = settings.token();
	}

	public static DiscordBridge connect(DiscordSettings settings, DiscordInbox inbox, Consumer<Runnable> serverThread) {
		JdaDiscordBridge bridge = new JdaDiscordBridge(settings, inbox, serverThread);
		JDA api = null;
		try {
			api = JDABuilder.createLight(
					settings.token(),
					EnumSet.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT))
					.addEventListeners(bridge)
					.build();
			bridge.jda = api;
			if (!bridge.awaitConnected()) {
				bridge.shutdown();
				PlayerTrades.LOGGER.warn("Discord login failed. Trades continue in game.");
				return new com.vucelich.playertrades.discord.NoOpDiscordBridge();
			}
			bridge.warnIfChannelMissing();
			PlayerTrades.LOGGER.info("Discord bridge connected");
			return bridge;
		} catch (Throwable failure) {
			try {
				bridge.jda = api;
				bridge.shutdown();
			} catch (Throwable ignored) {
				// A broken bot must not stop the Minecraft server.
			}
			PlayerTrades.LOGGER.warn("Discord login failed: {}", TokenRedaction.redact(String.valueOf(failure.getMessage()), settings.token()));
			return new com.vucelich.playertrades.discord.NoOpDiscordBridge();
		}
	}

	@Override
	public void postOffer(UUID playerId, String playerName, String offerText) {
		if (playerId == null) {
			return;
		}
		TrackedOffer offer = offers.computeIfAbsent(playerId, ignored -> new TrackedOffer());
		synchronized (offer) {
			// A message can start only one thread. After a trade, post a fresh message
			// so the next negotiation is not stuck on the previous thread.
			if (offer.threaded) {
				offer.messageId = null;
				offer.sent = null;
				offer.sending = false;
				offer.threaded = false;
			}
			offer.desired = OfferMessages.open(playerName, offerText);
			flush(offer);
		}
	}

	@Override
	public void updateOffer(UUID playerId, String playerName, OfferNotice notice) {
		if (playerId == null || notice == null) {
			return;
		}
		publish(playerId, OfferMessages.closed(playerName, notice));
	}

	@Override
	public void openThread(UUID sessionId, UUID offererId, String nameA, String nameB) {
		if (sessionId == null || offererId == null || threads.containsKey(sessionId)) {
			return;
		}
		TrackedOffer offer = offers.computeIfAbsent(offererId, ignored -> new TrackedOffer());
		synchronized (offer) {
			if (threads.containsKey(sessionId) || openingThreads.contains(sessionId) || sessionId.equals(offer.pendingSession)) {
				return;
			}
			offer.pendingSession = sessionId;
			offer.pendingNameA = nameA;
			offer.pendingNameB = nameB;
			if (offer.messageId != null) {
				spawnThread(offer);
			}
		}
	}

	@Override
	public void relayFromGame(UUID sessionId, String speaker, String text) {
		if (sessionId == null || !RelayPolicy.fromDiscord(text)) {
			return;
		}
		String line = OfferMessages.relay(speaker, text);
		ThreadChannel thread = threads.get(sessionId);
		if (thread != null) {
			send(thread, line);
			return;
		}
		List<String> queue = waitingRelays.computeIfAbsent(sessionId, ignored -> new ArrayList<>());
		synchronized (queue) {
			if (queue.size() < 20) {
				queue.add(line);
			}
		}
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		if (!event.isFromThread() || event.getAuthor().isBot() || event.isWebhookMessage()) {
			return;
		}
		UUID sessionId = sessionsByThread.get(event.getChannel().getId());
		if (sessionId == null) {
			return;
		}
		if (!sameGuild(event)) {
			return;
		}
		String text = event.getMessage().getContentDisplay();
		if (!RelayPolicy.fromDiscord(text)) {
			return;
		}
		String speaker = speaker(event.getAuthor());
		InboundDispatch.onServerThread(serverThread, () -> {
			if (inbox != null) {
				inbox.deliver(sessionId, speaker, text.trim());
			}
		});
	}

	@Override
	public void shutdown() {
		JDA current;
		synchronized (this) {
			current = this.jda;
			this.jda = null;
		}
		if (current == null) {
			return;
		}
		current.shutdown();
		try {
			if (!current.awaitShutdown(SHUTDOWN_WAIT)) {
				current.shutdownNow();
				current.awaitShutdown(Duration.ofSeconds(2));
			}
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			current.shutdownNow();
		}
	}

	private void publish(UUID playerId, String body) {
		TrackedOffer offer = offers.computeIfAbsent(playerId, ignored -> new TrackedOffer());
		synchronized (offer) {
			offer.desired = body;
			flush(offer);
		}
	}

	private void flush(TrackedOffer offer) {
		GuildMessageChannel channel = tradesChannel();
		if (channel == null || offer.desired == null || offer.desired.equals(offer.sent)) {
			return;
		}
		String body = offer.desired;
		if (offer.messageId != null) {
			offer.sent = body;
			channel.editMessageById(offer.messageId, body).setAllowedMentions(NO_PINGS).queue(ignored -> {}, this::logFailure);
			return;
		}
		if (offer.sending) {
			return;
		}
		offer.sending = true;
		channel.sendMessage(body).setAllowedMentions(NO_PINGS).queue(message -> {
			synchronized (offer) {
				offer.sending = false;
				offer.messageId = message.getId();
				offer.sent = body;
				if (!body.equals(offer.desired)) {
					flush(offer);
				}
				if (offer.pendingSession != null) {
					spawnThread(offer);
				}
			}
		}, failure -> {
			synchronized (offer) {
				offer.sending = false;
			}
			logFailure(failure);
		});
	}

	private void spawnThread(TrackedOffer offer) {
		UUID sessionId = offer.pendingSession;
		String messageId = offer.messageId;
		if (sessionId == null || messageId == null || threads.containsKey(sessionId)) {
			return;
		}
		if (!openingThreads.add(sessionId)) {
			offer.pendingSession = null;
			return;
		}
		offer.pendingSession = null;
		GuildMessageChannel channel = tradesChannel();
		if (!(channel instanceof IThreadContainer container)) {
			warnChannel();
			return;
		}
		String nameA = offer.pendingNameA;
		String nameB = offer.pendingNameB;
		String boundMessage = messageId;
		container.createThreadChannel(OfferMessages.threadName(nameA, nameB), messageId).queue(thread -> {
			synchronized (offer) {
				if (boundMessage.equals(offer.messageId)) {
					offer.threaded = true;
				}
			}
			threads.put(sessionId, thread);
			sessionsByThread.put(thread.getId(), sessionId);
			List<String> queued = waitingRelays.remove(sessionId);
			if (queued == null) {
				return;
			}
			List<String> copy;
			synchronized (queued) {
				copy = new ArrayList<>(queued);
			}
			for (String line : copy) {
				send(thread, line);
			}
		}, this::logFailure);
	}

	private void send(ThreadChannel thread, String line) {
		thread.sendMessage(line).setAllowedMentions(NO_PINGS).queue(ignored -> {}, this::logFailure);
	}

	private GuildMessageChannel tradesChannel() {
		JDA api = this.jda;
		if (api == null || settings.guildId().isBlank() || settings.tradesChannelId().isBlank()) {
			return null;
		}
		long channelId;
		long guildId;
		try {
			channelId = Long.parseLong(settings.tradesChannelId().trim());
			guildId = Long.parseLong(settings.guildId().trim());
		} catch (NumberFormatException bad) {
			return null;
		}
		TextChannel text = api.getTextChannelById(channelId);
		GuildMessageChannel channel = text != null ? text : api.getNewsChannelById(channelId);
		if (!(channel instanceof GuildChannel guildChannel) || guildChannel.getGuild().getIdLong() != guildId) {
			return null;
		}
		return channel;
	}

	private void warnIfChannelMissing() {
		if (tradesChannel() == null) {
			warnChannel();
		}
	}

	private void warnChannel() {
		if (channelWarned.compareAndSet(false, true)) {
			PlayerTrades.LOGGER.warn("Discord trades channel was not found");
		}
	}

	private boolean awaitConnected() throws InterruptedException {
		JDA api = this.jda;
		if (api == null) {
			return false;
		}
		long deadline = System.nanoTime() + LOGIN_WAIT.toNanos();
		while (System.nanoTime() < deadline) {
			JDA.Status status = api.getStatus();
			if (status == JDA.Status.CONNECTED) {
				return true;
			}
			if (status == JDA.Status.FAILED_TO_LOGIN || status == JDA.Status.SHUTDOWN) {
				return false;
			}
			Thread.sleep(100L);
		}
		return api.getStatus() == JDA.Status.CONNECTED;
	}

	private boolean sameGuild(MessageReceivedEvent event) {
		if (settings.guildId().isBlank() || event.getGuild() == null) {
			return false;
		}
		return settings.guildId().trim().equals(event.getGuild().getId());
	}

	private static String speaker(User author) {
		String display = author.getGlobalName();
		if (display == null || display.isBlank()) {
			display = author.getName();
		}
		return display == null ? "Discord" : display;
	}

	private void logFailure(Throwable failure) {
		String message = failure == null ? "" : String.valueOf(failure.getMessage());
		PlayerTrades.LOGGER.warn("Discord request failed: {}", TokenRedaction.redact(message, token));
	}

	private static final class TrackedOffer {
		private String messageId;
		private boolean sending;
		private String desired;
		private String sent;
		private UUID pendingSession;
		private String pendingNameA;
		private String pendingNameB;
		private boolean threaded;
	}
}
