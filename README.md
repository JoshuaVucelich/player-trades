# Player Trades

Server-side Fabric mod for player-to-player item trades on a Minecraft Java **26.3** dedicated server. Java players and Bedrock players (Geyser/Floodgate) use the same vanilla chest screen. There is no client mod and no custom client UI.

Milestones 1 through 4 are implemented. The chest screen can offer inventory stacks, ender chest stacks, and shulker contents. Config lives in `config/player_trades.json` (see `config/player_trades.example.json`). Finished trades are appended to `config/player_trades/trades.jsonl`. Operators with permission level 2 can run `/trade log`, `/trade admin cancel`, and `/trade admin reload`. When Discord is enabled, offers are posted to a channel and negotiation chat is copied into a thread. Items still move only in game, and only while both players are online. See [docs/MILESTONES.md](docs/MILESTONES.md) and [docs/DESIGN.md](docs/DESIGN.md).

```
/trade offer [text]
WTS [text]
/trade accept <name>
/trade
/trade cancel
```

Both players select stacks in the chest screen, then press Ready. Escrowed items leave the inventory immediately. A 3 second countdown swaps them, or the trade pauses if either inventory cannot fit the other side.

## Requirements

- Java 25 (compile and run)
- Minecraft 26.3
- Fabric Loader 0.19.5
- Fabric API 0.162.0+26.3
- Fabric Loom 1.18.3
- Gradle 9.7.1 (via the wrapper)

Geyser and Floodgate are not compile dependencies. Bedrock players are ordinary server players.

## Build

```sh
./gradlew build
```

The mod jar is written to `build/libs/player_trades-0.1.0.jar`.

## Install

1. Run a Minecraft 26.3 dedicated server with Fabric Loader 0.19.5.
2. Put Fabric API and `player_trades-0.1.0.jar` in the server `mods` folder.
3. Do not install this jar on clients. `fabric.mod.json` sets `"environment": "server"`, so a client will ignore it.

The Discord bot token belongs only in the server config file `config/player_trades.json`. That file is gitignored. `config/player_trades.example.json` keeps `"token": ""`. Never commit a token.

## Discord

The bridge stays off until `discord.enabled` is true and `discord.token` is non-blank. The server log then says `Discord bridge disabled` and does not open a socket. A bad token is logged as a login failure with the token redacted. The Minecraft server keeps running, and trades still work in game.

There is no Discord button that moves items. Swaps run only when both players are connected and both press Ready in the chest screen.

### What the owner sets up

1. Open the [Discord Developer Portal](https://discord.com/developers/applications) and create an application.
2. Open **Bot** and add a bot. Copy the bot token. Discord shows it once. Do not paste it into the repository, a screenshot, or the trade log.
3. On the same Bot page, turn on the privileged **Message Content Intent**. The bot has to read thread replies. Server Members Intent is not required.
4. Open **OAuth2**, then **URL Generator**. Choose the `bot` scope. Grant **View Channel**, **Send Messages**, **Create Public Threads**, **Send Messages in Threads**, and **Read Message History**. Open the generated URL and invite the bot to the guild that has `#trades`.
5. In the Discord client, enable Developer Mode (Settings, Advanced). Right-click the server name and choose **Copy Server ID**. Right-click the `#trades` channel and choose **Copy Channel ID**.
6. Start the Minecraft server once so it writes `config/player_trades.json`, or copy `config/player_trades.example.json` to that path. Edit the copy on the server:

```json
"discord": {
  "enabled": true,
  "token": "paste-the-bot-token-here",
  "guildId": "paste-the-server-id-here",
  "tradesChannelId": "paste-the-channel-id-here",
  "relayNegotiation": true
}
```

7. Restart the server, or run `/trade admin reload` as an operator. The log should contain `Discord bridge connected` and must not contain the token. If the channel id is wrong, the log says `Discord trades channel was not found` and in-game trades still work.
8. Post `WTS diamond sword` in game. `#trades` should show the player name, the offer text, and `/trade accept <name>`. Accepting, cancelling, or waiting out the offer updates that message so it no longer looks open. Chat during the trade is copied into a thread on that message, and thread replies come back in game as `[Discord] speaker: text`.

## Layout

Core code stays in `com.vucelich.playertrades` and may use Minecraft classes, not Fabric or NeoForge. Fabric lifecycle code stays in `com.vucelich.playertrades.fabric`. `src/client` is an empty Loom source set so common code cannot compile against client-only game classes.

## License

[MIT](LICENSE)
