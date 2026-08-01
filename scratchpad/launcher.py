#!/usr/bin/env python3
"""Generator for the missile launcher's 2x2x3 multiblock models + texture.

Everything about the launcher's *look* is authored here, once, in
"structure space": a 32 x 48 x 32 box (units of 1/16 block) covering the whole
2-wide, 3-tall, 2-deep structure, oriented as if FACING=NORTH (front = -Z).

Why a generator instead of hand-written JSON:

  * A vanilla block model may only use coordinates in [-16, 32], i.e. it can
    span 3 blocks on an axis at most *if* it is centred - a 3-block-tall
    structure anchored at its bottom block would need y up to 48 and is simply
    not expressible. So the geometry has to be cut into one model per block
    cell no matter what, and cutting 40-odd boxes into 12 cells by hand, three
    times over (empty / standard / cruise), is not something to do twice.
  * Face culling at the cuts is easy to get wrong by hand: a box that continues
    into the neighbouring cell must NOT emit a face on the cut plane, or every
    cell boundary pays for a pair of hidden coplanar quads.

The texture is generated too, same trick as ciws.py / radar.py: a 32x32 PNG
laid out as a 4x4 grid of 8px patches. uv space is always 0..16, so one patch
is 4 uv units; a face whose uv rect sits inside one patch comes out flat
coloured. One patch (HAZARD) is a real pattern rather than a flat colour.

Run from the repo root:  python3 scratchpad/launcher.py
"""

import json
import os
import shutil

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "icbmbasics")

# Structure extents, in 1/16-block units.
SW, SH, SD = 32, 48, 32          # 2 wide, 3 tall, 2 deep
CELLS_X, CELLS_Y, CELLS_Z = 2, 3, 2

TEXTURE_NAME = "missile_launcher_v2"
TEXTURE_REF = "icbmbasics:block/" + TEXTURE_NAME

# --------------------------------------------------------------------------
# Palette / texture
# --------------------------------------------------------------------------

# (name, column, row, rgb). Column/row index the 4x4 grid of 8px patches.
PALETTE = [
    ("CONCRETE",   0, 0, (0x8C, 0x8A, 0x80)),
    ("CONCRETE_D", 1, 0, (0x60, 0x5F, 0x58)),
    ("SCORCH",     2, 0, (0x1C, 0x1D, 0x1A)),
    ("HAZARD",     3, 0, None),                  # patterned, see below
    ("OLIVE",      0, 1, (0x4A, 0x52, 0x38)),
    ("OLIVE_D",    1, 1, (0x34, 0x3A, 0x27)),
    ("OLIVE_L",    2, 1, (0x62, 0x6B, 0x48)),
    ("STEEL",      3, 1, (0x6B, 0x70, 0x78)),
    ("STEEL_D",    0, 2, (0x43, 0x47, 0x4C)),
    ("GRATE",      1, 2, None),                  # patterned, see below
    ("RED",        2, 2, (0x8E, 0x2B, 0x22)),
    ("NOZZLE",     3, 2, (0x6E, 0x5A, 0x34)),
    ("BODY",       0, 3, (0xC9, 0xC9, 0xC1)),
    ("BODY_D",     1, 3, (0x9A, 0x9A, 0x93)),
    ("NOSE",       2, 3, (0xA3, 0x32, 0x28)),
    ("ACCENT",     3, 3, (0xB9, 0xBC, 0xB4)),
]

PATCH = {name: (col, row) for name, col, row, _ in PALETTE}


def write_texture(path):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 255))
    px = img.load()
    for name, col, row, rgb in PALETTE:
        x0, y0 = col * 8, row * 8
        if name == "HAZARD":
            # Diagonal yellow/black caution stripes, 8px tile.
            for y in range(8):
                for x in range(8):
                    stripe = ((x + y) % 8) < 4
                    px[x0 + x, y0 + y] = (
                        (0xC8, 0xA3, 0x2B, 255) if stripe else (0x22, 0x22, 0x1E, 255)
                    )
        elif name == "GRATE":
            # Walkway grating: dark base with a lighter cross-hatch.
            for y in range(8):
                for x in range(8):
                    lit = (x % 4 == 0) or (y % 4 == 0)
                    px[x0 + x, y0 + y] = (
                        (0x59, 0x5E, 0x52, 255) if lit else (0x2E, 0x32, 0x2A, 255)
                    )
        else:
            for y in range(8):
                for x in range(8):
                    px[x0 + x, y0 + y] = rgb + (255,)
    img.save(path)


