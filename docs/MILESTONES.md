# Player Trades milestones

Work is split into issues that can land independently. Later issues assume earlier ones in the same milestone are done, unless an issue says otherwise. Do not start these until the skeleton build is in place.

Out of scope for M1–M4: offline item escrow, a NeoForge loader, custom client screens, and parsing item names out of chat text. The chat line is an advertisement. Items move only because a player selected them in the chest menu.

## M1 — Sessions, chest GUI, ready, countdown, inventory swap

Inventory only. No ender chest, no shulker contents, no blacklist, no proximity, no Discord. Safety cancels (disconnect, death, dimension change, explicit cancel) are part of M1 because escrowed items must always have a way home.

### M1-1. Session state machine

**Scope:** Loader-agnostic session types and transitions. No commands and no menu yet.

**Acceptance criteria:**

- A session is keyed by the two player UUIDs. A player cannot be in two sessions, and cannot hold an offer while in a session.
- States are `OFFERED`, `NEGOTIATING`, `COUNTDOWN`, `COMMITTING`, `COMPLETED`, and `CANCELLED`.
- The only legal transitions are the ones in [docs/DESIGN.md](DESIGN.md). Any other transition throws and leaves the previous state unchanged.
- Offer edits increment a generation counter and, from `COUNTDOWN`, return the session to `NEGOTIATING` with both ready flags cleared.
- Unit tests cover every legal transition and at least one illegal transition per state, without starting Minecraft.

### M1-2. Offer and accept

**Scope:** Chat advertisement and commands that create a session. Both players must be online.

**Acceptance criteria:**

- `/trade offer [text]` and a chat message whose first word is `WTS` (any case) store one offer per player. A new offer replaces that player's previous offer. The offer records UUID, exact server name, text, and expiry (default 5 minutes, constant is fine until M2 config).
- The server broadcasts the offer with the literal command `/trade accept <name>` and a clickable run-command link for Java clients. The name is the server profile name, including a Floodgate prefix if one is present.
- `/trade accept <name>` opens a session only when the offer exists, is unexpired, the offerer is online, neither player is already in a session, and the names differ.
- Unknown names, expired offers, and self-accept produce a chat error and do not open a menu.
- Accepting consumes the offer. Cancelling the session does not restore the advertisement.

### M1-3. Inventory escrow

**Scope:** Selecting a stack removes it from the survival inventory into the session.

**Acceptance criteria:**

