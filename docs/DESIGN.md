# Player Trades design

Server-only Fabric mod for Minecraft Java 26.3. Java and Bedrock players (Geyser/Floodgate) share one dedicated server. Every GUI is a vanilla chest menu (`MenuType.GENERIC_9x6`) so Geyser can draw it. No client entrypoint, no custom `MenuType`, no custom screen.

Minecraft 26.1+ ships unobfuscated. Core code may import `net.minecraft` and stay loader-agnostic. It must not import `net.fabricmc` or a future NeoForge package. Fabric event registration and the dedicated-server entrypoint live in `com.vucelich.playertrades.fabric`.

These names were checked against the unobfuscated Minecraft 26.3 common jar and Fabric API 0.162.0+26.3:

| Concern | Type |
| --- | --- |
| Chest menu | `net.minecraft.world.inventory.ChestMenu` |
| Menu id the client already knows | `MenuType.GENERIC_9x6` (`MenuType<ChestMenu>`) |
| Virtual slots | `net.minecraft.world.SimpleContainer` (54 slots) |
| Open | `ServerPlayer.openMenu(net.minecraft.world.MenuProvider)` returns `OptionalInt` |
| Ender chest | `Player.getEnderChestInventory()` returns `PlayerEnderChestContainer` |
| Shulker contents | `DataComponents.CONTAINER` (`DataComponentType<ItemContainerContents>`), built with `ItemContainerContents.fromItems` |
| Clickable chat | `net.minecraft.network.chat.ClickEvent.RunCommand` |
| Countdown tick | `ServerTickEvents.END_SERVER_TICK` |
| Disconnect | `ServerPlayConnectionEvents.DISCONNECT` |
| Death | `ServerLivingEntityEvents.AFTER_DEATH` |
| Dimension change | `ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL` |
| Chat advertisement | `ServerMessageEvents.CHAT_MESSAGE` |
| Commands | `CommandRegistrationCallback` |
| Identity | player UUID and `GameProfile` name |

Do not register a new menu id. Vanilla clients and Geyser only know the generic 9×N menus.

## Packages

```
com.vucelich.playertrades
  PlayerTrades                 mod id, logger, init
  session                      state machine, offers, escrow
  gui                          slot layout constants, ChestMenu subclass
  swap                         pure insertion planner
  config                       JSON load/save
  log                          jsonl writer
  perm                         node constants and a checker interface
  discord                      DiscordBridge interface and a no-op
com.vucelich.playertrades.fabric
  PlayerTradesFabric           DedicatedServerModInitializer
  commands, tick, chat, disconnect, death, dimension hooks
```

A NeoForge port later adds `com.vucelich.playertrades.neoforge` that calls the same `PlayerTrades.init` and the same session service. No Gradle multi-project until that port is real.

The Discord library (JDA, added in M4) is called only from the bridge implementation. Session code depends on the interface.

## How a trade starts

1. A player types `/trade offer [text]` or a chat line whose first word is `WTS` (any case). The remainder is advertisement text. It is not parsed into items.
2. The server stores one `Offer` per player UUID: offerer UUID, exact server name, text, created-at, expiry. A new offer replaces the old one.
3. The server broadcasts the text plus the literal `/trade accept <name>`. Java clients also get a `ClickEvent.RunCommand` on an `[Accept]` span. Bedrock often drops click events, so the typed command is the path that must work for Geyser.
4. `/trade accept <name>` resolves an online player by exact name. The offer must be unexpired, the offerer online, the two UUIDs different, and neither UUID in a session.
5. The offer is consumed. Both players enter `NEGOTIATING` and both menus open.

Floodgate players are normal `ServerPlayer`s. Use the server UUID and the server name, including the Floodgate prefix. Do not special-case XUIDs.

## State machine

```
                 offer / WTS
        (none) ---------------> OFFERED
                                   |
                            accept (both online)
                                   v
                             NEGOTIATING <---------------------------+
                                   |                                 |
                          both ready, both escrows non-empty         |
                                   v                                 |
                              COUNTDOWN ---- offer edit / close -----+
                                   |         proximity fail (M2)
                                   | zero ticks, generation matches
                                   v
                              COMMITTING
                                 /    \
                          fits /      \ simulate fail or throw
                             v          v
                        COMPLETED    CANCELLED
```

