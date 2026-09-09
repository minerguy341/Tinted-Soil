#!/usr/bin/env python3
"""
Regenerates Tinted Soil's two generated assets:

  assets/tintedsoil/textures/block/tinted_soil.png   16x16 neutral soil texture
  assets/tintedsoil/textures/colormap/soil.png       256x256 biome soil colormap

Both are procedural and deterministic, so the repository never has to carry
hand-edited binaries and the colour model below stays the single source of truth.

Run from the repository root:  python3 tools/generate_assets.py

Why the texture is nearly white
-------------------------------
Block tints are multiplicative: the rendered colour is `texel * tint / 255`, so a
texture can only ever be darkened. Vanilla `dirt.png` is a saturated brown, which
means no tint can push it towards pale sand. The generated texture is therefore a
light, desaturated grain (mean luminance MEAN_LUMINANCE) that carries the *pattern*
only, and the colormap carries all of the colour -- exactly how vanilla treats
`grass_block_top.png` and `grass.png`.
"""

import os
import struct
import zlib

# --- Output paths ------------------------------------------------------------

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "tintedsoil", "textures")
TEXTURE_PATH = os.path.join(ASSETS, "block", "tinted_soil.png")
COARSE_TEXTURE_PATH = os.path.join(ASSETS, "block", "tinted_coarse_soil.png")
COLORMAP_PATH = os.path.join(ASSETS, "colormap", "soil.png")

# --- Colour model ------------------------------------------------------------
# Anchor colours are the *rendered* result we want on screen, before the texture
# luminance is divided out. Keep them in this space -- it is the only space that
# is easy to eyeball against a screenshot.

TEMPERATE = (134, 96, 67)    # #866043 - vanilla dirt, the reference point
HUMID = (104, 72, 46)        # #684832 - wet soil: jungle, swamp, dark forest
SAND = (206, 183, 138)       # #CEB78A - hot and rainless: desert, badlands, savanna
COLD = (146, 138, 128)       # #928A80 - frozen and washed out: snowy and peak biomes

# Mean luminance of the generated texture. Must stay above max(SAND)/255 = 0.808
# or the sandy end of the colormap clips against the 8-bit ceiling.
MEAN_LUMINANCE = 0.86

# Shaping exponents. `SAND_TEMP_EXP`/`SAND_DRY_EXP` keep merely-dryish temperate
# biomes (plains sits at downfall 0.4) from drifting towards sand; only genuinely
# rainless hot biomes get there. `COLD_EXP` keeps mild taiga from going grey.
SAND_TEMP_EXP = 1.5
SAND_DRY_EXP = 3.0
COLD_EXP = 2.0


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def soil_color(temperature, downfall):
    """Rendered soil colour for a clamped biome temperature/downfall pair."""
    t = min(max(temperature, 0.0), 1.0)
    d = min(max(downfall, 0.0), 1.0)

    base = lerp(TEMPERATE, HUMID, d)
    sand = (t ** SAND_TEMP_EXP) * ((1.0 - d) ** SAND_DRY_EXP)
    base = lerp(base, SAND, sand)
    cold = (1.0 - t) ** COLD_EXP
    return lerp(base, COLD, cold)


# --- Minimal PNG writer ------------------------------------------------------

def write_png(path, width, height, rgb_rows):
    """Writes 8-bit RGB PNG. `rgb_rows` is a list of rows of (r, g, b) tuples."""
    raw = bytearray()
    for row in rgb_rows:
        raw.append(0)  # filter type 0 (None)
        for r, g, b in row:
            raw += bytes((r & 0xFF, g & 0xFF, b & 0xFF))

    def chunk(tag, data):
        out = struct.pack(">I", len(data)) + tag + data
        return out + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(png)


# --- Deterministic noise -----------------------------------------------------

class Rng:
    """Small fixed LCG so regenerating never produces a different texture."""

    def __init__(self, seed):
        self.state = seed & 0xFFFFFFFF

    def next(self):
        self.state = (1103515245 * self.state + 12345) & 0x7FFFFFFF
        return self.state

    def unit(self):
        return self.next() / 0x7FFFFFFF