def uv(name):
    """uv rect for a palette patch, inset half a pixel so it can't bleed."""
    col, row = PATCH[name]
    return [col * 4 + 0.25, row * 4 + 0.25, col * 4 + 3.75, row * 4 + 3.75]


# --------------------------------------------------------------------------
# Geometry
# --------------------------------------------------------------------------

FACES = ("down", "up", "north", "south", "west", "east")


def box(name, frm, to, tex, top=None, bottom=None, sides=None):
    """One axis-aligned box in structure space.

    `tex` is the default patch; `top`/`bottom`/`sides` override it per face.
    No element ever carries a rotation - vanilla only accepts +-45/+-22.5 on a
    single axis, and every angled-looking thing here is faked with steps.
    """
    per = {}
    for f in FACES:
        if f == "up" and top:
            per[f] = top
        elif f == "down" and bottom:
            per[f] = bottom
        elif f in ("north", "south", "west", "east") and sides:
            per[f] = sides
        else:
            per[f] = tex
    return {"name": name, "from": list(frm), "to": list(to), "faces": per}


def base_structure():
    """Everything that is there whether or not a missile is loaded."""
    e = []

    # --- launch pad -------------------------------------------------------
    # Concrete apron under the whole 2x2 footprint.
    e.append(box("apron", (0, 0, 0), (32, 3, 32), "CONCRETE",
                 top="CONCRETE", sides="CONCRETE_D"))
    # Deck plates, laid as four slabs around a central flame trench so the
    # trench reads as a hole rather than a painted-on square.
    e.append(box("deck_n", (0, 3, 0), (32, 5, 11), "CONCRETE", top="GRATE"))
    e.append(box("deck_s", (0, 3, 21), (32, 5, 32), "CONCRETE", top="GRATE"))
    e.append(box("deck_w", (0, 3, 11), (11, 5, 21), "CONCRETE", top="GRATE"))
    e.append(box("deck_e", (21, 3, 11), (32, 5, 21), "CONCRETE", top="GRATE"))
    # Trench floor, scorched.
    e.append(box("trench", (11, 1, 11), (21, 3, 21), "SCORCH"))
    # Hazard kerb around the pad edge.
    e.append(box("kerb_n", (0, 5, 0), (32, 6, 2), "HAZARD"))
    e.append(box("kerb_s", (0, 5, 30), (32, 6, 32), "HAZARD"))
    e.append(box("kerb_w", (0, 5, 2), (2, 6, 30), "HAZARD"))
    e.append(box("kerb_e", (30, 5, 2), (32, 6, 30), "HAZARD"))
    # Hold-down arms clamping the missile's base, one per side of the trench.
    e.append(box("clamp_w", (9, 5, 14), (11, 9, 18), "STEEL_D"))
    e.append(box("clamp_e", (21, 5, 14), (23, 9, 18), "STEEL_D"))
    e.append(box("clamp_n", (14, 5, 9), (18, 9, 11), "STEEL_D"))
    e.append(box("clamp_s", (14, 5, 21), (18, 9, 23), "STEEL_D"))

    # --- gantry, all of it along the back (+Z) so the front stays open ----
    # Two full-height corner columns.
    e.append(box("col_w", (0, 3, 27), (4, 46, 31), "OLIVE", sides="OLIVE"))
    e.append(box("col_e", (28, 3, 27), (32, 46, 31), "OLIVE", sides="OLIVE"))
    # Umbilical mast between them.
    e.append(box("mast", (13, 3, 26), (19, 42, 32), "OLIVE_D"))
    e.append(box("mast_cap", (12, 42, 25), (20, 44, 32), "STEEL_D"))
    # Truss: horizontal bands plus short verticals. Diagonals are impossible
    # without element rotation, and stepping them would cost a dozen boxes for
    # a detail nobody reads at this scale.
    # Band heights dodge the platforms (19/33) and their rails (24/38) so the
    # truss stays readable instead of hiding behind a walkway.
    for y in (11, 27, 41):
        e.append(box("band_%d" % y, (4, y, 28), (28, y + 2, 31), "OLIVE_L"))
    for (x0, x1) in ((9, 11), (21, 23)):
        e.append(box("tie_%d_lo" % x0, (x0, 13, 28), (x1, 27, 31), "OLIVE_L"))
        e.append(box("tie_%d_hi" % x0, (x0, 29, 28), (x1, 41, 31), "OLIVE_L"))

    # --- two service platforms -------------------------------------------
    for tag, y in (("p1", 19), ("p2", 33)):
        e.append(box(tag + "_back", (0, y, 24), (32, y + 2, 32), "OLIVE",
                     top="GRATE", bottom="OLIVE_D"))
        e.append(box(tag + "_arm_w", (0, y, 8), (6, y + 2, 24), "OLIVE",
                     top="GRATE", bottom="OLIVE_D"))
        e.append(box(tag + "_arm_e", (26, y, 8), (32, y + 2, 24), "OLIVE",
                     top="GRATE", bottom="OLIVE_D"))
        # Toe boards + top rail around the open inner edge.
        e.append(box(tag + "_toe", (0, y + 2, 23), (32, y + 3, 24), "HAZARD"))
        e.append(box(tag + "_rail", (0, y + 5, 23), (32, y + 6, 24), "HAZARD"))
        e.append(box(tag + "_rail_w", (5, y + 5, 8), (6, y + 6, 24), "HAZARD"))
        e.append(box(tag + "_rail_e", (26, y + 5, 8), (27, y + 6, 24), "HAZARD"))
        e.append(box(tag + "_cap_w", (0, y + 5, 8), (6, y + 6, 9), "HAZARD"))
        e.append(box(tag + "_cap_e", (26, y + 5, 8), (32, y + 6, 9), "HAZARD"))
        e.append(box(tag + "_post_w", (5, y + 2, 8), (6, y + 5, 9), "STEEL_D"))
        e.append(box(tag + "_post_e", (26, y + 2, 8), (27, y + 5, 9), "STEEL_D"))
        # Swing arm reaching in toward the vehicle.
        e.append(box(tag + "_swing", (14, y + 2, 20), (18, y + 4, 25), "STEEL"))

    # --- topside ----------------------------------------------------------
    e.append(box("lmast_w", (1, 46, 28), (3, 48, 30), "STEEL_D"))
    e.append(box("lmast_e", (29, 46, 28), (31, 48, 30), "STEEL_D"))
    e.append(box("beacon", (14, 44, 27), (18, 46, 31), "RED"))
    return e


