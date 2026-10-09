# Player Trades

Server-side Fabric mod for player-to-player item trades on a Minecraft Java **26.3** dedicated server. Java players and Bedrock players (Geyser/Floodgate) use the same vanilla chest screen. There is no client mod and no custom client UI.

This repository is a buildable skeleton plus the design and milestone plan. Trade sessions, commands, and the Discord bot are not implemented yet. See [docs/MILESTONES.md](docs/MILESTONES.md) and [docs/DESIGN.md](docs/DESIGN.md).

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