`OFFERED` is not a two-player session. It is the advertisement. Accept creates the session directly in `NEGOTIATING`.

Terminal states are `COMPLETED` and `CANCELLED`. Timeouts use `CANCELLED` with a reason string, not a third terminal state.

| From | Event | To | Side effects |
| --- | --- | --- | --- |
| — | offer / WTS | `OFFERED` | replace previous offer for that UUID |
| `OFFERED` | accept | `NEGOTIATING` | consume offer, open both menus, generation = 1 |
| `OFFERED` | expiry, disconnect, new offer | — | drop the advertisement; no items are held |
| `NEGOTIATING` | both ready, both escrows non-empty | `COUNTDOWN` | store generation, set 60 ticks (config) |
| `NEGOTIATING` or `COUNTDOWN` | escrow add/remove | `NEGOTIATING` | generation++, both readys false, ticks cleared |
| `COUNTDOWN` | close menu, proximity fail | `NEGOTIATING` | both readys false; escrow kept |
| `COUNTDOWN` | ticks hit 0 and generation matches | `COMMITTING` | synchronous commit on the server thread |
| `COMMITTING` | both insertions applied | `COMPLETED` | clear escrow, close menus, log |
| `COMMITTING` | simulate says no space (abort policy) | `NEGOTIATING` | no writes, both readys false |
| any non-terminal | cancel, timeout, disconnect, death, dimension change, commit throw | `CANCELLED` | return or drop escrow, close menus, log |

Illegal transitions throw and do not change state. `COMMITTING` rejects re-entrant offer edits; the menu ignores clicks in that state.

All mutations run on the server thread. The countdown is a single `ServerTickEvents.END_SERVER_TICK` handler that decrements each active session once. At zero it calls commit before returning. No worker thread touches inventories.

## GUI layout

One 54-slot chest. Slot numbers are the menu's container slots (the player inventory is slots 54–89 and is handled separately).

```
Row 0  slots  0-7   Partner offer (display copies)
       slot   8     Partner ready lamp
Row 1  slots  9-16  Your offer (click to take back)
       slot   17    Your ready lamp
Rows 2-4 slots 18-44  Source page (27 slots)
Row 5  slots 45-53  Controls
```

Control row:

| Slot | Control |
| --- | --- |
| 45 | Previous source page |
| 46 | Tab: Inventory |
| 47 | Tab: Ender chest (M2; M1 shows a barrier and rejects the click) |
| 48 | Tab: Shulker picker (M2; M1 barrier) |
| 49 | Next source page |
| 50 | Filler |
| 51 | Filler |
| 52 | Cancel |
| 53 | Ready |

M1 uses the source page as a window onto the 36-slot survival inventory (27 + 9). The real inventory is also slots 54–89 under the chest, because that is how a vanilla chest screen is built. Clicks there escrow the same way as clicks on the source page. Both paths call one method.

M2 source pages:

- Inventory: 36 slots, two pages.
- Ender chest: 27 slots, one page.
- Shulker picker: one icon per shulker box in the inventory and per shulker box in the ender chest. The icon's name says which container it came from.
- Shulker contents: 27 slots of the chosen box.

Eight offer stacks per side is the M1 cap. A ninth selection is rejected with a chat line. Raising the cap means paging the offer rows, not a new menu type.

Icons are vanilla items: lime dye for ready, gray dye for not ready, barrier for cancel, chest, ender chest, shulker box, black stained glass pane for filler. Each display stack gets a custom-data marker so a real item of the same type cannot be confused with a button. The marker is never a gameplay component the player can keep, because display stacks are never inserted into a player inventory.

### Click policy

Override `clicked` and `quickMoveStack`. Deny `THROW`, `SWAP` (number keys), and `QUICK_CRAFT` (drag) on every virtual slot. Deny shift-click from the virtual container into the player inventory except for the player's own offer slots, which return that escrow stack.

| Target | Allowed action |
| --- | --- |
| Own inventory / hotbar / source page | Escrow the whole stack |
| Own offer slot | Return that stack to inventory, or reject if it does not fit |
| Partner offer, lamps, filler | Deny |
| Ready | Toggle own ready if escrow is non-empty |
| Cancel | Cancel the session |
| Tab / page | Change the source view only |

