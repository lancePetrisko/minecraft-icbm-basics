# ICBM Basics — CLAUDE.md

Fabric mod for Minecraft **1.21.11** (Yarn mappings, Loom 1.17). Missiles + a launcher block
with a GUI for targeting/waypoints, a radar block, tiered defensive armor (blocks + a codelock
door), and automatic ground-to-air defense (SAM sites + CIWS) wired to radar via a plain
connector block. See `README.md` for player-facing docs (crafting, config).
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
- `item/CruiseMissileItem.java` — a plain `Item` subclass whose only override is
  `appendTooltip(...)`, adding a translated line (`item.icbmbasics.cruise_missile.tooltip`)
  advertising the terrain-hugging/evasion/radar-cross-section behavior described under
  `MissileEntity` above. This is the mod's first item tooltip override — the signature in this
  MC version is `appendTooltip(ItemStack, TooltipContext, TooltipDisplayComponent,
  Consumer<Text>, TooltipType)`, not the older `List<Text>`-returning shape from earlier
  versions. Otherwise identical to `ICBM_MISSILE` as launcher ammo — both are valid in
  `MissileLauncherBlockEntity`'s `MISSILE_SLOT` (`isValid` accepts either), and which one was
  consumed is all `tryLaunch` uses to set the spawned `MissileEntity`'s `cruiseMissile` flag.
- `item/UsbDriveItem.java` — plain `Item` override of `use()`: right-click opens the drive's own
  GUI (`UsbDriveScreenHandler`/`UsbDriveScreen`) via an inline `ExtendedScreenHandlerFactory`
  keyed by `Hand` (not `BlockPos` — the drive has no world position). Waypoints live **on the
  item stack itself** via the `ModComponents.WAYPOINTS` data component, so a drive's list is
  portable between launchers/worlds and independent of any block entity.
