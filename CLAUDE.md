# ICBM Basics — CLAUDE.md

Fabric mod for Minecraft **1.21.11** (Yarn mappings, Loom 1.17). Missiles + a launcher block
with a GUI for targeting/waypoints. See `README.md` for player-facing docs (crafting, config).
This file is for me (Claude) — file map + gotchas so I don't have to re-explore each session.

## Repo layout gotcha

There are **two copies** of the project in this repo:

- `src/` at repo root — **this is the active one**. `settings.gradle` names the root project
  `icbm-basics` and root `build.gradle` builds from root `src/`. Always edit here.
- `icbm-basics/` (a nested subfolder with its own full `src/`, `build.gradle`, etc.) — a **stale
  duplicate**, untouched since the initial commit, not referenced by root `settings.gradle` as a
  subproject. Don't edit it; it's dead scaffold, not part of the build.

Package root for everything below: `com.example.icbmbasics`.

## File map (root `src/`)

Server/common (`src/main/java/com/example/icbmbasics/`):
- `ICBMBasics.java` — `ModInitializer`. Loads config, registers items/blocks/block-entities/
  entities/screen handlers, registers all C2S/S2C network payload receivers (target-set,
  waypoint save/delete, waypoint-list push).
- `block/MissileLauncherBlock.java` — `BlockWithEntity`. FACING + POWERED state, faces placer
  like a dispenser, opens GUI on right-click, fires on redstone **rising edge** only
  (`neighborUpdate`), drops its ammo item via `ItemScatterer` in `onStateReplaced`.
