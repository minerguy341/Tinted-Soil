#!/usr/bin/env python3
"""
Regenerates Tinted Soil's two generated assets:

  assets/tintedsoil/textures/block/tinted_dirt.png   16x16 neutral soil texture
  assets/tintedsoil/textures/colormap/soil.png       256x256 biome soil colormap

Both are procedural and deterministic, so the repository never has to carry
hand-edited binaries and the colour model below stays the single source of truth.

Run from the repository root:  python3 tools/generate_assets.py

Why the texture is nearly white
-------------------------------
Block tints are multiplicative: the rendered colour is `texel * tint / 255`, so a
texture can only ever be darkened. Vanilla `dirt.png` is a saturated brown, which
means no tint can push it towards pale sand. The generated texture is therefore a
vanilla's own grain, with the brown divided out, that carries the *pattern*
only, and the colormap carries all of the colour -- exactly how vanilla treats
`grass_block_top.png` and `grass.png`.
"""

import os
import struct
import zlib

# --- Output paths ------------------------------------------------------------

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "tintedsoil", "textures")
COLORMAP_PATH = os.path.join(ASSETS, "colormap", "soil.png")

# --- Colour model ------------------------------------------------------------
# Anchor colours are the *rendered* result we want on screen, before the texture
# luminance is divided out. Keep them in this space -- it is the only space that
# is easy to eyeball against a screenshot.

# The mean of vanilla's own dirt.png, read out of the 1.21.1 client jar. Plains is where it
# has to land: it is the biome players read as plain dirt, so matching it there is what
# makes replaced ground indistinguishable from untouched ground across most of a world.
#
# This is the mean of the 249 *chromatic* pixels only, not all 256. Vanilla's other 7 are
# grey pebbles, and those are reproduced literally by the pebble overlay rather than by the
# tint -- so the tinted part has to match the part of vanilla dirt it actually stands in
# for. Matching the all-256 mean here would double-count the pebbles and skew every biome.
VANILLA_DIRT = (134.5542, 95.5341, 65.4137)

# Mean luminance SoilSpriteSource derives from vanilla 1.21.1 dirt.png, confirmed from a
# running client. Mirrored by SoilTextures.DEFAULT_MEAN_LUMINANCE.
VANILLA_MEAN_LUMINANCE = 0.7232
PLAINS_TEMPERATURE, PLAINS_DOWNFALL = 0.8, 0.4

HUMID = (104, 72, 46)        # #684832 - wet soil: jungle, swamp, dark forest
SAND = (206, 183, 138)       # #CEB78A - hot and rainless: desert, badlands, savanna
COLD = (146, 138, 128)       # #928A80 - frozen and washed out: snowy and peak biomes

# Shaping exponents. `SAND_TEMP_EXP`/`SAND_DRY_EXP` keep merely-dryish temperate
# biomes (plains sits at downfall 0.4) from drifting towards sand; only genuinely
# rainless hot biomes get there. `COLD_EXP` keeps mild taiga from going grey.
SAND_TEMP_EXP = 1.5
SAND_DRY_EXP = 3.0
COLD_EXP = 2.0


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def soil_color(temperature, downfall, temperate=None):
    """Rendered soil colour for a clamped biome temperature/downfall pair."""
    t = min(max(temperature, 0.0), 1.0)
    d = min(max(downfall, 0.0), 1.0)

    base = lerp(TEMPERATE_DRY if temperate is None else temperate, HUMID, d)
    sand = (t ** SAND_TEMP_EXP) * ((1.0 - d) ** SAND_DRY_EXP)
    base = lerp(base, SAND, sand)
    cold = (1.0 - t) ** COLD_EXP
    return lerp(base, COLD, cold)


def solve_temperate_dry():
    """
    The dry-temperate anchor is derived, not chosen.

    Plains is not a corner of the model: at downfall 0.4 its colour is already part way
    along the humid lerp, and it picks up a little sand and a little cold on top. So
    setting the anchor to vanilla dirt does *not* make plains render as vanilla dirt --
    that is what this used to do, and plains came out at #876749 instead of #866043.

    Everything downstream of the anchor is a chain of lerps, which is affine in it, so
    exactly one anchor value lands plains on VANILLA_DIRT. Probing the chain with black
    and white recovers that affine map rather than restating its algebra here, which keeps
    the calibration correct if the other anchors or the shaping exponents are ever retuned.
    """
    lo = soil_color(PLAINS_TEMPERATURE, PLAINS_DOWNFALL, temperate=(0.0, 0.0, 0.0))
    hi = soil_color(PLAINS_TEMPERATURE, PLAINS_DOWNFALL, temperate=(255.0, 255.0, 255.0))
    return tuple(255.0 * (VANILLA_DIRT[i] - lo[i]) / (hi[i] - lo[i]) for i in range(3))