- `entity/MissileEntity.java` — boost → cruise → terminal-dive flight, trail particles,
  impact/explosion + crater carving. Renders client-side as an oversized item, oriented along
  its own flight path (`render/MissileEntityRenderer`, see client section below) — no custom
  model yet. Carries a `cruiseMissile` flag (persisted, set by
  `MissileLauncherBlockEntity.tryLaunch` based on whether the consumed ammo was `ICBM_MISSILE` or
  `CRUISE_MISSILE` — see the item note below) that gates three things, all off by default for a
  plain `ICBM_MISSILE`: **terrain-hugging cruise** (during the cruise phase, steers toward
  `CRUISE_HEIGHT_ABOVE_GROUND` (12) blocks above whatever's directly below via
  `World.getTopY(Heightmap.Type.MOTION_BLOCKING, ...)`, re-sampled every tick, instead of leveling
  off at whatever altitude boost reached — currently a cosmetic "flies low" look, since
  radar/SAM/CIWS detection is still plain distance-based with no line-of-sight/altitude check);
  a **one-time SAM-evasion juke** (`checkSamEvasion`, checked once per tick until spent — a
  `hasJuked` flag, persisted so a chunk reload mid-flight can't grant a free extra dodge — looks
  for a `SamInterceptorEntity` homing on this missile's own UUID via its new `getTargetId()`
  getter within 12 blocks, and if found, applies a single lateral velocity kick perpendicular to
  its heading, random left/right — deliberately a single dodge, not continuous evasive AI, so SAM
  sites aren't made hopeless); and a smaller **radar cross-section**
  (`getRadarCrossSectionMultiplier()`, 0.55 vs. 1.0 for standard) that actually shrinks the
  effective detection radius `RadarBlockEntity`/`SamSiteBlockEntity`/`CiwsBlockEntity` use against
  this missile specifically (their detection-radius comparisons all multiply by this per-missile,
  not by changing the block's own configured radius) — this is what backs up the cruise missile's
  tooltip claim today, without needing the bigger line-of-sight/altitude radar overhaul that's
  still just a TODO note. `getStack()` also returns whichever item matches the flag, so
  item-frame/renderer display stays correct for either type.
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
  `RadarBlock.getTicker` using `validateTicker`). Every `SCAN_INTERVAL_TICKS` (10), but only
  while at least one player has the GUI open (`viewers` set, added/removed in
  `createMenu`/`RadarScreenHandler.onClosed`) **or a linked monitor pinged it recently**
  (`lastMonitorPingTick`/`pulseFromMonitor` — see `MonitorBlockEntity` below; self-expiring via
  `MONITOR_ACTIVE_WINDOW_TICKS` (40), so an unlinked/broken monitor lets the radar go idle again
  on its own with no explicit deregistration needed), it scans
  `MissileEntity.getActiveMissiles(world)` (a lightweight static per-`ServerWorld` registry on
  `MissileEntity` — see below) for missiles within `ICBMConfig.radarTierDetectionRadii[tier-1]`,
  **scaled per-missile by `MissileEntity.getRadarCrossSectionMultiplier()`** (see the
  cruise-missile note above) rather than by changing the radar's own configured radius. A missile
  first seen very young (`age <= LAUNCH_ACQUIRE_AGE_TICKS`) is acquired as **outgoing**: tracked
  map-wide by UUID until it resolves, regardless of later distance (cross-section doesn't apply
  here — once acquired, an outgoing contact stays tracked). Anything else is **incoming**: shown
  only while actually in (cross-section-adjusted) range, dropped (no log entry) once it leaves.
  When a tracked missile disappears from the active set (impact), its last-known position is
  appended to a bounded impact log (`MAX_LOG_ENTRIES`), pushed to GUI viewers via
  `RadarUpdatePayload` — monitors get their own separate push, see below, not this one. Only one
  tier ships (`RADAR_MK1`); higher tiers are meant to be added the same way as the armor tiers
  below — another `RadarBlock` instance + another `ICBMConfig.radarTierDetectionRadii` entry.
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
- `entity/MissileEntity.destroyByInterceptor(ServerWorld)` — the clean "shot down" path used by
  both ground-to-air defenses on a successful hit. Deliberately **not** `explode()`: a mid-air
  intercept is debris, not a ground impact, so it skips crater carving/armor damage/explosion
  power entirely and just discards with a small particle/sound flourish. `MissileEntity` also
  gained `updateRotation()` (called each tick after velocity), setting yaw/pitch from the
  velocity vector so the entity visibly orients along its flight path — see the client
  `MissileEntityRenderer` note below for why that alone wasn't enough.
- `block/SamSiteBlock.java` + `block/entity/SamSiteBlockEntity.java` — non-directional like
  radar, no GUI on the block itself beyond the ammo slot's own screen. Every
  `SCAN_INTERVAL_TICKS` (10) it picks the nearest missile within `samDetectionRadius` (same
  "ignore anything younger than `LAUNCH_ACQUIRE_AGE_TICKS`" rule as radar, so it doesn't shoot
  its own base's outgoing missile off the pad, and the same per-missile
  `MissileEntity.getRadarCrossSectionMultiplier()` scaling as radar, so a cruise missile has to
  close to less than the configured radius before a site will even consider it) and fires a
  homing `SamInterceptorEntity` at it, rolling `samAccuracy` on arrival. **Sites coordinate through `SamSiteBlockEntity.CLAIMED_TARGETS`**,
  a world-wide (not radius-scoped) `Map<ServerWorld, Set<UUID>>` of missile UUIDs already
  claimed by an in-flight interceptor — claimed the instant a site fires, released by
  `SamInterceptorEntity.remove(RemovalReason)` (same "single override beats releasing at every
  `discard()` call site" reasoning as `MissileEntity`'s own active-registry cleanup) whenever
  that interceptor resolves, however it resolves (hit, miss, lost target, timeout). No two sites
  will ever fire on the same missile at once. Implements `Inventory` (single `SAM_AMMO` slot,
  `AMMO_SLOT = 0`) + `ExtendedScreenHandlerFactory<AmmoScreenData>` — see the ammo-GUI note below.
  Only fires while `WireNetwork.isConnectedToRadar` finds a path to a radar; ammo-empty and
  disconnected are both silent no-ops, not errors.
- `entity/SamInterceptorEntity.java` — homes on its target by **UUID**, re-resolved fresh every
  tick via `MissileEntity.getActiveMissiles(world)` rather than held as a direct reference, so a
  target that resolves mid-flight (already intercepted, already impacted) is handled cleanly —
  the interceptor just can't find it anymore and discards. Recomputes velocity toward the
  target's *current* position each tick (a homing missile, unlike the CIWS's ballistic rounds
  below), rolls `samAccuracy` once within `HIT_DISTANCE_SQ` of the target. `getTargetId()` (a
  public getter added for `MissileEntity`'s own use) exposes which missile UUID it's chasing —
  this is how a cruise missile notices an interceptor is homing on *it specifically* for its
  one-time evasion juke, without either class needing a shared registry beyond this getter.
- `block/CiwsBlock.java` + `block/entity/CiwsBlockEntity.java` — same non-directional/wired-to-
  radar/ammo-GUI shape as the SAM site (including the same per-missile
  `MissileEntity.getRadarCrossSectionMultiplier()` scaling on its target-selection distance
  check), but tuned as a long-range, low-accuracy, very-fast-firing point-defense gun rather than
  a homing missile. **Fire rate is a fractional accumulator, not a
  tick cooldown**: `CiwsBlockEntity.fireAccumulator` (a `double`, persisted as `FireAccumulator`)
  gains `ciwsRoundsPerSecond / 20.0` every tick and fires one burst per whole unit accumulated,
  capped at `MAX_BURSTS_PER_TICK` (20) as a safety net — a plain per-tick integer cooldown can't
  express more than 20 bursts/sec, and this needs to go well past that toward a real Phalanx's
  ~75/sec. **Lesson learned the hard way**: an earlier version paired the cooldown with an outer
  `world.getTime() % SCAN_INTERVAL_TICKS != 0` gate for "only scan every 5 ticks" perf reasons;
  since the cooldown could finish well before the next 5-tick-aligned check, it silently capped
  the *real* fire rate below whatever the cooldown said, for weeks, before anyone noticed ammo
  wasn't draining as fast as expected. There is no such outer gate anymore — the tick handler
  runs every tick and the accumulator alone governs rate. Don't reintroduce one without doing the
  arithmetic to make sure it can't out-throttle the configured rate.
  Each burst computes a **firing solution** (`computeLeadPoint`): a single-pass linear lead using
  the target's current position/velocity and `ciwsBulletSpeed` to estimate where it'll be by the
  time a tracer gets there, then spawns one `CiwsBulletEntity` aimed at that point (not the
  target's current position) — `BULLETS_PER_BURST` is `1` (was `3`; dropped per request once the
  higher fire rate alone read as a stream). The accuracy roll (`ciwsAccuracy`) happens once per
  burst *before* the bullet spawns; only a `lethal`-flagged bullet can actually resolve a hit,
  and only once it "arrives" (see below), not instantly at the muzzle.
- `entity/CiwsBulletEntity.java` — purely cosmetic, ballistic (no gravity, no per-tick homing,
  straight line from spawn velocity) tracer round rendered as a small flying gray-concrete
  "bullet" (`Items.GRAY_CONCRETE` via `getStack()`, same `MissileEntityRenderer` reuse as the
  missile/SAM interceptor — no new texture needed). Doesn't decide hit/miss itself: told at
  `configure()` time whether it's the `lethal` round and how many `travelTicks` until arrival
  (`distance / ciwsBulletSpeed`, matching the lead calculation's own time-of-flight estimate so
  the round visually reaches the lead point right as it resolves). On arrival it looks its target
  back up by UUID (same pattern as `SamInterceptorEntity`) and calls `destroyByInterceptor` only
  if `lethal` and the target still exists; either way it discards. Non-lethal rounds in a burst
  (currently none, since `BULLETS_PER_BURST = 1`, but the mechanism still applies if that's ever
  raised again) get a small random `SPREAD` added to their velocity so a burst doesn't look
  laser-straight.
- `block/WireNetwork.java` — the connectivity gate SAM sites/CIWS/monitors need:
  `findRadar(world, origin)` does a breadth-first search over orthogonal neighbors starting at
  the defense/monitor block's own position, capped at `MAX_NODES` (4096) as a safety net,
  traversing through `ModBlocks.WIRE` blocks (a plain `Block`, no entity, no subclass —
  distinguished by reference equality `block == ModBlocks.WIRE`, not `instanceof`, since there's
  only one wire block and no tiers to future-proof for) and returning the found `RadarBlock`'s
  position the moment one turns up as a neighbor (direct adjacency counts too, no wire needed for
  that case). `isConnectedToRadar` is now just `findRadar(...).isPresent()` — added when
  `MonitorBlockEntity` needed the actual radar position (not just a yes/no), so it made sense to
  have SAM/CIWS's existing boolean check delegate to the same BFS rather than keeping two copies.
  Called once per fire *attempt* inside `SamSiteBlockEntity`/`CiwsBlockEntity.tick` (i.e.
  rate-limited by the same cooldown/accumulator that gates firing, not run unconditionally every
  tick) — an unconnected site just sits idle regardless of ammo. `MonitorBlockEntity` calls
  `findRadar` on its own separate cadence (see below) since it isn't gated by a cooldown at all.
- `block/MonitorBlock.java` + `block/entity/MonitorBlockEntity.java` — a wall-mounted, purely
  passive display: no GUI, no inventory. Placed like a wall torch/sign — `getPlacementState` sets
  `FACING` from the clicked face's opposite (`ctx.getSide().getOpposite()`), falling back to
  facing away from the placer only when clicked on a floor/ceiling block. Every
  `SCAN_INTERVAL_TICKS` (10) it re-validates its link via `WireNetwork.findRadar`, and if linked,
  calls the found `RadarBlockEntity.pulseFromMonitor(...)` (see above) and pushes a
  `MonitorUpdatePayload` (S2C, see below) to `PlayerLookup.tracking(monitor)` — every player who
  can currently see this specific block, not gated behind any screen, which is what makes the
  wall's sweep animate constantly rather than only while someone has the radar's own GUI open.
  Any number of monitor blocks placed adjacent to each other and facing the same way act as one
  big screen — see `client/render/MonitorBlockEntityRenderer` below for how that "flexbox"
  stretching actually works; nothing server-side needs to know a wall is more than one block.
- `screen/SamAmmoScreenHandler.java` / `CiwsAmmoScreenHandler.java` — near-identical single-slot
  (ammo item only, restricted via `Slot.canInsert`) + player-inventory `ScreenHandler`s, same
  client/server dual-constructor shape as `MissileLauncherScreenHandler` (client side builds a
  throwaway `SimpleInventory` from `AmmoScreenData`, server side wraps the real block entity).
  Deliberately **not** unified into one shared base class — this codebase's established
  convention (see the SAM/CIWS/radar scan-loop duplication) is some duplication over a premature
  shared abstraction across block types, even when the shape is this close.
- `network/AmmoScreenData.java` — minimal S2C screen-opening payload for both ammo GUIs: just the
  block's `BlockPos`. No count/capacity fields needed — the ammo slot's `ItemStack` (with its
  count) rides on the normal vanilla slot-sync protocol, same reasoning as the launcher's ammo
  slot not needing one either.
- `registry/ModItemGroups.java` — the mod's own creative-inventory tab (`FabricItemGroup`,
  icon = the ICBM missile), registered **before** `ModItems.register()` in
  `ICBMBasics.onInitialize()` since `ModItems.register()` is what calls
  `ItemGroupEvents.modifyEntriesEvent(ModItemGroups.MAIN_KEY)` to populate it. Every item the mod
  registers lives here now, not scattered across vanilla tabs like `ItemGroups.COMBAT`.
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
  - `MonitorUpdatePayload` (S2C) — pushed by `MonitorBlockEntity` itself (not gated behind any
    screen, unlike `RadarUpdatePayload` above) to every player tracking that specific monitor
    block: the monitor's own pos, its linked radar's pos + detection radius (needed client-side
    to compute blip offsets the same way `RadarScreen` does), and a `RadarContact` list — no log,
    since a wall monitor is a live scope only, not the scrollable impact-log view. Cached
    client-side by monitor pos in `client/render/MonitorRenderData` (see below), independent of
    any open screen.
  - `SubmitDoorCodePayload` (C2S, keyed by the door's lower-half `BlockPos`) — keypad Submit
    button. `ArmoredDoorScreenData` (S2C, via `ExtendedScreenHandlerFactory`) is the initial
    keypad GUI-open payload: pos + whether a code is already set (picks set-mode vs enter-mode
    client-side) — **the code itself is never sent to the client**, only compared server-side.
- `config/ICBMConfig.java` — JSON config at `config/icbmbasics.json`
  (`explosionPower`, `destructionRadius`, `terrainDestruction`, `missileSpeed`,
  `radarTierDetectionRadii[]`, `armorZoneRadius`, `armorZoneMaxBlocks`, `armorTierHits[]`,
  `armorDamageRadius`, `samDetectionRadius`, `samFireCooldownTicks`, `samAccuracy`,
  `samInterceptorSpeed`, `ciwsDetectionRadius`, `ciwsRoundsPerSecond`, `ciwsAccuracy`,
  `ciwsBulletSpeed`). Loaded once into the static `ICBMBasics.CONFIG`. Per-tier values are
  plain arrays indexed by `tier - 1`, clamped in `sanitize()` — adding a tier means appending an
  array entry, not adding a new config field. **Every clamp in `sanitize()` is a hard ceiling on
  what a player-edited `config/icbmbasics.json` value actually does** — raising a default in this
  file without also raising its `sanitize()` clamp silently gets overridden back down on load
  (this bit the user once with `ciwsDetectionRadius`: they set it well above the then-current
  256 clamp and it just got clamped back to 256 with no error). When bumping a config default or
  telling the user to hand-edit the JSON for a bigger value, check the matching clamp line too.

There is **no more world-wide/shared waypoint list** — the old `storage/WaypointStorage.java`
(a `PersistentState` bound to the overworld) was deleted. Every launcher now keeps its own
list, and USB drives carry theirs on the item.

Client-only (`src/client/java/com/example/icbmbasics/client/`):
- `ICBMBasicsClient.java` — `ClientModInitializer`. Registers all three `FlyingItemEntity`
  renderers (missile, SAM interceptor, CIWS bullet — see `render/MissileEntityRenderer` below)
  plus `MonitorBlockEntityRenderer` (via `BlockEntityRendererRegistry`, a different Fabric API
  registry than the entity one), all six `HandledScreens` factories (launcher, USB drive, radar,
  armored door, SAM ammo, CIWS ammo), and the S2C refresh receivers:
  `LauncherWaypointListPayload`/`DriveWaypointListPayload` (waypoint lists), `RadarUpdatePayload`
  (radar contacts/log, matched to the open screen by `BlockPos` since a player could conceivably
  have closed one radar and opened another between pushes), and `MonitorUpdatePayload` (just
  writes into `MonitorRenderData`'s cache — not matched to any screen at all, since it isn't one).
  The two ammo GUIs need no such receiver — their slot syncs the normal vanilla way.
- `render/MissileRenderState.java` + `render/MissileEntityRenderer.java` — a **from-scratch**
  replacement for vanilla's `FlyingItemEntityRenderer`, not a subclass/wrapper of it. Decompiling
  `FlyingItemEntityRenderer.render()` (bytecode via `javap`, same technique as the "javap gotcha"
  below) showed it always does `matrixStack.multiply(camera.orientation)` — a hard camera
  billboard, with no yaw/pitch anywhere on its render state (`FlyingItemEntityRenderState` only
  carries the `ItemRenderState`). So there's no way to opt out of billboarding from outside that
  class; `MissileEntityRenderer` reimplements the same item-rendering path (same
  `ItemModelManager.updateForNonLivingEntity(..., ItemDisplayContext.GROUND, ...)` call) but
  rotates the matrix stack from the entity's own lerped yaw/pitch (`MissileRenderState.yaw/pitch`,
  populated in `updateRenderState` from `entity.getYaw(tickDelta)`/`getPitch(tickDelta)`) instead.
  Used for `MissileEntity`, `SamInterceptorEntity`, and `CiwsBulletEntity` alike — any
  `FlyingItemEntity` that sets its own yaw/pitch (see `MissileEntity.updateRotation()`) gets a
  renderer that actually respects it, just with a different `scale` per entity type.
- `render/MonitorRenderState.java` + `render/MonitorRenderData.java` +
  `render/MonitorBlockEntityRenderer.java` — the monitor wall's "flexbox" stretching, entirely
  client-side. Every frame, each monitor block's `updateRenderState` flood-fills its own
  orthogonally-adjacent neighbors in the wall's own plane (`right`/`up`/`down`, where `right` is
  `facing.rotateYCounterclockwise()` — this happens to equal `up × facing`, worked out by hand
  since `Direction` has no `crossProduct` helper of its own, only `Vec3i` does) that are also a
  `MonitorBlock` with the *same* `FACING`, capped at `MAX_WALL_NODES` (256, same cap-a-BFS
  precedent as `WireNetwork`), to find its own `(row, col)` slot and the group's overall
  `wallWidth`/`wallHeight` — no server-side registry of "which blocks form a wall" exists at all,
  every block just independently re-derives its place in the group each frame. `render()` then
  draws only this block's own slice of one shared virtual raster (`CELLS_PER_BLOCK`, 24 cells per
  block face) — the circle radius, sweep angle, and ring math are all computed in *whole-wall*
  cell coordinates, so they line up seamlessly across however many blocks make up the wall, and a
  block whose slot has no scope circle in it (an irregularly-shaped group) just shows bezel/blank
  instead of gaps. Quads are emitted via
  `OrderedRenderCommandQueue.submitCustom(matrices, RenderLayers.debugQuads(), (entry,
  vertexConsumer) -> ...)` — note **`RenderLayers` (plural)**, not `RenderLayer`, holds the actual
  static factory methods like `debugQuads()`/`solid()`/`cutout()` in this MC version; the plain
  `RenderLayer` class is now just the instance type. Position/color vertices go through
  `vertexConsumer.vertex(matrixEntry, x, y, z).color(r, g, b, a)` — no light/overlay/normal calls
  needed for this untextured debug layer. Sweep animation reads wall-clock time exactly like
  `RadarScreen`'s GUI sweep below, so it animates continuously regardless of any GUI being open —
  that's the whole point of a monitor over the radar's own screen. `MonitorRenderData` is just a
  `Map<BlockPos, Snapshot>` populated by the `MonitorUpdatePayload` receiver in
  `ICBMBasicsClient`, with a `STALE_MILLIS` (3000) cutoff so an unlinked/out-of-range monitor
  goes blank instead of showing a frozen last frame forever.
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
  armor tiers (block + door × 3, escalating iron → diamond → netherite), the armor tool, the SAM
  site + CIWS blocks, their ammo items (`sam_ammo`, `ciws_ammo`), the wire block, the monitor
  block, and the cruise missile. SAM/CIWS/wire/monitor all reuse `missile_launcher_side` as a
  placeholder texture (same deal as radar/armor); the two ammo items and the cruise missile reuse
  the `icbm_missile` item texture rather than getting their own — the cruise missile's only
  meaningful difference from the plain missile item is its tooltip
  (`item.icbmbasics.cruise_missile.tooltip`), not its appearance.

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
- **Radar only scans/pushes updates while its GUI is open** (`RadarBlockEntity.tick` bails if
  `viewers` is empty) — but this is purely a perf optimization for the GUI's own live-update
  push, not a general "is the radar active" signal. **SAM sites/CIWS do *not* depend on a radar's
  GUI being open or its `viewers` set at all** — they only need `WireNetwork.isConnectedToRadar`
  to find *a* `RadarBlock` in the network graph (block presence, not block-entity state), and
  they read missile positions straight from `MissileEntity.getActiveMissiles(world)` the same way
  radar itself does. A radar with nobody watching its scope still "counts" for wiring purposes.
