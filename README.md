# Player Trades

Server-side Fabric mod for player-to-player item trades on a Minecraft Java **26.3** dedicated server. Java players and Bedrock players (Geyser/Floodgate) use the same vanilla chest screen. There is no client mod and no custom client UI.

Milestone 2 is implemented: the chest screen can offer inventory stacks, ender chest stacks, and the contents of a shulker in either place. Config lives in `config/player_trades.json` (see `config/player_trades.example.json`). A full inventory overflows into the ender chest, then drops at the player's feet. The trade log and Discord bridge are still ahead. See [docs/MILESTONES.md](docs/MILESTONES.md) and [docs/DESIGN.md](docs/DESIGN.md).

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

The Discord bot token, when that milestone exists, belongs only in the server config file `config/player_trades.json`. Never commit a token.

## Layout

Core code stays in `com.vucelich.playertrades` and may use Minecraft classes, not Fabric or NeoForge. Fabric lifecycle code stays in `com.vucelich.playertrades.fabric`. `src/client` is an empty Loom source set so common code cannot compile against client-only game classes.

## License

[MIT](LICENSE)
