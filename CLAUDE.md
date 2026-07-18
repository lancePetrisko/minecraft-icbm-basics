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
- `block/entity/MissileLauncherBlockEntity.java` — implements `Inventory` (1-slot ammo,
  `MISSILE_SLOT = 0`) + `ExtendedScreenHandlerFactory<LauncherScreenData>`. Holds
  `targetX/Y/Z` + `hasTarget`. `tryLaunch()` requires both a loaded `ICBM_MISSILE` item and a
  locked target; spawns a `MissileEntity`, consumes the ammo, plays launch sound/particles.
  NBT via `writeData`/`readData` (this MC version's `WriteView`/`ReadView`, not raw NBT compounds).
- `entity/MissileEntity.java` — boost → cruise → terminal-dive flight, trail particles,
  impact/explosion + crater carving. Renders client-side as an oversized item
  (`FlyingItemEntityRenderer`, see client init) — no custom model yet.
- `screen/MissileLauncherScreenHandler.java` — `ScreenHandler` with the ammo slot (constants
  `MISSILE_SLOT_X/Y`) + `addPlayerSlots(...)` for the player's inventory/hotbar
  (`PLAYER_INV_Y` constant, shared with the client screen so layout stays in sync). Also carries
  the waypoint list (`getWaypoints`/`setWaypoints`) so the GUI can render/update it live.
- `registry/Mod*.java` — `ModItems`, `ModBlocks`, `ModBlockEntities`, `ModEntities`,
  `ModScreenHandlers`: plain registration holders, each with a `register()` called from
  `ICBMBasics.onInitialize()`.
- `network/` — payload records for the custom networking:
  - `SetTargetPayload` (C2S) — Confirm Target button.
  - `SaveWaypointPayload` / `DeleteWaypointPayload` (C2S) — waypoint list edits.
  - `WaypointListPayload` (S2C) — pushes the refreshed list back after save/delete.
  - `LauncherScreenData` (S2C, via `ExtendedScreenHandlerFactory`) — initial GUI-open payload:
    pos, target x/y/z, hasTarget, waypoints snapshot.
  - `Waypoint` — record `(name, x, y, z)`; has both a `Codec` (for `PersistentState`/NBT-ish
    storage) and a `PacketCodec` (for network), plus a shared `LIST_PACKET_CODEC`.
- `storage/WaypointStorage.java` — `PersistentState`, world-wide named-waypoint list. **Always
  bound to the overworld** (`world.getServer().getOverworld()`) regardless of which dimension a
  launcher is in, so all launchers share one list.
- `config/ICBMConfig.java` — JSON config at `config/icbmbasics.json`
  (`explosionPower`, `destructionRadius`, `terrainDestruction`, `missileSpeed`). Loaded once into
  the static `ICBMBasics.CONFIG`.

Client-only (`src/client/java/com/example/icbmbasics/client/`):
- `ICBMBasicsClient.java` — `ClientModInitializer`. Registers the missile entity renderer, the
  launcher `HandledScreens` factory, and the client-side `WaypointListPayload` receiver that
  live-updates an already-open launcher GUI.
- `screen/MissileLauncherScreen.java` — `HandledScreen<MissileLauncherScreenHandler>`. Fully
  custom-drawn (plain fills, no texture): X/Y/Z coord fields, Confirm Target button, name field +
  Save button, scrollable waypoint list (click name to load into fields, click "x" to delete).
  Layout constants at the top of the class (`COORD_ROW_Y`, `CONFIRM_ROW_Y`, `LIST_Y`, etc.) —
  **the item-slot grid (ammo slot + player inventory/hotbar) is pinned to vanilla 18px spacing
  and can't be shrunk without breaking item-icon alignment; only the custom-drawn section above
  it is free to resize.** `PLAYER_INV_Y` is defined once on the screen handler and referenced
  here so slot hitboxes and drawn backdrops never drift apart.

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
  older tutorials.
- The `ICBM_MISSILE` item is also used as `MissileEntity.getStack()`'s return (for e.g. item-frame
  rendering of the flying entity) — it's not solely "ammo," so don't assume removing ammo-related
  code lets you delete the item too.