- `block/entity/MissileLauncherBlockEntity.java` — implements `Inventory` (2 slots:
  `MISSILE_SLOT = 0`, `USB_SLOT = 1`) + `ExtendedScreenHandlerFactory<LauncherScreenData>`.
  Holds `targetX/Y/Z` + `hasTarget`, plus its **own per-block** `List<Waypoint> waypoints`
  (`saveWaypoint`/`removeWaypoint`/`getWaypoints`, case-insensitive overwrite-by-name — every
  launcher has an independent list now, there's no shared/global one). `tryLaunch()` requires
  both a loaded `ICBM_MISSILE` item and a locked target; spawns a `MissileEntity`, consumes the
  ammo, plays launch sound/particles. NBT via `writeData`/`readData` (this MC version's
  `WriteView`/`ReadView`, not raw NBT compounds) — the waypoint list is written with
  `view.put("Waypoints", Waypoint.CODEC.listOf(), waypoints)` / read with
  `view.read("Waypoints", Waypoint.CODEC.listOf())`.
- `item/UsbDriveItem.java` — plain `Item` override of `use()`: right-click opens the drive's own
  GUI (`UsbDriveScreenHandler`/`UsbDriveScreen`) via an inline `ExtendedScreenHandlerFactory`
  keyed by `Hand` (not `BlockPos` — the drive has no world position). Waypoints live **on the
  item stack itself** via the `ModComponents.WAYPOINTS` data component, so a drive's list is
  portable between launchers/worlds and independent of any block entity.
- `entity/MissileEntity.java` — boost → cruise → terminal-dive flight, trail particles,
  impact/explosion + crater carving. Renders client-side as an oversized item
  (`FlyingItemEntityRenderer`, see client init) — no custom model yet.
- `screen/MissileLauncherScreenHandler.java` — `ScreenHandler` with two slots (constants
  `MISSILE_SLOT_X/Y`, `USB_SLOT_X/Y`) + `addPlayerSlots(...)` for the player's inventory/hotbar
  (`PLAYER_INV_Y` constant, shared with the client screen so layout stays in sync — bumped to
  190 to make room for the extra waypoint section). Carries the launcher's own waypoint list
  (`getLauncherWaypoints`/`setLauncherWaypoints`, pushed live via `LauncherWaypointListPayload`)
  plus `getDriveWaypoints()`, which reads straight off slot 1's `ItemStack` component —
  **no separate payload needed for the drive list**, since vanilla already syncs slot
  `ItemStack`s (components included) to the client automatically. `quickMove` routes by slot
  index (0/1 = special slots) and by item type when moving from the player inventory.
- `screen/UsbDriveScreenHandler.java` — slotless `ScreenHandler` backing the drive's GUI; holds
  the `Hand` it was opened from and the `PlayerEntity`, so `canUse` can verify the player is
  still holding a `USB_DRIVE` in that hand.
- `registry/Mod*.java` — `ModItems`, `ModBlocks`, `ModBlockEntities`, `ModEntities`,
  `ModScreenHandlers`, `ModComponents`: plain registration holders, each with a `register()`
  called from `ICBMBasics.onInitialize()`. `ModComponents.WAYPOINTS` is a
  `ComponentType<List<Waypoint>>` (`Registries.DATA_COMPONENT_TYPE`) built from
  `Waypoint.CODEC.listOf()` / `Waypoint.LIST_PACKET_CODEC` — read/write via
  `ItemStack.getOrDefault(ModComponents.WAYPOINTS, List.of())` / `.set(...)`. This was the
  mod's first data component; there's no NBT-on-item precedent to follow elsewhere.
- `network/` — payload records for the custom networking:
  - `SetTargetPayload` (C2S) — Confirm Target button.
  - `SaveLauncherWaypointPayload` / `DeleteLauncherWaypointPayload` (C2S, keyed by launcher
    `BlockPos`) — edits to a launcher's own list. `LauncherWaypointListPayload` (S2C) echoes the
    refreshed list back.
  - `SaveDriveWaypointPayload` / `DeleteDriveWaypointPayload` (C2S, keyed by `Hand` — the
    server resolves the actual stack via `player.getStackInHand(hand)`, never trusting a
    client-supplied stack) — edits to a USB drive's on-item list. `DriveWaypointListPayload`
    (S2C) echoes the refreshed list back.
  - `LauncherScreenData` (S2C, via `ExtendedScreenHandlerFactory`) — initial launcher GUI-open
    payload: pos, target x/y/z, hasTarget, the launcher's own waypoints (**not** the drive's —
    that comes along on the USB slot's synced stack once the screen opens).
  - `UsbDriveScreenData` (S2C, via `ExtendedScreenHandlerFactory`) — initial drive GUI-open
    payload: which `Hand`, and that hand's current waypoint list.
  - `Waypoint` — record `(name, x, y, z)`; has both a `Codec` (for NBT/component storage) and a
    `PacketCodec` (for network), plus a shared `LIST_PACKET_CODEC`. No longer tied to a single
    global store — both launcher block entities and USB drive item stacks persist their own
    `List<Waypoint>` independently.
- `config/ICBMConfig.java` — JSON config at `config/icbmbasics.json`
  (`explosionPower`, `destructionRadius`, `terrainDestruction`, `missileSpeed`). Loaded once into
  the static `ICBMBasics.CONFIG`.

There is **no more world-wide/shared waypoint list** — the old `storage/WaypointStorage.java`
(a `PersistentState` bound to the overworld) was deleted. Every launcher now keeps its own
list, and USB drives carry theirs on the item.

Client-only (`src/client/java/com/example/icbmbasics/client/`):
- `ICBMBasicsClient.java` — `ClientModInitializer`. Registers the missile entity renderer, both
  `HandledScreens` factories (launcher + USB drive), the `LauncherWaypointListPayload` receiver
  (refreshes a launcher GUI's own list) and the `DriveWaypointListPayload` receiver (refreshes
  an open drive GUI's list).
- `screen/MissileLauncherScreen.java` — `HandledScreen<MissileLauncherScreenHandler>`. Fully
  custom-drawn (plain fills, no texture): X/Y/Z coord fields, Confirm Target + Use My Location
  buttons, name field + Save button, and **two** scrollable waypoint sections — the launcher's
  own (editable: click name to load, click "x" to delete) and the slotted drive's
  (`DRIVE_LABEL_COLOR`-tinted header, read-only — click name to load, no delete; edit it via the
  drive's own GUI instead). Layout constants at the top of the class (`COORD_ROW_Y`,
  `CONFIRM_ROW_Y`, `LAUNCHER_LIST_Y`, `DRIVE_LIST_Y`, etc.) —
  **the item-slot grid (ammo + USB slots, player inventory/hotbar) is pinned to vanilla 18px
  spacing and can't be shrunk without breaking item-icon alignment; only the custom-drawn
  section above it is free to resize** (it grew to fit the second waypoint section, pushing
  `PLAYER_INV_Y` down to 190). `PLAYER_INV_Y` is defined once on the screen handler and
  referenced here so slot hitboxes and drawn backdrops never drift apart.
- `screen/UsbDriveScreen.java` — `HandledScreen<UsbDriveScreenHandler>`. Same plain-fill style
  and X/Y/Z/name/Save/Use-My-Location/list pattern as the launcher screen, but no item slots at
  all (the drive edits its own held stack, not an `Inventory`) — `backgroundHeight` is sized
  just for the custom-drawn content.

Resources (`src/main/resources/`):
- `fabric.mod.json`, `icbmbasics.mixins.json` — mod metadata / mixin config (no mixins yet, just
  the empty config).
- `assets/icbmbasics/lang/en_us.json` — all GUI/translatable strings.
- `assets/icbmbasics/{blockstates,models,items,textures}/` — block/item models + placeholder
  textures for `missile_launcher` and `icbm_missile`.
- `data/icbmbasics/recipe/` — crafting recipes for the launcher and the missile.

Root: `TODO.txt` in `src/` (not resources) is the user's running feature wishlist/roadmap —
check it for context on what's next or already decided against.

## Build / run

```bash
./gradlew compileJava compileClientJava   # quick check, no full build
./gradlew build                            # jar -> build/libs/icbm-basics-1.0.0.jar
./gradlew runClient                        # launch dev client
```
Requires Java 21+. Windows: use `.\gradlew.bat` from PowerShell.

## Things to remember about this codebase

- **Server-authoritative**: all targeting/flight/explosion logic runs server-side; the C2S
  handlers in `ICBMBasics.java` re-validate distance (`isWithinDistance(pos, 8.0)`) and Y bounds
  even though the client GUI already does client-side validation — don't trust client input.
  Same idea in `MissileLauncherScreenHandler.canUse` (delegates to `Inventory.canPlayerUse`,
  which checks proximity to the launcher's actual `BlockPos`).
  Terrain destruction also respects the vanilla `mobGriefing` gamerule on top of the config flag.
- **Redstone trigger is edge-based**: `neighborUpdate` in `MissileLauncherBlock` only calls
  `tryLaunch()` when POWERED flips false→true, so holding power doesn't repeat-fire.
- **This MC version uses `WriteView`/`ReadView`** for block-entity NBT (`writeData`/`readData`),
  not the older raw `NbtCompound` methods — don't reach for `writeNbt`/`readNbt` patterns from
  older tutorials. For arbitrary codec-backed values (not just the primitive `putInt`/
  `putBoolean`/etc. helpers) use `view.put(key, codec, value)` / `view.read(key, codec)` —
  that's how the launcher's per-block `List<Waypoint>` is persisted.
- The `ICBM_MISSILE` item is also used as `MissileEntity.getStack()`'s return (for e.g. item-frame
  rendering of the flying entity) — it's not solely "ammo," so don't assume removing ammo-related
  code lets you delete the item too.
- **USB drive waypoints live on the item stack**, not in any block/world storage — via
  `ModComponents.WAYPOINTS`, a `DataComponentType` (`Registries.DATA_COMPONENT_TYPE`). This was
  the first data component added to the mod; if you add another item-stack-persisted field
  later, follow the same `ComponentType.builder().codec(...).packetCodec(...).build()` pattern
  in `registry/ModComponents.java` rather than reaching for NBT.
- **Slot `ItemStack`s (with their components) sync to the client automatically** as part of the
  vanilla `ScreenHandler` protocol — this is why `MissileLauncherScreenHandler.getDriveWaypoints()`
  can just read `getSlot(USB_SLOT).getStack()` on either side without a dedicated network
  payload. Don't add one for read-only slot-derived data like this; only add a payload for data
  that isn't already riding on a synced slot/stack (e.g. the launcher's own list, which lives on
  the block entity, not a stack).
- **No more global waypoint storage.** Each launcher block entity and each USB drive item stack
  keeps its own independent `List<Waypoint>` — there is no shared/world-wide list anymore (the
  old `storage/WaypointStorage.java` `PersistentState` was removed). Don't reintroduce a shared
  store without checking whether the user still wants per-launcher/per-drive isolation.