- **SAM/CIWS fire-rate math is fragile to get right — reason about actual ticks-per-shot, not
  just the config field name.** Two real bugs shipped from getting this wrong: (1) an outer
  `SCAN_INTERVAL_TICKS` gate that silently capped the real rate below what the cooldown config
  said (see the CIWS section above), and (2) a plain integer tick-cooldown can't express more
  than 20 fires/sec no matter how low you set it, since a tick is the smallest unit — getting
  meaningfully faster than that requires a fractional accumulator (`CiwsBlockEntity.fireAccumulator`),
  not a smaller cooldown number. Before changing either SAM's or CIWS's fire timing, work out the
  actual ticks/bursts-per-second by hand rather than assuming a config value change alone did
  what it says.
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
  was confirmed after a first guess compiled wrong. Also how `MonitorBlockEntityRenderer` found
  that the client-side render pipeline in this MC version is a bigger rework than usual:
  `BlockEntityRenderer`/`EntityRenderer` render off a separate `*RenderState` object populated in
  `updateRenderState` (not straight off the live entity/block-entity in `render()` itself), custom
  geometry goes through `OrderedRenderCommandQueue.submitCustom(matrices, layer, (entry,
  vertexConsumer) -> ...)` rather than a `VertexConsumerProvider` grabbed directly, and the
  `RenderLayer` static factories (`debugQuads()`, `solid()`, etc.) live on **`RenderLayers`**
  (plural) now, not on `RenderLayer` itself — don't assume any pre-1.21.11 rendering tutorial's
  method locations are still right without checking.