def generate_texture(size=16, seed=0x50494C21, clump=2, levels=6, spread=0.24):
    """
    Light, desaturated granular soil: blocky clumps plus per-pixel speckle.

    `clump` is the width of a coherent patch in pixels and `spread` the peak-to-peak
    luminance range, so raising both gives the chunkier, higher-contrast grain used for
    coarse soil. Whatever the settings, the result is normalised to MEAN_LUMINANCE, which
    is what lets one colormap serve every soil texture.
    """
    rng = Rng(seed)

    cells = max(1, size // clump)
    patches = [[rng.unit() for _ in range(cells)] for _ in range(cells)]
    field = []
    for y in range(size):
        row = []
        for x in range(size):
            patch = patches[min(y // clump, cells - 1)][min(x // clump, cells - 1)]
            row.append(0.55 * patch + 0.45 * rng.unit())
        field.append(row)

    # Quantise to a handful of shades so it reads as pixel art rather than noise.
    quantised = [[round(v * (levels - 1)) / (levels - 1) for v in row] for row in field]

    flat = [v for row in quantised for v in row]
    mean = sum(flat) / len(flat)

    rows = []
    for row in quantised:
        out = []
        for v in row:
            lum = MEAN_LUMINANCE + (v - mean) * spread
            lum = min(max(lum, 0.0), 1.0)
            # Strictly neutral: R == G == B. Any hue baked in here would multiply against
            # the tint and pull every biome's soil toward it, so all colour comes from the
            # colormap and none from the texture.
            value = int(round(lum * 255))
            out.append((value, value, value))
        rows.append(out)
    return rows


def texture_mean_luminance(rows):
    total = 0.0
    count = 0
    for row in rows:
        for r, g, b in row:
            total += (r + g + b) / 3.0 / 255.0
            count += 1
    return total / count


# --- Colormap ----------------------------------------------------------------

def generate_colormap(luminance, size=256):
    """
    Indexed exactly like vanilla `grass.png` (see GrassColor.get):

        x = (1 - temperature) * 255
        y = (1 - temperature * downfall) * 255

    so only the y >= x triangle is reachable. The unreachable half is filled with
    the downfall=1 colour for that temperature, which keeps bilinear-ish sampling
    by other tools well behaved.
    """
    rows = []
    for y in range(size):
        row = []
        for x in range(size):
            t = 1.0 - x / (size - 1)
            p = 1.0 - y / (size - 1)  # p == temperature * downfall
            d = 1.0 if y < x else (p / t if t > 1e-6 else 0.0)
            r, g, b = soil_color(t, d)
            # Divide the texture's luminance back out so `texel * tint` lands on
            # the anchor colours above.
            row.append(tuple(min(255, max(0, int(round(c / luminance)))) for c in (r, g, b)))
        rows.append(row)
    return rows




# =============================================================================
# JSON resources
# =============================================================================
#
# Minecraft renamed several data folders in 1.21 (`tags/blocks` -> `tags/block`,
# `loot_tables` -> `loot_table`) and replaced item colour providers with item model
# definitions in 1.21.4. Rather than keep hand-written copies per layout in the
# repository -- which drift -- everything below is emitted from one definition into
# every layout. Versions simply ignore the directories they do not know about.

import json

RESOURCES = os.path.join(ROOT, "src", "main", "resources")
NS = "tintedsoil"
GRASS = NS + ":tinted_grass_block"
SOIL = NS + ":tinted_soil"
COARSE = NS + ":tinted_coarse_soil"

# Verified against the shipped jars of each mod rather than guessed:
#   biomesoplenty:origin_grass_block   - the only grass/dirt block BOP adds
#   biomeswevegone:lush_*/sandy_dirt/peat - BWG's entries in the minecraft:dirt tag
#
# Coarse dirt and podzol are included as bare soil: neither has a grass overlay, so
# mapping them to the grass block would paint grass over badlands and old-growth taiga
# floors. Folding them in does change two vanilla behaviours, because they stop being
# distinguishable from plain dirt:
#   - tinted grass will now spread onto ground that used to be coarse dirt or podzol,
#     where vanilla grass never could;
#   - mushrooms lose podzol's grow-at-any-light-level rule, since that keys off
#     minecraft:mushroom_grow_block and the replacement is not in it. Adding the
#     replacement to that tag would instead let mushrooms grow on every soil block.
#
# Still excluded: rooted dirt (drops hanging roots), mycelium (spreads, and grows
# mushrooms), mud, and BWG's overgrown/podzol dacite. Add them to these tags with a
# datapack if you would rather have the smoother surface.
REPLACEABLE_GRASS = [
    ("minecraft:grass_block", True),
    ("biomesoplenty:origin_grass_block", False),
    ("biomeswevegone:lush_grass_block", False),
]

REPLACEABLE_SOIL = [
    ("minecraft:dirt", True),
    ("minecraft:podzol", True),
    ("biomeswevegone:lush_dirt", False),
    ("biomeswevegone:sandy_dirt", False),
    ("biomeswevegone:peat", False),
]

# Becomes tinted coarse soil, which grass will not spread onto.
REPLACEABLE_COARSE_SOIL = [
    ("minecraft:coarse_dirt", True),
]

# Which soil type a replaced block records, as a `soil_type` blockstate value. This is
# colour only: the type gives the block a characteristic cast that survives worldgen, and
# SoilTypeBlend blurs it across neighbours so type boundaries fade instead of cutting off.
# "default" has no tag -- it is the fallback, and contributes no colour of its own.
SOIL_TYPES = {
    "podzol": [("minecraft:podzol", True)],
    "lush": [("biomeswevegone:lush_dirt", False), ("biomeswevegone:lush_grass_block", False)],
    "sandy": [("biomeswevegone:sandy_dirt", False)],
    "peat": [("biomeswevegone:peat", False)],
    "origin": [("biomesoplenty:origin_grass_block", False)],
}

# Must match SoilType.java, including order.
SOIL_TYPE_VALUES = ["default", "podzol", "lush", "sandy", "peat", "origin"]

# Vanilla tags each replacement joins, mirroring the vanilla block it stands in for.
# Read out of the 1.21.1 client jar. Joining them is what makes other mods' tag-driven
# data apply to the replacements.
#
# Because coarse soil is its own block it reproduces coarse dirt's tags exactly, including
# armadillo_spawnable_on. Plain tinted soil deliberately mirrors `dirt` only: podzol also
# folds into it, and inheriting podzol's tags would widen them -- foxes would spawn on all
# soil, mushrooms would grow on it everywhere -- rather than preserve behaviour.
VANILLA_TAGS_BY_BLOCK = {
    GRASS: ["dirt", "mineable/shovel", "sniffer_diggable_block", "animals_spawnable_on",
            "foxes_spawnable_on", "frogs_spawnable_on", "parrots_spawnable_on",
            "rabbits_spawnable_on", "wolves_spawnable_on", "valid_spawn"],
    SOIL: ["dirt", "mineable/shovel", "sniffer_diggable_block", "convertable_to_mud"],
    COARSE: ["dirt", "mineable/shovel", "sniffer_diggable_block", "convertable_to_mud",
             "armadillo_spawnable_on", "foxes_spawnable_on", "wolves_spawnable_on"],
}


def vanilla_tags():
    """Inverts VANILLA_TAGS_BY_BLOCK into tag -> blocks, keeping block order stable."""
    out = {}
    for block in (GRASS, SOIL, COARSE):
        for tag in VANILLA_TAGS_BY_BLOCK[block]:
            out.setdefault(tag, []).append(block)
    return out

TAG_DIRS = ["tags/block", "tags/blocks"]        # 1.21+, 1.20.x
LOOT_DIRS = ["loot_table", "loot_tables"]       # 1.21+, 1.20.x


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")


def tag_file(entries):
    values = []
    for block, required in entries:
        values.append(block if required else {"id": block, "required": False})
    return {"replace": False, "values": values}


def write_tags():
    for directory in TAG_DIRS:
        write_json(os.path.join(RESOURCES, "data", NS, directory, "replaceable_grass.json"),
                   tag_file(REPLACEABLE_GRASS))
        write_json(os.path.join(RESOURCES, "data", NS, directory, "replaceable_soil.json"),
                   tag_file(REPLACEABLE_SOIL))
        write_json(os.path.join(RESOURCES, "data", NS, directory, "replaceable_coarse_soil.json"),
                   tag_file(REPLACEABLE_COARSE_SOIL))
        for soil_type, entries in SOIL_TYPES.items():
            write_json(os.path.join(RESOURCES, "data", NS, directory, "soil_type", soil_type + ".json"),
                       tag_file(entries))
        for name, blocks in vanilla_tags().items():
            write_json(os.path.join(RESOURCES, "data", "minecraft", directory, name + ".json"),
                       {"replace": False, "values": blocks})


def silk_touch_condition(modern):
    """The match_tool predicate shape changed in 1.21; each layout gets its own."""
    if modern:
        return {
            "condition": "minecraft:match_tool",
            "predicate": {"predicates": {
                "minecraft:enchantments": [
                    {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}
                ]}},
        }
    return {
        "condition": "minecraft:match_tool",
        "predicate": {"enchantments": [
            {"enchantment": "minecraft:silk_touch", "levels": {"min": 1}}
        ]},
    }


def write_loot_tables():
    for directory in LOOT_DIRS:
        modern = directory == "loot_table"
        base = os.path.join(RESOURCES, "data", NS, directory, "blocks")

        # Bare soil simply drops itself.
        for name, item in (("tinted_soil", SOIL), ("tinted_coarse_soil", COARSE)):
            write_json(os.path.join(base, name + ".json"), {
                "type": "minecraft:block",
                "random_sequence": NS + ":blocks/" + name,
                "pools": [{
                    "rolls": 1.0, "bonus_rolls": 0.0,
                    "entries": [{"type": "minecraft:item", "name": item}],
                    "conditions": [{"condition": "minecraft:survives_explosion"}],
                }],
            })

        # Grass drops soil unless mined with silk touch, exactly like vanilla grass.
        write_json(os.path.join(base, "tinted_grass_block.json"), {
            "type": "minecraft:block",
            "random_sequence": NS + ":blocks/tinted_grass_block",
            "pools": [{
                "rolls": 1.0, "bonus_rolls": 0.0,
                "entries": [{
                    "type": "minecraft:alternatives",
                    "children": [
                        {"type": "minecraft:item", "name": GRASS,
                         "conditions": [silk_touch_condition(modern)]},
                        {"type": "minecraft:item", "name": SOIL,
                         "conditions": [{"condition": "minecraft:survives_explosion"}]},
                    ],
                }],
            }],
        })


def cube_faces(texture, tint=None, cullface=True, faces=None):
    out = {}
    for face in (faces or ("down", "up", "north", "south", "west", "east")):
        entry = {"uv": [0, 0, 16, 16], "texture": texture}
        if cullface:
            entry["cullface"] = face
        if tint is not None:
            entry["tintindex"] = tint
        out[face] = entry
    return out


def write_models():
    models = os.path.join(RESOURCES, "assets", NS, "models")
    soil_texture = NS + ":block/tinted_soil"

    # Vanilla's dirt model has no tint index, so the cube is spelled out here instead of
    # inheriting cube_all. Tint index 1 is soil throughout the mod.
    for name in ("tinted_soil", "tinted_coarse_soil"):
        texture = NS + ":block/" + name
        write_json(os.path.join(models, "block", name + ".json"), {
            "parent": "minecraft:block/block",
            "textures": {"particle": texture, "all": texture},
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16],
                          "faces": cube_faces("#all", tint=1)}],
        })

    # Same two-element structure as minecraft:block/grass_block: an opaque cube plus a
    # grass overlay on the four sides. The difference is that the dirt half is tinted
    # (index 1) instead of being a fixed brown, and the sides use the neutral soil
    # texture rather than the pre-composited grass_block_side.
    write_json(os.path.join(models, "block", "tinted_grass_block.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": soil_texture,
            "bottom": soil_texture,
            "side": soil_texture,
            "top": "minecraft:block/grass_block_top",
            "overlay": "minecraft:block/grass_block_side_overlay",
        },
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
                "down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down", "tintindex": 1},
                "up": {"uv": [0, 0, 16, 16], "texture": "#top", "cullface": "up", "tintindex": 0},
                "north": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "north", "tintindex": 1},
                "south": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "south", "tintindex": 1},
                "west": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "west", "tintindex": 1},
                "east": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "east", "tintindex": 1},
            }},
            {"from": [0, 0, 0], "to": [16, 16, 16],
             "faces": cube_faces("#overlay", tint=0, faces=("north", "south", "west", "east"))},
        ],
    })

    write_json(os.path.join(models, "block", "tinted_grass_block_snow.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": soil_texture,
            "bottom": soil_texture,
            "side": "minecraft:block/grass_block_snow",
            "top": "minecraft:block/grass_block_top",
        },
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
            "down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down", "tintindex": 1},
            "up": {"uv": [0, 0, 16, 16], "texture": "#top", "cullface": "up", "tintindex": 0},
            "north": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "north"},
            "south": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "south"},
            "west": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "west"},
            "east": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "east"},
        }}],
    })

    # Pre-1.21.4 inventory model. Newer versions read assets/<ns>/items/ instead but still
    # resolve this path, so both can ship side by side.
    for name in ("tinted_soil", "tinted_coarse_soil", "tinted_grass_block"):
        write_json(os.path.join(models, "item", name + ".json"),
                   {"parent": NS + ":block/" + name})