def standard_missile():
    """ICBM sitting on the pad: fat enough to actually read at 16px."""
    e = []
    e.append(box("m_nozzle", (13, 7, 13), (19, 10, 19), "NOZZLE"))
    e.append(box("m_body", (12, 10, 12), (20, 38, 20), "BODY", sides="BODY"))
    # Bands are inflated half a unit past the body so they never z-fight, and
    # sit in the gaps between the platforms so they aren't hidden by a walkway.
    e.append(box("m_band_lo", (11.5, 24, 11.5), (20.5, 26, 20.5), "NOSE"))
    e.append(box("m_band_hi", (11.5, 30, 11.5), (20.5, 31, 20.5), "BODY_D"))
    e.append(box("m_taper", (13, 38, 13), (19, 42, 19), "BODY"))
    e.append(box("m_nose", (14, 42, 14), (18, 45, 18), "NOSE"))
    e.append(box("m_tip", (15, 45, 15), (17, 46.5, 17), "NOSE"))
    # Four axis-aligned fin plates. Deliberately plates, not rotated blades.
    e.append(box("m_fin_n", (15, 10, 8), (17, 18, 12), "OLIVE_L"))
    e.append(box("m_fin_s", (15, 10, 20), (17, 18, 24), "OLIVE_L"))
    e.append(box("m_fin_w", (8, 10, 15), (12, 18, 17), "OLIVE_L"))
    e.append(box("m_fin_e", (20, 10, 15), (24, 18, 17), "OLIVE_L"))
    return e