- Clicking a main-inventory or hotbar stack in the trade menu moves the whole stack into that player's escrow and out of the inventory.
- The escrowed stack is the same component-bearing stack that left the inventory (`ItemStack.copy` at the boundary, then the original slot cleared). Count, damage, enchantments, and custom data survive.
- Clicking an escrowed offer slot returns that stack to the inventory. If the inventory cannot fit it, the click is rejected and the stack stays in escrow.
- Display items (filler, buttons, the other player's offer) cannot be taken, dropped, swapped with a number key, or drag-painted.
- Closing the menu does not return escrow and does not delete it. `/trade` reopens the menu for a player who is still in the session.

### M1-4. Chest menu

**Scope:** Both players get a vanilla 6-row chest menu. No new `MenuType` registration.

**Acceptance criteria:**

- The server opens `MenuType.GENERIC_9x6`. A vanilla Java client shows a double chest. No client class is added.
- The slot map matches the layout in [docs/DESIGN.md](DESIGN.md): partner offer, own offer, own inventory page, control row.
- The partner's offer slots are display copies. Taking them is denied.
- Ready and Cancel are items in the control row, not a custom screen widget.
- `stillValid` is false when the session is no longer `NEGOTIATING` or `COUNTDOWN`, which closes the menu.

### M1-5. Ready and countdown

**Scope:** Both players arm the trade, then a short server-side countdown runs.

**Acceptance criteria:**

- Ready is rejected when that player's escrow is empty.
- When the second player readies, the session enters `COUNTDOWN` for 3 seconds (60 server ticks).
- Any escrow add or remove clears both ready flags, aborts the countdown, and returns the session to `NEGOTIATING`.
- The ready slot's item name shows the remaining whole seconds to both players.
- A player closing the menu clears both ready flags but leaves escrow in place.
- At zero the commit runs inside that same server tick, synchronously. It does not schedule work onto another thread.

### M1-6. Atomic inventory swap

**Scope:** Move each escrow into the other player's inventory, or restore both inventories exactly.

**Acceptance criteria:**

- Commit runs only on the server thread, only from `COUNTDOWN` at zero, and only when the generation counter matches the value captured at countdown start.
- The planner simulates both insertions against a copy of each inventory before mutating. If either side does not fit, the commit aborts, both readys clear, escrow is unchanged, and both players are told who lacked space. M1 has no overflow.
- On the success path, each escrow stack is inserted, both escrow lists are empty, the state is `COMPLETED`, and both menus close.
- If mutation throws, both inventories are restored from pre-images taken before the first write, escrow is restored from copies taken before the write, and the session is `CANCELLED`.
- A scripted or unit-level test (inventory doubles, no world required if the planner is pure) shows a failed second insertion does not leave the first player's items removed.
- Completed trades cannot be committed twice.

### M1-7. Safety cancels

**Scope:** Disconnect, death, dimension change, and explicit cancel return escrow. Items are never deleted.

**Acceptance criteria:**

- `/trade cancel` and the Cancel slot, during `NEGOTIATING` or `COUNTDOWN`, return escrow to that owner and cancel for both.
- Disconnect returns escrow during the disconnect callback, before player data is saved. Rejoining shows the items in the inventory. If they do not fit, they go to the ender chest, then drop at the last position.
- Death drops escrow at the death position as item entities. It does not put items back into the inventory that death is clearing.
- A dimension change cancels the session and returns escrow in the destination world.
- After every cancel path, both players have no session, both menus are closed, and the sum of items (inventory + ender chest + drops + leftover escrow) equals the pre-trade sum.

## M2 — Tabs, space, timeout, blacklist, proximity

### M2-1. Config file

**Scope:** Load `config/player_trades.json` from the Fabric config directory. Missing file writes the default. Invalid file logs an error and keeps the previous in-memory config (or the defaults, on first load).

**Acceptance criteria:**

- The file matches the schema in [docs/DESIGN.md](DESIGN.md).
- `discord.token` is never logged.
- M1 constants (countdown, offer lifetime) move to this file. Defaults stay 3 seconds and 5 minutes.

### M2-2. Source tabs

**Scope:** The source area can show the survival inventory, the ender chest, the contents of a shulker in the inventory, or the contents of a shulker in the ender chest.

**Acceptance criteria:**

- Control-row buttons switch tabs. The active tab is highlighted.
- Inventory tab pages through 36 storage slots. Ender chest is one page of 27. A shulker is one page of 27.
- A shulker picker lists shulker boxes found in the inventory and in the ender chest as separate entries. Choosing one shows its contents.
- Clicking a shulker on the inventory or ender tab escrows the box itself. Clicking an item on a contents tab escrows that inner stack only.
- Offering the box is rejected while any of its contents are already in escrow, with a chat explanation.
- Shulkers nested inside shulkers are ignored. One level only.

### M2-3. Container component safety

**Scope:** Inner-stack edits write a new container component. They never mutate a list shared with another stack.

**Acceptance criteria:**

- Removing an inner stack copies the shulker, replaces `DataComponents.CONTAINER` with a new `ItemContainerContents`, and writes that copy back to the slot.
- The escrowed inner stack still has its own components (enchants, custom name, damage).
- Two shulker copies do not alias. Editing one in the trade does not change a copy that was already in the other hand or in escrow.
- Returning an inner stack on cancel restores it to the original box when that box is still in the same inventory and has room. Otherwise it uses the M2 overflow rule.

### M2-4. Space checks and overflow

**Scope:** Commit may use the ender chest, then a drop, when the destination inventory is full.

**Acceptance criteria:**

- `overflow` of `abort` matches M1: no write happens, countdown cancels.
- `overflow` of `ender_then_drop` (the default) fills inventory, then ender chest, then drops the rest at the player's feet.
- The simulation accounts for items the other side is receiving and for items leaving. A swap of two full inventories of different items succeeds.
- Armor and offhand are never used as overflow space.
- No item is destroyed when every destination is full. Leftovers are entities in the world.

### M2-5. Timeout and cooldown

**Scope:** Offers and idle sessions expire. A finished trade sets a per-player cooldown.

**Acceptance criteria:**

- An offer older than `offerTimeoutSeconds` cannot be accepted.
- A session with no escrow edit and no ready click for `sessionTimeoutSeconds` cancels and returns items.
- After `COMPLETED` or `CANCELLED`, both players refuse new offers and accepts until `cooldownSeconds` elapses. The message says how long remains.
- Defaults match the config table in the design doc.

### M2-6. Item blacklist

**Scope:** Operators list item ids the menu will not escrow.

**Acceptance criteria:**

- `blacklist` entries are item identifiers. A click on a listed item is rejected, the stack stays put, and the player is told the item is blacklisted.
- The check applies to inner shulker contents and to the shulker item itself.
- Unknown ids in the file are logged at load and ignored. They do not crash the server.
- Already-escrowed items are rechecked at commit. A reload that blacklists one of them aborts the countdown and returns items.

### M2-7. Proximity and same world

**Scope:** Optional distance and dimension limits.

**Acceptance criteria:**

- `requireSameWorld` false and `maxDistance` 0 (defaults) impose no limit.
- When enabled, the check runs at accept, at each ready click, once per countdown second, and at commit.
- Failure aborts to `NEGOTIATING` if both players are still online and in a session, clears readys, and explains which rule failed.
- A dimension change still cancels outright (M1-7). Proximity does not override that.
- Distance is horizontal-plus-vertical block distance between the two players, measured only when they share a dimension.

## M3 — Trade log and admin commands

### M3-1. Append-only trade log

**Scope:** Every terminal session writes one JSON line.

**Acceptance criteria:**

- The file is `config/player_trades/trades.jsonl`.
- Each line has the fields in the design doc, including both escrows serialized with the item stack codec so components survive.
- `COMPLETED`, `CANCELLED`, and expired timeouts all write a line. A crash mid-`COMMITTING` is still represented if the failure handler runs.
- The writer does not truncate the file on restart.

### M3-2. Admin log commands

**Scope:** Read the log from in game.

**Acceptance criteria:**

- `/trade log [player] [page]` lists recent rows (time, id, names, result) newest first, 8 per page.
- `/trade log show <id>` prints both sides' item ids, counts, and custom names.
- A player without `playertrades.admin.log` gets the same denial as a missing command. No row contents leak.
- An unknown id reports not found.

### M3-3. Admin cancel and reload

**Scope:** Force-stop a session and reread config.

**Acceptance criteria:**

- `/trade admin cancel <player>` cancels that player's session using the M1 return rules and writes a log line with reason `admin`.
- `/trade admin reload` rereads config. A bad file keeps the previous config and tells the operator.
- Reload never prints `discord.token`.

### M3-4. Permission nodes

**Scope:** LuckPerms when it is installed, otherwise operator level.

**Acceptance criteria:**

- Nodes match the table in the design doc.
- With LuckPerms absent, `playertrades.trade` is allowed for every player, and `playertrades.admin.*` requires permission level 2.
- With LuckPerms present, its result is used and the level-2 fallback is not applied on top.
- Floodgate UUIDs go through the same check as Java UUIDs. No separate Bedrock path.

## M4 — Discord bridge

### M4-1. Bridge interface

**Scope:** Core talks to a `DiscordBridge`. The default implementation does nothing.

**Acceptance criteria:**

- `PlayerTrades` trade code does not import a Discord library.
- With `discord.enabled` false or `discord.token` empty, the server logs that the bridge is disabled and does not open a socket.
- The log line does not contain the token.

### M4-2. Bot process

**Scope:** When enabled, connect with the token from config and shut down with the server.

**Acceptance criteria:**

- The bot uses `discord.guildId` and `discord.tradesChannelId`.
- A bad token logs the library's failure and leaves the Minecraft server running. Trades still work in game.
- Server stop disconnects the bot. The token is not written to the trade log or to debug logs.

### M4-3. Offer posts

**Scope:** New and replaced offers are posted to the trades channel.

**Acceptance criteria:**

- The message includes the player name, the offer text, and `/trade accept <name>`.
- Replacing an offer edits or replaces the previous bot message for that player rather than leaving a stale one.
- Expiry, accept, and cancel update the message so it no longer looks open.
- Nothing in Discord can move items. There is no accept button that calls the swap.

### M4-4. Negotiation relay

**Scope:** While a session is `NEGOTIATING` or `COUNTDOWN`, chat between the two players is copied to Discord, and Discord replies are copied back.

**Acceptance criteria:**

- In-game chat from either participant during the session is posted into a thread on the offer message.
- A Discord message in that thread is delivered to both players as a server message, on the server thread.
- Commands and `WTS` lines are not relayed.
- Messages from a player who is not in the session are ignored.
- If either player is offline, the bridge does not create escrow and does not call commit.

### M4-5. Online swap stays in game

**Scope:** Guard rail so the bot cannot become an item path.

**Acceptance criteria:**

- Commit still requires two `ServerPlayer`s who are connected.
- The bridge has no method that accepts item stacks.
- A review of the Discord package shows it only formats text and schedules runnables onto the server thread.

## Later, not scheduled

- Offline escrow: hold items for a player who is not online, then deliver on join. Explicitly not part of M4.
- NeoForge entrypoint in `com.vucelich.playertrades.neoforge`, reusing the core package.
- Partial-stack offers (Java right-click split) if Geyser click translation is reliable enough.
- Trade tax, currency, or auction listings.