- **A missile's special behavior is a per-item flag on the entity, not a config option or a
  second entity type.** `MissileEntity.cruiseMissile` is set once at spawn by
  `MissileLauncherBlockEntity.tryLaunch` based on whether the consumed ammo was `ICBM_MISSILE` or
  `CRUISE_MISSILE`, and every behavior difference (terrain-hugging cruise, the one-time SAM juke,
  the radar cross-section multiplier) reads that one boolean — there's no `MissileEntityType`
  split and no per-tier config array like radar/armor use. If another missile variant is ever
  added, prefer extending this same one-flag-per-behavior pattern (or promoting `cruiseMissile` to
  an enum if a third variant needs genuinely different values, not just on/off) over introducing a
  new `EntityType`, which would mean duplicating everything `MissileEntity` already does
  (registration, radar/SAM/CIWS scanning, rendering, armor-hit logic) for no real gain.
- **Radar's "only scans while something's watching" gate now has two independent triggers, not
  one.** `RadarBlockEntity.tick` used to bail whenever `viewers` (GUI observers) was empty; it now
  also stays active while `lastMonitorPingTick` is recent (see `MonitorBlockEntity`/
  `pulseFromMonitor` above). Don't reintroduce a check that only looks at `viewers` when touching
  this method — a radar wired to a monitor wall but with no player currently in its own GUI must
  keep scanning, exactly like a radar with the GUI open but no monitors ever did.