def cruise_missile():
    """Cruise round: slimmer, shorter, stub wings and a belly intake."""
    e = []
    e.append(box("c_nozzle", (13.5, 7, 13.5), (18.5, 10, 18.5), "NOZZLE"))
    e.append(box("c_body", (12.5, 10, 12.5), (19.5, 33, 19.5), "BODY", sides="BODY"))
    e.append(box("c_band", (12, 18, 12), (20, 19.5, 20), "OLIVE_L"))
    e.append(box("c_ogive", (13.5, 33, 13.5), (18.5, 36, 18.5), "BODY"))
    e.append(box("c_nose", (14.5, 36, 14.5), (17.5, 38.5, 17.5), "NOSE"))
    e.append(box("c_intake", (13.5, 12, 19.5), (18.5, 17, 21), "STEEL_D"))
    e.append(box("c_wing_w", (6, 22, 15), (12.5, 23.5, 17), "OLIVE"))
    e.append(box("c_wing_e", (19.5, 22, 15), (26, 23.5, 17), "OLIVE"))
    e.append(box("c_fin_n", (15, 10, 8.5), (17, 16, 12.5), "OLIVE_L"))
    e.append(box("c_fin_s", (15, 10, 19.5), (17, 16, 23.5), "OLIVE_L"))
    e.append(box("c_fin_w", (8.5, 10, 15), (12.5, 16, 17), "OLIVE_L"))
    e.append(box("c_fin_e", (19.5, 10, 15), (23.5, 16, 17), "OLIVE_L"))
    return e


LOADS = {
    0: [],                    # LOAD_EMPTY
    1: standard_missile(),    # LOAD_STANDARD
    2: cruise_missile(),      # LOAD_CRUISE
}


# --------------------------------------------------------------------------
# Slicing structure space into one model per block cell
# --------------------------------------------------------------------------

# Which face lives on which bound of a box. A face is dropped when the slice
# cut that bound, because the box carries on into the neighbouring cell and
# the neighbour's own slice will butt right up against it.
FACE_BOUND = {
    "west": (0, "min"), "east": (0, "max"),
    "down": (1, "min"), "up": (1, "max"),
    "north": (2, "min"), "south": (2, "max"),
}

EPS = 1e-6


def slice_into(elements, i, j, k):
    """Clip `elements` to cell (i, j, k), returning model elements in local
    0..16 coordinates."""
    origin = (i * 16, j * 16, k * 16)
    out = []
    for el in elements:
        lo, hi, keep = [], [], True
        for axis in range(3):
            a0 = max(el["from"][axis], origin[axis])
            a1 = min(el["to"][axis], origin[axis] + 16)
            if a1 - a0 <= EPS:
                keep = False
                break
            lo.append(a0)
            hi.append(a1)
        if not keep:
            continue

        faces = {}
        for face, tex in el["faces"].items():
            axis, side = FACE_BOUND[face]
            cut = (lo[axis] > el["from"][axis] + EPS) if side == "min" \
                else (hi[axis] < el["to"][axis] - EPS)
            if cut:
                continue
            faces[face] = {"uv": uv(tex), "texture": "#0"}
        if not faces:
            continue

        out.append({
            "name": el["name"],
            "from": [round(lo[a] - origin[a], 4) for a in range(3)],
            "to": [round(hi[a] - origin[a], 4) for a in range(3)],
            "faces": faces,
        })
    return out


def part_index(i, j, k):
    """Cell -> PART property value. Must match MissileLauncherBlock.java."""
    return j * 4 + k * 2 + i


def block_model(elements):
    return {
        # Thin plates and open railings look blotchy under smooth lighting.
        "ambientocclusion": False,
        "textures": {"0": TEXTURE_REF, "particle": TEXTURE_REF},
        "elements": elements,
    }


