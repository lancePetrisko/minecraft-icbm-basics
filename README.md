# ICBM Basics

A Fabric mod for **Minecraft 1.21.11** (the latest 1.21.x release). Craft missiles, load them
into a launcher block, dial in target coordinates in a GUI, and fire with a redstone signal.
Then build up defenses — radar, tiered armored blocks/doors, and automatic ground-to-air
defense (SAM sites and CIWS) — to track and shoot down incoming missiles before they land.

## Building

```bash
./gradlew build
```

The finished jar lands in `build/libs/icbm-basics-1.0.0.jar` (ignore the `-sources` jar).

Requirements: Java 21+. The Gradle wrapper downloads everything else on first run
(Loom 1.17, Minecraft 1.21.11, Yarn `1.21.11+build.6`, Fabric Loader 0.19.3,
Fabric API 0.141.4+1.21.11 — all current as of this writing, see https://fabricmc.net/develop).

To open in an IDE, import the project as a Gradle project (IntelliJ: open `build.gradle`).
Run the game in dev with `./gradlew runClient`.

> **Mappings note:** this project uses Yarn mappings as requested. Be aware that 1.21.11 is
> the *final* Minecraft version with official Yarn support — Fabric has moved to Mojang's
> official mappings from 26.1 onward. If you later port this mod past 1.21.11, you'll need to
> migrate to Mojang mappings first (see the Fabric porting docs at https://docs.fabricmc.net/develop/porting/).

## Installing

1. Install the [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11.
2. Drop the built jar **and** the matching [Fabric API](https://modrinth.com/mod/fabric-api) jar
   into your `.minecraft/mods` folder (client and/or server).

All of the mod's items live in their own **ICBM Basics** creative-inventory tab.

## Offense: missiles & launchers

1. **Craft the launcher** — 2 iron blocks in the middle row (left + right, center empty) and
   3 iron bars along the bottom row:

   ```
   [ ]      [ ]      [ ]
   [Iron Blk][ ]     [Iron Blk]
   [Iron Bars][Iron Bars][Iron Bars]
   ```
   (Like most vanilla recipes, the two-row shape also matches shifted up one row.)

2. **Place the launcher** — it faces you like a dispenser/furnace.

3. **Craft a missile** — bottom row 3 iron ingots, all six remaining slots TNT:

   ```
   [TNT][TNT][TNT]
   [TNT][TNT][TNT]
   [Iron][Iron][Iron]
   ```
   The missile item does nothing on its own — it's ammo.

4. **Load & target** — right-click the launcher to open its GUI. Put a missile in the
   ammo slot, type the target X / Y / Z into the three fields, and press **Confirm Target**.
   You can also press **Use My Location** to fill the fields with your current position, and
   save named waypoints (per-launcher) with the name field + **Save** button.

5. **USB drives (optional)** — craft a USB drive (iron nuggets around a redstone dust, see
   the recipe file) and right-click it in hand to open its own GUI: save named coordinates
   onto the drive itself (manual X/Y/Z or **Use My Location**). Slot the drive into a
   launcher's second slot to see its waypoints listed alongside the launcher's own — click one
   to load it into the coordinate fields. The drive's list is portable between launchers; edit
   it only from the drive's own GUI.

6. **Fire** — give the launcher a redstone signal (flick a lever on it). On the rising edge,
   if a missile is loaded and a target is locked, it launches: vertical boost, cruise, then a
   terminal dive onto the target, with a smoke/flame trail and engine sound the whole way. The
   missile model itself banks to match its flight — vertical on boost, flat on cruise, angled
   into the dive — instead of just billboarding to the camera.

7. **Impact** — a large explosion (default power 10 vs. TNT's 4), an expanding particle
   shockwave ring, and a carved crater.

## Defense: radar, armor, and ground-to-air

### Radar

Craft a Radar block (iron ingots/blocks, glass panes, redstone — see the recipe file) and
place it anywhere; it's non-directional. Right-click to open its scope GUI: a circular scanline
display with a rotating sweep, range rings, and blips for every missile within its detection
radius. Missiles caught very young (still on the pad) are tracked as **outgoing** (cyan) across
the whole map until they resolve; anything else shows as **incoming** (red) only while actually
in range. A scrollable log below the scope records where tracked missiles impacted.

### Armored blocks & doors

Three tiers each of armored blocks and matching codelock doors (iron → diamond → netherite
materials, see the recipe files), absorbing 2 / 5 / 10 missile hits respectively before
breaking — ordinary explosions and even the missile's own crater-carving can't touch them, only
a direct missile hit counts. Damage shows as a visible 0-3 stage on the block.

Placement is capped by an **armor zone**: up to 128 armored blocks/doors within 30 blocks of a
zone's anchor point (doors count too). Placing further away starts a new zone. Craft the **Armor
Zone Tool** (iron ingots around a compass) and right-click any block to re-anchor the nearest
zone there — handy if you want to relocate or expand your base's armor budget.

Armored doors add a numeric keypad codelock (right-click to set/enter a code in a small GUI) and
ignore redstone entirely, so a lever can't bypass the lock.

### SAM sites & CIWS

Two automatic, no-aiming-required defenses — place them and they defend themselves:

- **SAM Site** — long detection range, slow reload, launches a homing interceptor rocket at the
  nearest qualifying missile. About **60% accurate** per shot.
- **CIWS** — far longer range than the SAM site (200 blocks by default), fires 50 bursts a
  second — close to a real Phalanx's ~75 rounds/sec. Each burst spawns one small flying tracer
  round (a little gray-concrete "bullet") aimed at a computed firing solution: it leads a
  moving missile based on its current velocity and a tracer-speed estimate, aiming where the
  missile *will be* rather than where it currently is, the way a real fire-control computer
  would — the round only resolves its hit once it visually arrives, not the instant it's fired.
  Trades accuracy for that range — only about **30% accurate** per burst.

Both ignore missiles that are still on their own launch pad (same rule radar uses to tell your
own outgoing missile from an incoming one), and multiple SAM sites coordinate automatically —
once one site fires on a missile, every other SAM site skips that missile until the interceptor
resolves, so you never waste two rockets on the same target.

**Both need ammo** — SAM sites take `SAM Interceptor Rocket` items, CIWS take `CIWS Ammo Belt`
items (see the recipe files), one consumed per shot/burst. Right-click either block to open a
small GUI showing the ammo slot and a live "Ammo: X / Y" count, or just pipe items in with a
hopper — both are ordinary single-slot inventories under the hood.

**Both also need to be wired to a Radar.** A SAM site or CIWS with ammo but no path to a radar
just sits idle. Craft **Signal Wire** (copper ingots around a redstone dust, see the recipe
file) and run a chain of it from the SAM/CIWS to any radar block — placing the defense block
directly next to a radar works too, no wire needed for that. The connection is checked as a
simple block-adjacency network (not redstone-style signal strength), so the wire run can be any
length.

None of the ground-to-air blocks (or the wire) have real art yet — they currently reuse the
launcher's side texture as a placeholder.

## Config

Generated at `config/icbmbasics.json` on first launch:

| Key                       | Default   | Meaning                                                              |
|---------------------------|-----------|------------------------------------------------------------------------|
| `explosionPower`          | `10.0`    | Explosion strength (vanilla TNT is 4.0).                             |
| `destructionRadius`       | `8`       | Extra crater-carving radius on top of the vanilla explosion; `0` disables the extra crater. |
| `terrainDestruction`      | `true`    | Master switch for block damage. When `false`, impacts hurt entities but never break blocks. |
| `missileSpeed`            | `1.1`     | Horizontal cruise speed in blocks/tick.                              |
| `radarTierDetectionRadii` | `[96]`    | Detection radius (blocks) per radar tier, index 0 = tier 1.          |
| `armorZoneRadius`         | `30`      | How close (blocks) two placements must be to count as the same armor zone. |
| `armorZoneMaxBlocks`      | `128`     | Max armored blocks/doors allowed in a single zone.                   |
| `armorTierHits`           | `[2,5,10]`| Missile hits absorbed per armor tier before breaking.                |
| `armorDamageRadius`       | `6`       | How close a missile impact must be to damage an armored block/door.  |
| `samDetectionRadius`      | `80`      | SAM site detection/engagement radius (blocks).                       |
| `samFireCooldownTicks`    | `60`      | Ticks between SAM interceptor launches (one at a time per site).     |
| `samAccuracy`             | `0.6`     | Chance (0-1) a fired SAM interceptor destroys its target.            |
| `samInterceptorSpeed`     | `1.6`     | Cruise speed of a SAM interceptor in blocks/tick.                    |
| `ciwsDetectionRadius`     | `200`     | CIWS detection/engagement radius (blocks).                           |
| `ciwsRoundsPerSecond`     | `50.0`    | CIWS bursts per second (fractional - can exceed the 20/sec a tick-cooldown would cap out at). |
| `ciwsAccuracy`            | `0.3`     | Chance (0-1) a single CIWS burst destroys its target.                |
| `ciwsBulletSpeed`         | `4.0`     | Tracer speed (blocks/tick) used to compute the CIWS's lead on a moving target. |

Terrain destruction additionally respects the **`mobGriefing` gamerule** — if it's `false`,
no blocks are broken regardless of config. All flight, targeting, and explosion logic runs
server-side only; clients just receive normal entity tracking, so there's nothing to cheat.

## Project layout

- `src/main/java/.../ICBMBasics.java` — mod init, config load, C2S packet handling
- `block/MissileLauncherBlock.java` — facing, GUI opening, redstone rising-edge trigger
- `block/entity/MissileLauncherBlockEntity.java` — inventory (ammo + USB slot), stored target,
  per-launcher waypoint list, launch logic
- `block/RadarBlock.java` + `block/entity/RadarBlockEntity.java` — scans in-flight missiles,
  tracks outgoing vs. incoming, pushes contacts/impact log to viewers
- `block/ArmoredBlock.java` / `ArmoredDoorBlock.java` + their block entities — tiered blast
  resistance, hit-count damage stages, codelock (doors), gated by `storage/ArmorZoneStorage.java`
- `block/SamSiteBlock.java` / `CiwsBlock.java` + their block entities — automatic ground-to-air
  defense, ammo inventories, ammo GUIs, gated on `block/WireNetwork.java` finding a radar
- `block/WireNetwork.java` — capped BFS over `WIRE` blocks, the connectivity check SAM/CIWS need
- `entity/MissileEntity.java` — boost/cruise/dive flight, trail, impact + crater
- `entity/SamInterceptorEntity.java` — homing interceptor fired by SAM sites
- `entity/CiwsBulletEntity.java` — cosmetic tracer round fired by a CIWS burst; straight
  ballistic flight, resolves the hit on arrival rather than instantly at the muzzle
- `item/UsbDriveItem.java` — right-click opens the drive's own waypoint-editing GUI
- `item/ArmorToolItem.java` — right-click re-anchors the nearest armor zone
- `screen/` — every GUI's container logic (launcher, USB drive, radar, armored door, SAM/CIWS ammo)
- `src/client/java/.../` — the matching `HandledScreen`s, plus `MissileEntityRenderer` (orients
  the flying-item model along its own flight path instead of billboarding to the camera)
- `network/` — GUI-opening data (S2C) and target/waypoint/code payloads (C2S/S2C)
- `registry/ModComponents.java` — the `WAYPOINTS` data component storing a drive's list on
  the item stack itself
- `registry/ModItemGroups.java` — the mod's own creative-inventory tab
- `storage/ArmorZoneStorage.java` — per-world `PersistentState` tracking armor zone placement budgets
- `config/ICBMConfig.java` — JSON config at `config/icbmbasics.json`

The missile (and SAM interceptor) render as an oversized item sprite via a custom
`MissileEntityRenderer`, rotated to match flight direction — a deliberate, robust placeholder
short of a full 3D model. Textures across the mod are simple generated/reused placeholders;
replace the PNGs under `src/main/resources/assets/icbmbasics/textures/` any time.

## License

CC0 — do whatever you like with it.
