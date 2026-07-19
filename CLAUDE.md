# ICBM Basics — CLAUDE.md

Fabric mod for Minecraft **1.21.11** (Yarn mappings, Loom 1.17). Missiles + a launcher block
with a GUI for targeting/waypoints, a radar block, and tiered defensive armor (blocks + a
codelock door). See `README.md` for player-facing docs (crafting, config).
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
- `block/RadarBlock.java` + `block/entity/RadarBlockEntity.java` — a stationary, non-directional
  `BlockWithEntity` with a `BlockEntityTicker` (`RadarBlockEntity.tick`, registered via
  `RadarBlock.getTicker` using `validateTicker`). Every `SCAN_INTERVAL_TICKS` (10), but **only
  while at least one player has the GUI open** (`viewers` set, added/removed in
  `createMenu`/`RadarScreenHandler.onClosed`), it scans `MissileEntity.getActiveMissiles(world)`
  (a lightweight static per-`ServerWorld` registry on `MissileEntity` — see below) for missiles
  within `ICBMConfig.radarTierDetectionRadii[tier-1]`. A missile first seen very young
  (`age <= LAUNCH_ACQUIRE_AGE_TICKS`) is acquired as **outgoing**: tracked map-wide by UUID until
  it resolves, regardless of later distance. Anything else is **incoming**: shown only while
  actually in range, dropped (no log entry) once it leaves. When a tracked missile disappears
  from the active set (impact), its last-known position is appended to a bounded impact log
  (`MAX_LOG_ENTRIES`), pushed to viewers via `RadarUpdatePayload`. Only one tier ships
  (`RADAR_MK1`); higher tiers are meant to be added the same way as the armor tiers below —
  another `RadarBlock` instance + another `ICBMConfig.radarTierDetectionRadii` entry.