def scaled_model(elements, scale, offset):
    """The whole structure squeezed into one model, for the item form."""
    out = []
    for el in elements:
        faces = {f: {"uv": uv(t), "texture": "#0"} for f, t in el["faces"].items()}
        out.append({
            "name": el["name"],
            "from": [round(el["from"][a] * scale + offset[a], 4) for a in range(3)],
            "to": [round(el["to"][a] * scale + offset[a], 4) for a in range(3)],
            "faces": faces,
        })
    return out


# --------------------------------------------------------------------------
# Emit
# --------------------------------------------------------------------------

FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}

OLD_MODELS = [
    "missile_launcher_0", "missile_launcher_1", "missile_launcher_2",
    "missile_launcher_lower_0", "missile_launcher_lower_1", "missile_launcher_lower_2",
    "missile_launcher_upper_0", "missile_launcher_upper_1", "missile_launcher_upper_2",
]


def dump(path, data):
    with open(path, "w") as fh:
        json.dump(data, fh, indent="\t")
        fh.write("\n")


def main():
    blocks_dir = os.path.join(ASSETS, "models", "block")
    items_dir = os.path.join(ASSETS, "models", "item")
    tex_dir = os.path.join(ASSETS, "textures", "block")

    write_texture(os.path.join(tex_dir, TEXTURE_NAME + ".png"))

    for stale in OLD_MODELS:
        p = os.path.join(blocks_dir, stale + ".json")
        if os.path.exists(p):
            os.remove(p)

    base = base_structure()

    # One model per (part, load). Where a cell holds no missile geometry all
    # three loads collapse onto the same file - most of the 36 do.
    model_for = {}
    written = 0
    for j in range(CELLS_Y):
        for k in range(CELLS_Z):
            for i in range(CELLS_X):
                p = part_index(i, j, k)
                empty = slice_into(base, i, j, k)
                key_empty = json.dumps(empty, sort_keys=True)
                names = {}
                for load, extra in LOADS.items():
                    if load != 0 and json.dumps(
                            slice_into(base + extra, i, j, k), sort_keys=True) == key_empty:
                        names[load] = names.get(0, "missile_launcher_p%d" % p)
                        continue
                    name = ("missile_launcher_p%d" % p) if load == 0 \
                        else ("missile_launcher_p%d_%d" % (p, load))
                    dump(os.path.join(blocks_dir, name + ".json"),
                         block_model(slice_into(base + extra, i, j, k)))
                    names[load] = name
                    written += 1
                model_for[p] = names

    # Blockstate: part x facing x loaded. `y` rotates the model about the
    # block centre; the *placement* of each part under that rotation is worked
    # out Java-side (see MissileLauncherBlock.offsetFromCore).
    variants = {}
    for p in range(12):
        for facing, ydeg in FACING_Y.items():
            for load in (0, 1, 2):
                v = {"model": "icbmbasics:block/" + model_for[p][load]}
                if ydeg:
                    v["y"] = ydeg
                variants["part=%d,facing=%s,loaded=%d" % (p, facing, load)] = v
    dump(os.path.join(ASSETS, "blockstates", "missile_launcher.json"),
         {"variants": variants})

    # Item: the whole thing at 0.35 scale, which lands inside vanilla's
    # -16..32 element bounds (32*0.35 = 11.2 wide, 48*0.35 = 16.8 tall).
    scale = 0.35
    offset = (8 - SW * scale / 2, 0.0, 8 - SD * scale / 2)
    item = {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {"0": TEXTURE_REF, "particle": TEXTURE_REF},
        "elements": scaled_model(base + standard_missile(), scale, offset),
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, -2.5, 0],
                    "scale": [0.56, 0.56, 0.56]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0],
                       "scale": [0.25, 0.25, 0.25]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, -2, 0],
                      "scale": [0.5, 0.5, 0.5]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                                      "scale": [0.3, 0.3, 0.3]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0],
                                      "scale": [0.35, 0.35, 0.35]},
        },
    }
    dump(os.path.join(items_dir, "missile_launcher.json"), item)

    print("wrote %d block models, %d blockstate variants" % (written, len(variants)))


if __name__ == "__main__":
    main()
