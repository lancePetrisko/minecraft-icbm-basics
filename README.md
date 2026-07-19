# ICBM Basics

A Fabric mod for **Minecraft 1.21.11** (the latest 1.21.x release). Craft missiles, load them
into a launcher block, dial in target coordinates in a GUI, and fire with a redstone signal.
The missile boosts vertically, arcs toward the target, and detonates with a large (configurable)
explosion, a particle shockwave, and optional terrain destruction.

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

## How to use in game

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
   terminal dive onto the target, with a smoke/flame trail and engine sound the whole way.

7. **Impact** — a large explosion (default power 10 vs. TNT's 4), an expanding particle
   shockwave ring, and a carved crater.

## Config

Generated at `config/icbmbasics.json` on first launch:

| Key                  | Default | Meaning                                                            |
|----------------------|---------|--------------------------------------------------------------------|
| `explosionPower`     | `10.0`  | Explosion strength (vanilla TNT is 4.0).                           |
| `destructionRadius`  | `8`     | Extra crater-carving radius on top of the vanilla explosion; `0` disables the extra crater. |
| `terrainDestruction` | `true`  | Master switch for block damage. When `false`, impacts hurt entities but never break blocks. |
| `missileSpeed`       | `1.1`   | Horizontal cruise speed in blocks/tick.                            |

Terrain destruction additionally respects the **`mobGriefing` gamerule** — if it's `false`,
no blocks are broken regardless of config. All flight, targeting, and explosion logic runs
server-side only; clients just receive normal entity tracking, so there's nothing to cheat.

## Project layout

- `src/main/java/.../ICBMBasics.java` — mod init, config load, C2S packet handling
- `block/MissileLauncherBlock.java` — facing, GUI opening, redstone rising-edge trigger
- `block/entity/MissileLauncherBlockEntity.java` — inventory (ammo + USB slot), stored target,
  per-launcher waypoint list, launch logic
- `entity/MissileEntity.java` — boost/cruise/dive flight, trail, impact + crater
- `item/UsbDriveItem.java` — right-click opens the drive's own waypoint-editing GUI
- `screen/MissileLauncherScreenHandler.java` — container (ammo slot, USB slot, player inventory)
- `screen/UsbDriveScreenHandler.java` — slotless container backing the drive's own GUI
- `src/client/java/.../MissileLauncherScreen.java` — GUI with X/Y/Z fields, confirm/use-location
  buttons, and both the launcher's own and the slotted drive's waypoint lists
- `src/client/java/.../UsbDriveScreen.java` — the drive's own X/Y/Z + name + waypoint list GUI
- `network/` — GUI-opening data (S2C) and target/waypoint payloads (C2S/S2C)
- `registry/ModComponents.java` — the `WAYPOINTS` data component storing a drive's list on
  the item stack itself
- `config/ICBMConfig.java` — JSON config at `config/icbmbasics.json`

The missile currently renders as its oversized item sprite (via the vanilla
`FlyingItemEntityRenderer`) — a deliberate, robust placeholder. Swap in a custom
`EntityModel` + renderer in `ICBMBasicsClient` if you want a proper 3D missile. Textures are
simple generated placeholders; replace the PNGs under
`src/main/resources/assets/icbmbasics/textures/` any time.

## License

CC0 — do whatever you like with it.