- `entity/MissileEntity.java` also carries a **static per-`ServerWorld` active-missile registry**
  (`ACTIVE_MISSILES`, a `WeakHashMap<ServerWorld, Set<MissileEntity>>`) that radar scans instead
  of iterating every entity in the world. Lazily registers a missile on its first server tick
  (`tick()`); deregisters via an override of `remove(RemovalReason)` — **not**
  `onAddedToWorld`/`onRemoved`, which don't exist as overridable `Entity` methods in this Yarn
  build (tried that first, `javap`'d the yarn-mapped jar under
  `~/.gradle/caches/fabric-loom/minecraftMaven/...` to confirm — see "javap" gotcha below).
  `remove(RemovalReason)` is what `discard()` routes through, so a chunk merely unloading (which
  doesn't call `remove`) never looks like an impact.
- `block/ArmoredBlock.java` + `block/entity/ArmoredBlockEntity.java` — tiered (`ARMORED_BLOCK_MK1/
  2/3`), extremely high blast resistance (200/600/1200, all well above `MissileEntity`'s
  `MAX_CARVED_BLAST_RESISTANCE` of 100) so **neither the vanilla explosion nor crater carving can
  touch it** — only `ArmoredBlockEntity.applyMissileHit()` (called from `MissileEntity.explode()`,
  see below) can break it, after `ICBMConfig.armorTierHits[tier-1]` hits. Damage shows via a
  shared `ArmoredBlock.ARMOR_DAMAGE` `IntProperty` (0–3, one shared instance reused by the door
  block too) recomputed each hit. Placement is capped by an **armor zone**
  (`storage/ArmorZoneStorage.java`) — see its own section below. Both `ArmoredBlockEntity` and
  `ArmoredDoorBlockEntity` implement `block/entity/ArmoredEntity.java` (`applyMissileHit()`), the
  interface `MissileEntity.explode()` scans for, so it doesn't need to know about either type by
  name.
- `block/ArmoredDoorBlock.java` + `block/entity/ArmoredDoorBlockEntity.java` — extends vanilla
  `DoorBlock` directly (not `BlockWithEntity` — `DoorBlock` isn't one) and separately implements
  `BlockEntityProvider` (that interface lives in `net.minecraft.block`, **not**
  `net.minecraft.block.entity`, despite the name — another one confirmed via `javap`).
  `createBlockEntity` only returns a real entity for `HALF == LOWER`; the upper half is purely
  visual (same block instance, `hasBlockEntity()` is state-independent, so `createBlockEntity`
  just returns `null` for the upper half rather than the type being different). Same tiered-armor
  scheme as `ArmoredBlock`, mirrored onto both halves' blockstates on hit. Adds a numeric codelock
  (`code`/`codeSet` fields) — first keypad submission sets the code, later ones must match it
  server-side (`checkCode`), validated in `ICBMBasics.java`'s `SubmitDoorCodePayload` receiver,
  never trusted from the client. **Redstone is fully disabled** (`neighborUpdate` overridden to a
  no-op) so the lock can't be bypassed with a lever. `onUse` opens the keypad GUI instead of
  calling `super.onUse` (which would just toggle the door) — actual opening happens via
  `DoorBlock.setOpen(...)` (public, inherited, no override needed) from the payload receiver only
  after a correct code.
- `item/ArmorToolItem.java` — plain `Item` override of `useOnBlock`: right-click re-anchors the
  nearest armor zone to the clicked block (`ArmorZoneStorage.reanchor`) and reports its count via
  action bar. No ownership/permission model — anyone can re-anchor any zone.
- `storage/ArmorZoneStorage.java` — a `PersistentState` (see the `PersistentState` gotcha below)
  holding a `List` of mutable `ArmorZone` (anchor `BlockPos` + `count`), one store per
  `ServerWorld` (not forced onto the overworld like the old waypoint store was — armor is
  physical to its own dimension). Placing an `ArmoredBlock`/`ArmoredDoorBlock` joins whichever
  zone's anchor is within `ICBMConfig.armorZoneRadius` (30) if it isn't already at
  `armorZoneMaxBlocks` (128), else starts a fresh zone anchored at the new position. **Gated in
  `getPlacementState`** (`ArmorZoneStorage.checkPlacement`, static helper) — placement is refused
  outright (returns `null`, item never leaves the hand) rather than allowed-then-broken. That
  matters: the earlier place-then-`breakBlock`-on-FULL approach was tried first and had a real
  bug — `onStateReplaced` unconditionally releases a zone slot on removal, so breaking a
  never-actually-claimed block would incorrectly decrement an unrelated zone. Gating placement
  itself sidesteps this entirely, since every armored block that ever exists was, by construction,
  successfully claimed. `onStateReplaced` in both `ArmoredBlock` and `ArmoredDoorBlock` releases
  unconditionally on removal (safe, per the above) — the door additionally guards on
  `HALF == LOWER` since both halves fire `onStateReplaced` independently and claim only happened
  once, keyed on the lower half.
- `registry/Mod*.java` — `ModItems`, `ModBlocks`, `ModBlockEntities`, `ModEntities`,
  `ModScreenHandlers`, `ModComponents`: plain registration holders, each with a `register()`
  called from `ICBMBasics.onInitialize()`. `ModComponents.WAYPOINTS` is a
  `ComponentType<List<Waypoint>>` (`Registries.DATA_COMPONENT_TYPE`) built from
  `Waypoint.CODEC.listOf()` / `Waypoint.LIST_PACKET_CODEC` — read/write via
  `ItemStack.getOrDefault(ModComponents.WAYPOINTS, List.of())` / `.set(...)`. This was the
  mod's first data component; there's no NBT-on-item precedent to follow elsewhere. Tiered blocks
  (radar, armored block, armored door) each get one shared `BlockEntityType` registered across
  all their tier blocks via `FabricBlockEntityTypeBuilder.create(ctor, tierBlock1, tierBlock2, ...)`
  — add a new tier block to that vararg list rather than registering a whole new type.
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
  - `RadarScreenData` (S2C, via `ExtendedScreenHandlerFactory`) — initial radar GUI-open payload:
    pos, tier, detection radius, current contacts/log. `RadarUpdatePayload` (S2C) is the live
    periodic push while the GUI stays open, carrying `RadarContact` (id, x/y/z, `outgoing` flag)
    and `RadarLogEntry` (x/y/z of a resolved impact) lists.
  - `SubmitDoorCodePayload` (C2S, keyed by the door's lower-half `BlockPos`) — keypad Submit
    button. `ArmoredDoorScreenData` (S2C, via `ExtendedScreenHandlerFactory`) is the initial
    keypad GUI-open payload: pos + whether a code is already set (picks set-mode vs enter-mode
    client-side) — **the code itself is never sent to the client**, only compared server-side.
- `config/ICBMConfig.java` — JSON config at `config/icbmbasics.json`
  (`explosionPower`, `destructionRadius`, `terrainDestruction`, `missileSpeed`,
  `radarTierDetectionRadii[]`, `armorZoneRadius`, `armorZoneMaxBlocks`, `armorTierHits[]`,
  `armorDamageRadius`). Loaded once into the static `ICBMBasics.CONFIG`. Per-tier values are
  plain arrays indexed by `tier - 1`, clamped in `sanitize()` — adding a tier means appending an
  array entry, not adding a new config field.

There is **no more world-wide/shared waypoint list** — the old `storage/WaypointStorage.java`
(a `PersistentState` bound to the overworld) was deleted. Every launcher now keeps its own
list, and USB drives carry theirs on the item.

Client-only (`src/client/java/com/example/icbmbasics/client/`):
- `ICBMBasicsClient.java` — `ClientModInitializer`. Registers the missile entity renderer, all
  four `HandledScreens` factories (launcher, USB drive, radar, armored door), and the S2C
  refresh receivers: `LauncherWaypointListPayload`/`DriveWaypointListPayload` (waypoint lists),
  `RadarUpdatePayload` (radar contacts/log, matched to the open screen by `BlockPos` since a
  player could conceivably have closed one radar and opened another between pushes).
- `screen/RadarScreen.java` — `HandledScreen<RadarScreenHandler>`, no slots. Custom-drawn circular
  scope: a scanline-filled dark circle, a rotating sweep line driven by wall-clock time
  (`System.currentTimeMillis() % SWEEP_PERIOD_MS`, purely decorative/client-side — doesn't need
  server ticks), range rings, and blips plotted from `RadarContact` positions relative to the
  radar's own `BlockPos` (cyan = outgoing/tracked, red = incoming), plus a scrollable impact log
  below using the same list-scroll pattern as the waypoint lists.
- `screen/ArmoredDoorScreen.java` — `HandledScreen<ArmoredDoorScreenHandler>`, no slots. A 0–9
  digit keypad + Clear + Submit (`gui.icbmbasics.submit_code`) built from `ButtonWidget`s, a
  masked (`*`) display of what's typed so far, and copy that switches between
  `gui.icbmbasics.set_code`/`enter_code` based on `ArmoredDoorScreenHandler.isCodeSet()`
  (client-known only for display; the server independently re-derives set-vs-enter mode from the
  block entity and never trusts the client's guess). Submitting always closes the
  screen (`this.close()`) and lets the server react/message back — no second round-trip to change
  GUI mode mid-session.
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
- `assets/icbmbasics/{blockstates,models,items,textures}/` — block/item models. No new textures
  drawn for radar or armor yet — everything reuses the existing `missile_launcher_*`/`usb_drive`
  PNGs as placeholders (the user is drawing real art later); only the JSON models/blockstates are
  real. The armored block tiers each have 4 model files (`armored_block_mk{n}_{0..3}`, one per
  `armor_damage` stage) wired through a `variants` blockstate keyed on that property alone — the
  door tiers **don't** split by damage stage (facing × half × hinge × open is already 32 variants
  per vanilla's own door blockstate convention; ×4 damage stages was excessive boilerplate for
  placeholder art). Omitting a property from every variant key in a blockstate is fine and
  doesn't crash — Minecraft treats it as "any value," which is how the door blockstates get away
  with not mentioning `armor_damage` at all despite the property existing on the block.
- `data/icbmbasics/recipe/` — crafting recipes for the launcher, the missile, the radar, all six
  armor tiers (block + door × 3, escalating iron → diamond → netherite), and the armor tool.

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
- **No more global waypoint storage** — that's still true specifically for waypoints. Each
  launcher block entity and each USB drive item stack keeps its own independent `List<Waypoint>`,
  no shared/world-wide list (the old `storage/WaypointStorage.java` `PersistentState` was
  removed). Don't reintroduce a shared waypoint store without checking the user still wants
  per-launcher/per-drive isolation. **This does not generalize to every future feature** —
  `storage/ArmorZoneStorage.java` is a new `PersistentState` (same shape as the deleted
  `WaypointStorage`: `world.getPersistentStateManager().getOrCreate(STATE_TYPE)`), and that's
  intentional. A per-base placement budget is inherently shared/global by design, not a
  per-item/per-block concern like waypoints were — don't read the waypoint decision as "never use
  `PersistentState` again."
- **Radar only scans while its GUI is open.** `RadarBlockEntity.tick` bails immediately if
  `viewers` is empty — no server work happens for a radar nobody is looking at. If you add
  behavior that should happen regardless of whether the GUI is open (e.g. a future SAM site
  reacting to radar contacts), don't gate it behind `viewers`; that check exists purely as a perf
  optimization for the GUI's own live-update push, not a general "is the radar active" signal.
- **Missile-hit armor logic lives in `MissileEntity.explode()`, not in the blocks.** Both
  `ArmoredBlockEntity` and `ArmoredDoorBlockEntity` implement `block/entity/ArmoredEntity` purely
  so `explode()` can scan a radius and call `applyMissileHit()` without knowing which concrete
  type it hit. If you add another armored block type later, implement that interface rather than
  teaching `MissileEntity` a new `instanceof` check.
- **`javap` the yarn-mapped jar before guessing vanilla API surface**, especially for anything not
  already used elsewhere in this codebase (vanilla `DoorBlock`, `BlockEntityProvider`,
  `BlockSetType`, `TallBlockItem`, `Entity` lifecycle hooks). The plain intermediary jars under
  `~/.gradle/caches/fabric-loom/<version>/minecraft-*.jar` are **obfuscated**, not yarn-named —
  the actual named jar is under
  `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-common/<version>-net.fabricmc.yarn.<version>+build.<n>-v2/`.
  `unzip` the class you care about out of that jar, then run
  `~/.gradle/jdks/eclipse_adoptium-*/bin/javap.exe -p SomeClass.class` (no `javap` on PATH in this
  environment, but a JDK under `.gradle/jdks` has one). This is how `Entity#onAddedToWorld`/
  `onRemoved` were confirmed **not** to exist in this Yarn build (had to fall back to overriding
  `remove(RemovalReason)` + lazy first-tick registration instead — see `MissileEntity` above), and
  how `BlockEntityProvider`'s real package (`net.minecraft.block`, not `net.minecraft.block.entity`)
  was confirmed after a first guess compiled wrong.