def write_blockstates(rotations=4):
    states = os.path.join(RESOURCES, "assets", NS, "blockstates")

    def rotated(model):
        return [{"model": model} if y == 0 else {"model": model, "y": y}
                for y in range(0, 360, 360 // rotations)]

    # Every soil type shares its model -- they differ by tint, not geometry -- but each
    # needs its own blockstate variant so the property is representable.
    for name in ("tinted_soil", "tinted_coarse_soil"):
        write_json(os.path.join(states, name + ".json"), {"variants": {
            "soil_type=" + soil_type: rotated(NS + ":block/" + name)
            for soil_type in SOIL_TYPE_VALUES
        }})

    variants = {}
    for soil_type in SOIL_TYPE_VALUES:
        variants["snowy=false,soil_type=" + soil_type] = rotated(NS + ":block/tinted_grass_block")
        variants["snowy=true,soil_type=" + soil_type] = {"model": NS + ":block/tinted_grass_block_snow"}
    write_json(os.path.join(states, "tinted_grass_block.json"), {"variants": variants})


def argb(rgb):
    """Constant tints are signed ARGB ints and vanilla always sets alpha to 0xFF."""
    value = 0xFF000000 | (rgb & 0xFFFFFF)
    return value - (1 << 32) if value >= (1 << 31) else value


def write_item_definitions(soil_tint):
    """1.21.4+ item model definitions, which replaced code-registered ItemColor providers."""
    items = os.path.join(RESOURCES, "assets", NS, "items")

    for name in ("tinted_soil", "tinted_coarse_soil"):
        write_json(os.path.join(items, name + ".json"), {"model": {
            "type": "minecraft:model",
            "model": NS + ":block/" + name,
            # Index 0 is unused by the soil models but has to exist to reach index 1.
            "tints": [
                {"type": "minecraft:constant", "value": argb(0xFFFFFF)},
                {"type": "minecraft:constant", "value": argb(soil_tint)},
            ],
        }})

    write_json(os.path.join(items, "tinted_grass_block.json"), {"model": {
        "type": "minecraft:model",
        "model": NS + ":block/tinted_grass_block",
        "tints": [
            {"type": "minecraft:grass", "temperature": 0.5, "downfall": 1.0},
            {"type": "minecraft:constant", "value": argb(soil_tint)},
        ],
    }})


def write_lang():
    write_json(os.path.join(RESOURCES, "assets", NS, "lang", "en_us.json"), {
        "block." + NS + ".tinted_grass_block": "Tinted Grass Block",
        "block." + NS + ".tinted_soil": "Tinted Soil",
        "block." + NS + ".tinted_coarse_soil": "Tinted Coarse Soil",
    })


def main():
    texture = generate_texture()
    luminance = texture_mean_luminance(texture)
    write_png(TEXTURE_PATH, len(texture[0]), len(texture), texture)

    # Chunkier, higher-contrast grain, normalised to the same mean so it shares the colormap.
    coarse = generate_texture(seed=0x43524150, clump=4, levels=4, spread=0.34)
    write_png(COARSE_TEXTURE_PATH, len(coarse[0]), len(coarse), coarse)

    colormap = generate_colormap(luminance)
    write_png(COLORMAP_PATH, len(colormap[0]), len(colormap), colormap)

    # Inventory icons have no biome, so they use the plains entry, the same value
    # SoilColormap.defaultColor() resolves to at runtime.
    plains = colormap[int((1.0 - 0.8 * 0.4) * 255)][int((1.0 - 0.8) * 255)]
    soil_tint = (plains[0] << 16) | (plains[1] << 8) | plains[2]

    write_tags()
    write_loot_tables()
    write_models()
    write_blockstates()
    write_item_definitions(soil_tint)
    write_lang()

    print("texture   %s (mean luminance %.4f)" % (os.path.relpath(TEXTURE_PATH, ROOT), luminance))
    print("texture   %s (mean luminance %.4f)"
          % (os.path.relpath(COARSE_TEXTURE_PATH, ROOT), texture_mean_luminance(coarse)))
    print("colormap  %s" % os.path.relpath(COLORMAP_PATH, ROOT))
    print("json      tags, loot tables, models, blockstates, item definitions, lang")
    print("inventory soil tint #%06X" % soil_tint)
    print()
    print("Sanity check (rendered colour = colormap * texture luminance):")
    for name, t, d in (
        ("plains", 0.8, 0.4),
        ("forest", 0.7, 0.8),
        ("jungle", 0.95, 0.9),
        ("swamp", 0.8, 0.9),
        ("desert/savanna/badlands", 1.0, 0.0),
        ("taiga", 0.25, 0.8),
        ("snowy plains", 0.0, 0.5),
    ):
        r, g, b = soil_color(t, d)
        print("  %-26s T=%.2f D=%.2f  #%02X%02X%02X" % (name, t, d, round(r), round(g), round(b)))


if __name__ == "__main__":
    main()