M1 moves the whole stack. Partial stacks are a later change and need a Geyser click check before they are trusted.

`stillValid` is true only while the session is `NEGOTIATING` or `COUNTDOWN`, the player is alive, and (M2) proximity still holds. Returning false closes the screen.

Closing the screen does not cancel and does not delete escrow. It clears both ready flags and tells the player the trade is still open: `/trade` reopens, `/trade cancel` returns items. That avoids a disconnect between "I pressed escape" and "my items are gone."

`removed(Player)` must not drop virtual-container stacks. They are icons. Escrow is not stored in the virtual container; it is a list on the session. The virtual slots are display copies refreshed after every edit.

## Escrow

Selecting an item removes it from its source immediately and appends it to that player's escrow list. The other player is sent a display copy. The original object lives only in the list.

Why remove immediately:

- Death drops whatever is still in the inventory. An offered stack that is still in the inventory can drop and also be inserted into the partner.
- A hopper, a click, or a second menu cannot move a stack that is no longer there.
- Disconnect can put the list back in one place, the disconnect callback, instead of hoping a slot index is still valid.

Each escrow entry records:

- the stack (`ItemStack`, components included)
- origin: inventory slot, ender slot, or shulker (container kind, container slot, inner slot)
- the generation at which it was added

Return order on cancel:

1. Original slot, if it is empty or holds the same stack identity we expect for a partial return.
2. Any free slot in the origin container.
3. Survival inventory.
4. Ender chest.
5. Drop at the player's feet (or death position, or last position on disconnect).

Never discard.

### Shulker and ender components (M2)

Shulker contents are the `minecraft:container` component (`DataComponents.CONTAINER`, `ItemContainerContents`), not a block entity, while the box is an item. Ender chest contents are a real inventory on the player.

When taking one inner stack:

1. Copy the shulker `ItemStack`.
2. Copy the contents list out of the component. Do not mutate the list the live stack still references.
3. Remove or shrink the chosen entry.
4. `box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(copiedList))`. `fromItems` is the factory on the 26.3 class.
5. Write the copied box back into the inventory or ender slot.
6. Put a separate copy of the removed inner stack into escrow.

`ItemStack.copy()` is the only way stacks cross this boundary. Do not keep a reference to a stack that is still inside a component list.

Offering the shulker item itself is refused while any escrow entry still points at that box. The player puts those inner stacks back first, or the click is denied. That keeps the box and its component from diverging.

Vanilla does not allow a shulker inside a shulker. The picker does not recurse. A modded nested box is offered as an opaque item, not opened.

While a session is open, a click that would open the real ender-chest block menu is cancelled by `stillValid` on our menu only if ours is the open menu. If the player somehow has another container open, the session cancels. Hoppers cannot see an ender chest or a player inventory, so they are not an escrow path.

## Atomic swap

Commit is one method call on the server thread. Nothing else runs until it returns, so a disconnect packet cannot interleave mid-write. Death and dimension changes are also server-thread events; they either happen before this call sees `isAlive()` / the current level, or after the session is already `COMPLETED` and no longer holding items.

```
commit(session):
  assert server thread
  assert state == COUNTDOWN and ticks == 0
  assert session.generation == generation captured when countdown started
  state = COMMITTING

  if either player is null, disconnected, dead, or (M2) out of range:
    cancel and return
  if blacklist now rejects an escrow stack:
    cancel and return

  escrowA = copy of A's list
  escrowB = copy of B's list
  preA = copy of every A inventory slot (M2: ender chest too)
  preB = copy of every B inventory slot

  plan = simulate(A, escrowB, B, escrowA, overflow policy)
  if plan.rejected and policy is abort:
    state = NEGOTIATING
    clear both readys
    tell both who lacked space
    return

  try:
    apply plan to the live inventories
    clear both escrow lists
    state = COMPLETED
    close menus
    log
  catch:
    write preA and preB back onto the live inventories
    restore escrow lists from escrowA and escrowB
    state = CANCELLED
    return escrow with the overflow rule
    log reason = rollback
```

Simulation uses copies. It places stacks with the same rules as apply: merge into equal stacks that are not full, then empty slots. M1 simulation looks only at the 36 storage slots. M2 then ender chest, then a drop list. Armor and offhand are excluded.