TEMPERATE_DRY = solve_temperate_dry()


PNG_MAGIC = bytes((0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))


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

    png = PNG_MAGIC
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(png)


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
SOIL = NS + ":tinted_dirt"
COARSE = NS + ":tinted_coarse_dirt"

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

# The grass block renders in the cutout layer so the side overlay's transparent pixels
# stay transparent. Mipmapped rather than plain cutout, so the fringe does not alias into
# noise at distance -- the same layer vanilla uses for leaves.
#
# NeoForge reads this field straight out of the model. Fabric API has no equivalent (it
# has no model-JSON render type at all), so on Fabric the layer is registered per block in
# TintedSoilFabricClient, which is why every model of the block carries the same value
# here: Fabric applies its choice to all of them regardless.
RENDER_TYPE = "minecraft:cutout_mipped"

# Item icons render a single model, so the grass block's two halves are recombined.
INVENTORY_MODEL = {"tinted_grass_block": "tinted_grass_block_inventory"}


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
        for name, item in (("tinted_dirt", SOIL), ("tinted_coarse_dirt", COARSE)):
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
    soil_texture = NS + ":block/tinted_dirt"

    # Vanilla's dirt model has no tint index, so the cube is spelled out here instead of
    # inheriting cube_all. Tint index 1 is soil throughout the mod.
    for name in ("tinted_dirt", "tinted_coarse_dirt"):
        texture = NS + ":block/" + name
        pebbles = texture + "_pebbles"
        write_json(os.path.join(models, "block", name + ".json"), {
            "parent": "minecraft:block/block",
            "render_type": RENDER_TYPE,
            "textures": {"particle": texture, "all": texture, "pebbles": pebbles},
            "elements": [
                {"from": [0, 0, 0], "to": [16, 16, 16],
                 "faces": cube_faces("#all", tint=1)},
                # No tint index: the pebbles are stone, and stone does not take on the
                # colour of the soil around it. Leaving them untinted is what keeps them
                # the same grey in a swamp as in a desert, exactly as vanilla's are.
                {"from": [0, 0, 0], "to": [16, 16, 16],
                 "faces": cube_faces("#pebbles")},
            ],
        })

    # Vanilla packs the soil cube and the grass overlay into one model. That cannot work
    # here: vanilla's `#side` is the pre-composited grass_block_side, so its overlay only
    # ever adds colour on top of pixels that are already grass, and the block stays in the
    # solid layer. This mod's `#side` is bare soil, so the overlay's transparent pixels are
    # the whole point -- and in the solid layer alpha is ignored, which paints them opaque
    # black over the soil. So the overlay is its own model, carrying the cutout render type,
    # and the blockstate stacks it on top of the soil cube.
    grass_textures = {
        "particle": soil_texture,
        "bottom": soil_texture,
        "side": soil_texture,
        "top": "minecraft:block/grass_block_top",
        "overlay": NS + ":block/grass_overlay_default",
    }
    soil_cube = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
        "down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down", "tintindex": 1},
        "up": {"uv": [0, 0, 16, 16], "texture": "#top", "cullface": "up", "tintindex": 0},
        "north": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "north", "tintindex": 1},
        "south": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "south", "tintindex": 1},
        "west": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "west", "tintindex": 1},
        "east": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "east", "tintindex": 1},
    }}
    grass_overlay = {"from": [0, 0, 0], "to": [16, 16, 16],
                     "faces": cube_faces("#overlay", tint=0,
                                         faces=("north", "south", "west", "east"))}
    # Everywhere the soil texture shows: the four sides and the bottom, but not the top,
    # which is grass rather than soil.
    grass_pebbles = {"from": [0, 0, 0], "to": [16, 16, 16],
                     "faces": cube_faces("#pebbles",
                                         faces=("down", "north", "south", "west", "east"))}
    grass_textures["pebbles"] = NS + ":block/tinted_dirt_pebbles"

    write_json(os.path.join(models, "block", "tinted_grass_block.json"), {
        "parent": "minecraft:block/block",
        "render_type": RENDER_TYPE,
        "textures": grass_textures,
        "elements": [soil_cube, grass_pebbles],
    })

    # Podzol's crust, over tinted dirt. The sides take the band that
    # SoilSpriteSource subtracts out of podzol_side, so the dirt below it stays tinted and
    # keeps blending; the top is podzol all the way across and simply covers the dirt.
    write_json(os.path.join(models, "block", "tinted_podzol_overlay.json"), {
        "parent": "minecraft:block/block",
        "render_type": RENDER_TYPE,
        "textures": {
            "particle": NS + ":block/podzol_overlay_top",
            "side": NS + ":block/podzol_overlay_side",
            "top": NS + ":block/podzol_overlay_top",
        },
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": dict(
            cube_faces("#side", faces=("north", "south", "west", "east")),
            up={"uv": [0, 0, 16, 16], "texture": "#top", "cullface": "up"},
        )}],
    })

    # One fringe model per soil type. A worldgen mod's grass does not always wear vanilla's
    # fringe -- BWG's lush grass has a longer one reaching further down the block -- so the
    # overlay sprite is per type, and SoilSpriteSource fills each from the matching mod's
    # texture when it is installed and from vanilla's when it is not. The model always names
    # our own sprite, so nothing here depends on a mod being present.
    for soil_type in SOIL_TYPE_VALUES:
        write_json(os.path.join(models, "block", "tinted_grass_block_overlay_%s.json" % soil_type), {
            "parent": "minecraft:block/block",
            "render_type": RENDER_TYPE,
            "textures": {"particle": soil_texture,
                         "overlay": NS + ":block/grass_overlay_" + soil_type},
            "elements": [grass_overlay],
        })

    # Blockstate multipart is a world-render mechanism; a held or dropped item renders one
    # model. So the inventory icon needs the two halves recombined into a single model.
    write_json(os.path.join(models, "block", "tinted_grass_block_inventory.json"), {
        "parent": "minecraft:block/block",
        "render_type": RENDER_TYPE,
        "textures": grass_textures,
        "elements": [soil_cube, grass_pebbles, grass_overlay],
    })

    write_json(os.path.join(models, "block", "tinted_grass_block_snow.json"), {
        "parent": "minecraft:block/block",
        "render_type": RENDER_TYPE,
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
    for name in ("tinted_dirt", "tinted_coarse_dirt", "tinted_grass_block"):
        write_json(os.path.join(models, "item", name + ".json"),
                   {"parent": NS + ":block/" + INVENTORY_MODEL.get(name, name)})


def write_blockstates(rotations=4):
    states = os.path.join(RESOURCES, "assets", NS, "blockstates")

    def rotated(model):
        return [{"model": model} if y == 0 else {"model": model, "y": y}
                for y in range(0, 360, 360 // rotations)]

    # Every soil type shares its model -- they differ by tint, not geometry -- but each
    # needs its own blockstate variant so the property is representable.
    # Coarse dirt has no crust of its own, so it stays a plain variants map.
    write_json(os.path.join(states, "tinted_coarse_dirt.json"), {"variants": {
        "soil_type=" + soil_type: rotated(NS + ":block/tinted_coarse_dirt")
        for soil_type in SOIL_TYPE_VALUES
    }})

    # Plain dirt is multipart so podzol can be the soil cube plus a crust laid over it,
    # rather than a separate model or a tint pretending to be one.
    write_json(os.path.join(states, "tinted_dirt.json"), {"multipart": [
        {"apply": rotated(NS + ":block/tinted_dirt")},
        {"when": {"soil_type": "podzol"},
         "apply": {"model": NS + ":block/tinted_podzol_overlay"}},
    ]})

    # Multipart rather than variants, because the soil cube and the grass overlay are now
    # separate models that both have to render. Parts apply in order, so the overlay's
    # quads are emitted after the soil cube's and win the depth tie at the shared surface.
    #
    # `soil_type` is deliberately absent from every condition: it selects a tint, not a
    # model. Multipart also has no exhaustiveness requirement, so unlike the variants map
    # this no longer has to enumerate the property at all.
    #
    # Only the soil cube is randomly rotated, as before. The overlay is a fringe that reads
    # the same at any rotation, and leaving it fixed keeps it from being rotated
    # independently of the cube beneath it.
    write_json(os.path.join(states, "tinted_grass_block.json"), {"multipart": [
        {"when": {"snowy": "false"},
         "apply": rotated(NS + ":block/tinted_grass_block")},
    ] + [
        # The fringe is chosen by soil type, so this part is per value rather than one entry
        # for them all. Multipart conditions are equality tests, which is exactly what is
        # wanted: a block wears the fringe of the grass it stands in for.
        {"when": {"snowy": "false", "soil_type": soil_type},
         "apply": {"model": NS + ":block/tinted_grass_block_overlay_" + soil_type}}
        for soil_type in SOIL_TYPE_VALUES
    ] + [
        {"when": {"snowy": "true"},
         "apply": {"model": NS + ":block/tinted_grass_block_snow"}},
    ]})


def argb(rgb):
    """Constant tints are signed ARGB ints and vanilla always sets alpha to 0xFF."""
    value = 0xFF000000 | (rgb & 0xFFFFFF)
    return value - (1 << 32) if value >= (1 << 31) else value


def write_item_definitions(soil_tint):
    """1.21.4+ item model definitions, which replaced code-registered ItemColor providers."""
    items = os.path.join(RESOURCES, "assets", NS, "items")

    for name in ("tinted_dirt", "tinted_coarse_dirt"):
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
        "model": NS + ":block/" + INVENTORY_MODEL["tinted_grass_block"],
        "tints": [
            {"type": "minecraft:grass", "temperature": 0.5, "downfall": 1.0},
            {"type": "minecraft:constant", "value": argb(soil_tint)},
        ],
    }})


def write_atlas():
    """
    Adds the runtime sprite source to the block atlas.

    <p>The file goes under `assets/minecraft/`, not the mod's own namespace: the atlas
    definition's id is built from the *atlas* id (`minecraft:blocks`), and the game reads it
    with `getResourceStack`, which collects that one id from every loaded pack and
    concatenates their sources. So this appends to vanilla's list rather than replacing it,
    and a copy under `assets/tintedsoil/` is simply never looked at.
    """
    write_json(os.path.join(RESOURCES, "assets", "minecraft", "atlases", "blocks.json"),
               {"sources": [{"type": NS + ":derived_soil"}]})


def write_lang():
    write_json(os.path.join(RESOURCES, "assets", NS, "lang", "en_us.json"), {
        "block." + NS + ".tinted_grass_block": "Tinted Grass Block",
        "block." + NS + ".tinted_dirt": "Tinted Dirt",
        "block." + NS + ".tinted_coarse_dirt": "Tinted Coarse Dirt",
    })


def main():
    # The soil textures are not written here any more. SoilSpriteSource derives them at
    # atlas-stitch time from whatever dirt.png the player's resource packs provide, so the
    # mod follows a pack instead of shipping a frozen copy of one version's art -- and
    # nothing derived from Mojang's textures ends up in the jar.
    #
    # This is the luminance that derivation measures from vanilla's own dirt.png, verified
    # against the running client. The dormant climate colormap below is pre-divided by it,
    # which is exact for vanilla and approximate under a pack that changes dirt's contrast.
    luminance = VANILLA_MEAN_LUMINANCE

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
    write_atlas()
    write_lang()

    print("textures  derived at runtime by SoilSpriteSource (mean luminance %.4f on vanilla)"
          % luminance)
    print("colormap  %s" % os.path.relpath(COLORMAP_PATH, ROOT))
    print("json      tags, loot tables, models, blockstates, item definitions, atlas, lang")
    print("inventory soil tint #%06X" % soil_tint)
    # SoilColormap.java mirrors this for its degraded fallback path; printing it keeps the
    # two in sync by hand rather than by memory.
    print("dry anchor      (%.2f, %.2f, %.2f) -> SoilColormap.TEMPERATE {%d, %d, %d}"
          % (TEMPERATE_DRY + tuple(int(round(v)) for v in TEMPERATE_DRY)))

    # A tint is stored as `rendered / luminance` in 8 bits, so no rendered colour brighter
    # than this can be represented. Vanilla's grain is high-contrast, which pushes the
    # texture mean down and the ceiling with it -- the price of matching vanilla exactly.
    ceiling = luminance * 255.0
    print("ceiling   rendered channels must stay <= %.1f (SoilColormap.MEAN_LUMINANCE %.4f)"
          % (ceiling, luminance))
    for name, colour in (("HUMID", HUMID), ("SAND", SAND), ("COLD", COLD),
                         ("dry anchor", TEMPERATE_DRY)):
        if max(colour) > ceiling:
            print("          !! %s %s exceeds it and is clamped on write"
                  % (name, tuple(int(round(v)) for v in colour)))
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
