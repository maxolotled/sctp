# Shop Logger (Fabric client mod)

Logs Snailcraft-style chest/barrel shop signs to CSV and Excel, either as you
shop normally or automatically as you walk near known shops.

## Sign format

Sign is always on the **front face** of the chest/barrel (the side with the
visible latch/handle — technically the block's `FACING` property). Front or
back of the sign both work.

```
[PRICE]
<amount>          per single item
<currency>        e.g. diamond, diamondblock
<seller username>
```

Price is stored both as the raw per-item amount and normalized to **price
per 64** (`amount * 64`) for easy cross-shop comparison.

## Two scan paths

### Manual (always on)
Right-click a chest/barrel yourself — the mod reads what's already rendered
in the vanilla GUI you're looking at. Nothing hidden, nothing automated.

### Automatic (off by default — toggle with a keybind)
Every ~5 seconds the mod scans loaded chunks in a 4-chunk radius for
chests/barrels with a valid shop sign on their front face and remembers
them. On each tick, if you're within interaction range (4.5 blocks) of a
known shop that hasn't been scanned in the last 5 minutes, it:

1. Sends a normal block-interact packet to the server (same as you
   right-clicking it).
2. Intercepts the resulting `OpenScreen`/inventory-sync packets via a mixin
   **before** a GUI is ever constructed on screen — you'll never see it
   flash open.
3. Reads the synced item stacks, logs them, and sends a close-screen packet
   back to the server so nothing stays "open" server-side.

Rate limits (edit constants at the top of `ShopAutoScanner.java`):
- Minimum time between any two silent opens — no longer a fixed number.
  It adapts to how long recent silent opens actually took to resolve
  (`DEFAULT_COOLDOWN_MS` 750ms until there's real data, then
  `COOLDOWN_SAFETY_MULTIPLIER`x the slowest of the last
  `ROUND_TRIP_SAMPLES`, clamped between `MIN_COOLDOWN_MS` and
  `MAX_COOLDOWN_MS`) — see `getAdaptiveCooldownMs()`.
- `PER_SHOP_COOLDOWN_MS` — 5 minutes before re-checking the same shop
- `INTERACT_RANGE` — 4.5 blocks (normal reach distance)

Toggle it in-game with the **Toggle automatic proximity scanning** keybind
(unbound by default — set it under Controls > Shop Logger). It confirms
in chat with how many shops it currently knows about.

Double chests are handled: if the sign isn't on the half you're nearest to,
the mod also checks the paired half's front face.

## Exporting

Press **Export shop log** (also set under Controls > Shop Logger) to write:

- `run/shoplogger/shops.csv`
- `run/shoplogger/shops.xlsx`

Columns: Item Name, Base Item, Price Per Item, Price Per 64, Currency,
Amount, Seller, Position, Last Seen. Both files regenerate fully from the
current in-memory log each time, combining whatever was logged from both
the manual and automatic paths, keyed by (container position, item), so
re-scans update rows instead of duplicating them.

## Building

Requires JDK 17+.

```bash
./gradlew build
```

Output jar: `build/libs/shoplogger-1.0.0.jar` — drop it in your Fabric
`mods/` folder alongside Fabric API for Minecraft 1.20.4.

**Note on mixin method names:** `onOpenScreen` and `onInventory` are the
Yarn-mapped names for `ClientPlayNetworkHandler`'s screen-open and
inventory-sync packet handlers as of 1.20.4. If Loom fails to apply the
mixin at build time (usually a clear error naming the missing method), run
`./gradlew genSources` and check the actual method names for your exact
Minecraft/Yarn version in that class — packet handler names have stayed
stable for a long time but can shift between major versions.

## File map

- `ShopSign.java` — sign parsing + price normalization
- `SignFinder.java` — front-face sign lookup, double-chest aware
- `ShopEntryFactory.java` — turns a synced container's slots into log rows
- `ShopLog.java` — shared in-memory store for both scan paths
- `ShopScanner.java` — manual (right-click) path
- `ShopAutoScanner.java` — automatic silent-scan path (discovery, targeting,
  rate limiting, arm/disarm state machine)
- `mixin/ClientPlayNetworkHandlerMixin.java` — the packet interception that
  makes the silent path possible
- `CsvExporter.java` / `ExcelExporter.java` — output formatting