Because each side is both losing escrow (already removed) and gaining the other side's escrow, two full inventories of different items still fit. The simulator must not look at the pre-trade inventory and forget that. Escrow is already gone, so the free space is the live inventory's free space. That is the space that matters.

Apply inserts the actual escrow stacks (not a second copy) so components are not rebuilt by id and count. The copies exist only for rollback.

A completed session drops its escrow lists before `COMPLETED` is visible to other callbacks. A second commit sees a terminal state and returns.

### Disconnect, death, dimension

| Event | When | Where items go |
| --- | --- | --- |
| Disconnect | `ServerPlayConnectionEvents.DISCONNECT`, while the `ServerPlayer` is still being saved | Inventory, then ender chest, then drop at last position. Rejoin must show the items. |
| Death | `ServerLivingEntityEvents.AFTER_DEATH`. Do not put stacks into the dying inventory | Item entities at the death position. The death inventory is about to be cleared or dropped; do not rely on it. |
| Dimension change | `ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL`, after the player is in the new level | Cancel, then return into the inventory in the new level. |
| Explicit cancel | Click or `/trade cancel` | Return order above. |

If the disconnect callback turns out to run after the save in this Loader version, move the return earlier. The acceptance test is rejoin, not the callback name.

## Inventory space

Checked in the simulator at commit, not only when the player clicks Ready. The inventory can change during the countdown (the other 27 slots are still live).

`overflow` in config:

| Value | Behavior |
| --- | --- |
| `abort` | No writes. Countdown returns to `NEGOTIATING`. |
| `ender_then_drop` | Inventory, then ender chest, then `ItemEntity` at the feet. Default. |

M1 hard-codes `abort`. M2 reads the config.

## Config

Path: Fabric config directory, `config/player_trades.json`. Created with defaults on first server start (M2). The Discord token is an empty string in the default file. Do not log the token, do not put a real token in the repository, do not put the runtime file in git.

```json
{
  "countdownSeconds": 3,
  "sessionTimeoutSeconds": 120,
  "offerTimeoutSeconds": 300,
  "cooldownSeconds": 10,
  "requireSameWorld": false,
  "maxDistance": 0,
  "overflow": "ender_then_drop",
  "allowEmptyTrades": false,
  "blacklist": [],
  "discord": {
    "enabled": false,
    "token": "",
    "guildId": "",
    "tradesChannelId": "",
    "relayNegotiation": true
  }
}
```

| Field | Meaning |
| --- | --- |
| `countdownSeconds` | Both-ready delay. `0` is rejected at load; minimum 1. |
| `sessionTimeoutSeconds` | Idle time in `NEGOTIATING` with no escrow edit and no ready click. |
| `offerTimeoutSeconds` | Advertisement lifetime. |
| `cooldownSeconds` | Per-player delay after a terminal state before a new offer or accept. |
| `requireSameWorld` | Accept and commit require the same dimension. |
| `maxDistance` | Block distance. `0` means off. Ignored when they are in different dimensions; same-world handles that. |
| `overflow` | `abort` or `ender_then_drop`. |
| `allowEmptyTrades` | Default false. Ready is refused on an empty escrow. |
| `blacklist` | Item ids, `namespace:path`. |
| `discord.enabled` | Master switch. False skips socket setup even if a token is present. |
| `discord.token` | Bot token. Empty disables the bot. |
| `discord.guildId` / `tradesChannelId` | Where offers are posted. |
| `discord.relayNegotiation` | Thread relay while a session is open. |

Invalid JSON or an unknown `overflow` value: log the parse error, keep the previous config, and do not start the bot with a half-parsed token.

## Permissions

Checker interface in core. Fabric implementation: if LuckPerms is loaded, ask it; otherwise use vanilla permission level. Do not compile against LuckPerms. Reflect or an optional classpath so a server without it still boots.

| Node | Fallback without LuckPerms | Use |
| --- | --- | --- |
| `playertrades.trade` | allow | offer, accept, ready, cancel own, reopen |
| `playertrades.bypass.cooldown` | level 2 | ignore cooldown |
| `playertrades.bypass.proximity` | level 2 | ignore distance and same-world |
| `playertrades.admin.log` | level 2 | `/trade log` |
| `playertrades.admin.cancel` | level 2 | `/trade admin cancel` |
| `playertrades.admin.reload` | level 2 | `/trade admin reload` |

There is no blacklist bypass. Change the list instead.

M1 leaves every trade command allowed and does not register admin commands. The checker arrives with M3; M2 proximity and cooldown call it if it exists, otherwise they apply to everyone.

## Commands

Brigadier, registered from the Fabric side, calling into core.

| Command | Milestone | Who |
| --- | --- | --- |
| `/trade offer [text]` | M1 | `playertrades.trade` |
| `/trade accept <player>` | M1 | `playertrades.trade` |
| `/trade` | M1 | reopen own menu |
| `/trade cancel` | M1 | own session |
| `/trade log [player] [page]` | M3 | admin.log |
| `/trade log show <id>` | M3 | admin.log |
| `/trade admin cancel <player>` | M3 | admin.cancel |
| `/trade admin reload` | M3 | admin.reload |

## Trade log

Append-only JSON lines at `config/player_trades/trades.jsonl`. One record per terminal session. Item stacks go through the vanilla item codec so components round-trip. The log is not a backup of the inventories; it is an audit of what the swap tried to move.

```json
{
  "id": "uuid",
  "time": "2026-10-09T16:00:00Z",
  "result": "COMPLETED",
  "reason": "",
  "world": "minecraft:overworld",
  "players": [
    {"uuid": "...", "name": "Alice"},
    {"uuid": "...", "name": ".Bob"}
  ],
  "offers": [
    [{"id": "minecraft:diamond", "count": 64}],
    [{"id": "minecraft:shulker_box", "count": 1, "components": {}}]
  ]
}
```

`result` is `COMPLETED` or `CANCELLED`. `reason` is empty, `timeout`, `disconnect`, `death`, `dimension`, `rollback`, `admin`, or `blacklist`.

## Discord bridge

```
server thread                         bot threads
  OfferService  -- postOffer() -->     outbound queue --> channel message
  TradeSession  -- relay() ------>     thread message
  server.execute <----------------     inbound listener
  commit()  never called from the bot
```

`DiscordBridge`:

- `postOffer(Offer)`
- `updateOffer(Offer, status)` where status is open, accepted, expired, cancelled
- `relayFromGame(sessionId, speaker, text)`
- `shutdown()`

`NoOpDiscordBridge` is the default. The JDA implementation is created only when `discord.enabled` is true and `token` is non-blank.

Rules:

- The bot posts and talks. It has no API that accepts item stacks.
- Swap runs only inside `commit`, which requires two connected `ServerPlayer`s. A Discord message cannot satisfy that.
- Inbound Discord text is scheduled with `MinecraftServer.execute` before any player is touched.
- Negotiation relay is on only in `NEGOTIATING` and `COUNTDOWN`, and only for the two participants' chat, excluding lines that start with `/` and lines that start a new `WTS` offer.
- Replies land as system messages to both players, prefixed with the Discord speaker.
- Auth failure is logged from the library message with the token redacted. The game server keeps running.
- Server stopping calls `shutdown()` and joins the bot with a short timeout.
- Offline escrow is not this bridge. Offers are for online players only. Holding items for someone who is offline is a later project.

## Proximity (M2)

At accept, at each ready click, once per countdown second, and at the start of commit:

- If `requireSameWorld` and the dimensions differ, fail.
- If `maxDistance` > 0 and the Euclidean distance is greater, fail.

A failed check during countdown returns to `NEGOTIATING` and clears readys. It does not return items. A dimension *change* (the player actually moved worlds) still uses the M1 cancel path, because the open menu and the partner are no longer in the same place and the session should not sit open across worlds by accident. `requireSameWorld` false does not disable the dimension-change cancel.

## Blacklist (M2)

Checked when a stack is about to enter escrow and again at commit. Compare the item id only. Component matching can wait. Unknown ids in the file are skipped with a warning so a typo does not refuse every trade.

## Test notes

State transitions, the insertion planner, config parsing, and blacklist matching should be plain unit tests. They do not need a world. Menu click behavior, disconnect save order, and a Geyser client need a running 26.3 server; those stay as the acceptance checks on the issues.

Do not add a client test source set. A dedicated-server run is the manual check for the chest screen.
