#!/usr/bin/env python3
"""
Regenerates every JSON resource Tinted Soil ships, plus the one PNG it still has:

  assets/tintedsoil/soils.json                   which blocks are soil, and what to do
  assets/tintedsoil/models/block/soil/**.json     one replacement model per soil model
  assets/minecraft/atlases/blocks.json            hooks the runtime sprite source in
  assets/tintedsoil/textures/colormap/soil.png    256x256 climate colormap (dormant)

All of it is deterministic, so nothing in the repository is hand-edited and the
definitions below stay the single source of truth.

Run from the repository root:  python3 tools/generate_assets.py

Why there is no soil texture here
---------------------------------
Block tints are multiplicative: the rendered colour is `texel * tint / 255`, so a
tinted texture can only ever be darkened, and any hue left in it would pull every
soil colour towards itself. The greyscale each soil is tinted on is therefore
derived at runtime by SoilSpriteSource, from whatever textures the player's
resource packs actually supply -- vanilla's dirt, BWG's peat, Bare Bones' dirt --
rather than shipped as a frozen copy of one version's art.

Deriving per texture is also what removes the old brightness ceiling. A tint is
stored as `rendered / mean luminance` in eight bits, so a colour brighter than the
texture it multiplies cannot be represented. Each soil is now tinted on a greyscale
made from its own texture, so the colour it needs is always the one that texture
can reach: BWG's sandy #D9CA9D comes out at tint #FCEBB7 instead of being clipped.
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
# Tinted Soil does not add blocks. It retints the soil blocks that are already
# there, by replacing the *models* they resolve to at load time. Everything the
# runtime needs to do that is emitted below into one definition file plus one
# replacement model per model being replaced.
#
# Keeping it in resources rather than in code is what lets a resource pack extend
# it: `soils.json` is read with `getResourceStack`, so a pack that ships its own
# copy adds soils rather than replacing the list. It has to be a *client* resource
# and not a datapack tag, because models are loaded and the atlas is stitched long
# before any block tag exists on the client.

import json

RESOURCES = os.path.join(ROOT, "src", "main", "resources")
NS = "tintedsoil"

MC = "minecraft"
BWG = "biomeswevegone"
BOP = "biomesoplenty"


def tex(namespace, name):
    return "%s:block/%s" % (namespace, name)


def model(namespace, name):
    return "%s:block/%s" % (namespace, name)


# --- What counts as soil -----------------------------------------------------
#
# Each entry names one block, the models it resolves to, and the textures on those
# models that are soil rather than something growing on it. Model and texture ids
# are read out of each mod's own jar, not guessed:
#
#   minecraft   1.21.1 client jar
#   BWG         oh-the-biomes-weve-gone 2.6.0-Fabric
#   BOP         BiomesOPlenty-fabric-1.21.1-21.1.0.14
#
# `colour` is the texture whose average colour the block votes with in the blend,
# and whose mean luminance divides the tint. It is the block's *soil* texture --
# a grass block votes with the dirt under it, not with its own grassy side, so a
# grass block and the dirt beside it agree on the colour of the ground.
#
# Deliberately excluded, as before: rooted dirt (drops hanging roots), mycelium
# (spreads, and grows mushrooms), mud, and BWG's overgrown/podzol dacite.


def cube(model_id, all_texture, particle=None):
    return dict(id=model_id, shape="cube", all=all_texture,
                particle=particle or all_texture)


def column(model_id, bottom, side, top, side_is_soil=True, top_tint=None, particle=None):
    return dict(id=model_id, shape="column", bottom=bottom, side=side, top=top,
                side_is_soil=side_is_soil, top_tint=top_tint,
                particle=particle or bottom)


def grass(model_id, bottom, side, top, overlay, particle=None):
    return dict(id=model_id, shape="grass", bottom=bottom, side=side, top=top,
                overlay=overlay, particle=particle or bottom)


SOILS = [
    dict(block=MC + ":dirt", colour=tex(MC, "dirt"), models=[
        cube(model(MC, "dirt"), tex(MC, "dirt")),
    ]),
    dict(block=MC + ":coarse_dirt", colour=tex(MC, "coarse_dirt"), models=[
        cube(model(MC, "coarse_dirt"), tex(MC, "coarse_dirt")),
    ]),
    # Podzol is dirt wearing a crust: `podzol_side.png` is `dirt.png` byte for byte
    # below its top few rows. So its body is plain dirt -- which is what keeps it
    # blending with the ordinary ground it is made of -- and the crust is subtracted
    # out into a sprite of its own and laid over the top, untinted, exactly as the
    # grass overlay is. The bottom face is bare dirt in vanilla too.
    dict(block=MC + ":podzol", colour=tex(MC, "dirt"), models=[
        dict(id=model(MC, "podzol"), shape="crust", all=tex(MC, "dirt"),
             crust_side=NS + ":soil/minecraft/block/podzol_side_crust",
             crust_top=tex(MC, "podzol_top"), particle=tex(MC, "podzol_side")),
    ]),
    # `grass_block_snow` is shared: podzol's snowy=true variant resolves to it too.
    # Both blocks vote with dirt, so one replacement serves both.
    dict(block=MC + ":grass_block", colour=tex(MC, "dirt"), models=[
        grass(model(MC, "grass_block"), bottom=tex(MC, "dirt"),
              side=tex(MC, "grass_block_side"), top=tex(MC, "grass_block_top"),
              overlay=tex(MC, "grass_block_side_overlay")),
        column(model(MC, "grass_block_snow"), bottom=tex(MC, "dirt"),
               side=tex(MC, "grass_block_snow"), top=tex(MC, "grass_block_top"),
               side_is_soil=False, top_tint=0),
    ]),

    dict(block=BWG + ":lush_dirt", colour=tex(BWG, "lush_dirt"), models=[
        cube(model(BWG, "lush_dirt"), tex(BWG, "lush_dirt")),
    ]),
    dict(block=BWG + ":sandy_dirt", colour=tex(BWG, "sandy_dirt"), models=[
        cube(model(BWG, "sandy_dirt"), tex(BWG, "sandy_dirt")),
    ]),
    dict(block=BWG + ":peat", colour=tex(BWG, "peat"), models=[
        cube(model(BWG, "peat"), tex(BWG, "peat")),
    ]),
    # BWG's lush grass wears a longer fringe than vanilla's, reaching further down
    # the block. Naming its own overlay here is what keeps that difference; the old
    # design had to copy each mod's fringe into a sprite of its own because every
    # replaced block shared one model.
    dict(block=BWG + ":lush_grass_block", colour=tex(BWG, "lush_dirt"), models=[
        grass(model(BWG, "lush_grass_block"), bottom=tex(BWG, "lush_dirt"),
              side=tex(BWG, "lush_grass_block_side"),
              top=tex(BWG, "lush_grass_block_top"),
              overlay=tex(BWG, "lush_grass_block_side_overlay")),
        column(model(BWG, "lush_grass_block_snowy"), bottom=tex(BWG, "lush_dirt"),
               side=tex(BWG, "lush_grass_block_snow_side"),
               top=tex(BWG, "lush_grass_block_top"), side_is_soil=False, top_tint=0),
    ]),

    # BOP paints its grass on rather than overlaying it: `origin_grass_block_side`
    # is vanilla's grass_block_side with a green fringe painted over, and the model
    # carries no tint index at all. Nothing special is needed -- the fringe is far
    # enough off the soil hue that it separates out with the pebbles and is drawn
    # back untinted, which is exactly how BOP renders it. The top stays untinted too.
    dict(block=BOP + ":origin_grass_block", colour=tex(MC, "dirt"), models=[
        column(model(BOP, "origin_grass_block"), bottom=tex(MC, "dirt"),
               side=tex(BOP, "origin_grass_block_side"),
               top=tex(BOP, "origin_grass_block_top")),
        column(model(BOP, "origin_grass_block_snow"), bottom=tex(MC, "dirt"),
               side=tex(BOP, "origin_grass_block_snow"),
               top=tex(BOP, "origin_grass_block_top"), side_is_soil=False),
    ]),
]

# Sprites derived by subtracting one texture from another, for a soil that composites
# its overlay into the base texture instead of shipping one. Podzol is the only such
# block in vanilla; keeping a pixel only where the two differ recovers the crust.
OVERLAYS = [
    dict(sprite=NS + ":soil/minecraft/block/podzol_side_crust",
         over=tex(MC, "podzol_side"), under=tex(MC, "dirt")),
]

# Tint index 0 is grass and 1 is soil, in every model this emits. Vanilla already
# uses 0 for grass, so leaving it there keeps whatever provider a mod registered for
# its own grass block working untouched -- the mod only ever claims index 1.
GRASS_TINT = 0
SOIL_TINT = 1


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")


def sprite_ids(texture):
    """The derived sprite pair for a texture, named after the texture it came from."""
    namespace, _, path = texture.partition(":")
    base = "%s:soil/%s/%s" % (NS, namespace, path)
    return {"soil": base, "pebbles": base + "_pebbles"}


def soil_textures():
    """Every texture that has to be split into a greyscale and a pebble sprite."""
    found = []
    for soil in SOILS:
        for texture in [soil["colour"]] + [t for m in soil["models"]
                                           for t in model_soil_textures(m)]:
            if texture not in found:
                found.append(texture)
    return found


def model_soil_textures(entry):
    """The textures of one model that are soil, and so get replaced by a greyscale."""
    shape = entry["shape"]
    if shape in ("cube", "crust"):
        return [entry["all"]]
    if shape == "column":
        return [entry["bottom"]] + ([entry["side"]] if entry["side_is_soil"] else [])
    if shape == "grass":
        return [entry["bottom"], entry["side"]]
    raise ValueError(shape)


def replacement_id(entry):
    """Where the replacement for a model lives, keyed by the model it stands in for."""
    namespace, _, path = entry["id"].partition(":")
    return "%s:block/soil/%s/%s" % (NS, namespace, path.split("/")[-1])


def face(texture, direction, tint=None):
    out = {"uv": [0, 0, 16, 16], "texture": texture, "cullface": direction}
    if tint is not None:
        out["tintindex"] = tint
    return out


def element(faces):
    return {"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}


SIDES = ("north", "south", "west", "east")
ALL_FACES = ("down", "up") + SIDES


def build_model(entry):
    """
    Rebuilds one soil model out of derived sprites.

    Geometry mirrors the model being stood in for, face for face, so a block keeps
    the shape it had. Only two things change: a soil face samples the greyscale
    sprite and gains tint index 1, and a second, untinted element draws that
    texture's pebbles back on top of it.
    """
    textures = {"particle": entry["particle"]}
    elements = []
    shape = entry["shape"]

    def soil(slot, texture):
        sprites = sprite_ids(texture)
        textures[slot] = sprites["soil"]
        textures[slot + "_pebbles"] = sprites["pebbles"]

    if shape in ("cube", "crust"):
        soil("all", entry["all"])
        elements.append(element({d: face("#all", d, SOIL_TINT) for d in ALL_FACES}))
        # No tint index: pebbles are stone, and stone does not take on the colour of
        # the soil around it. Untinted is what keeps them the same grey in a swamp as
        # in a desert -- which is what vanilla's own are.
        elements.append(element({d: face("#all_pebbles", d) for d in ALL_FACES}))
        if shape == "crust":
            textures["crust_side"] = entry["crust_side"]
            textures["crust_top"] = entry["crust_top"]
            crust = {d: face("#crust_side", d) for d in SIDES}
            crust["up"] = face("#crust_top", "up")
            elements.append(element(crust))
        return textures, elements

    soil("bottom", entry["bottom"])
    body = {"down": face("#bottom", "down", SOIL_TINT)}
    pebbles = {"down": face("#bottom_pebbles", "down")}

    if shape == "grass" or entry["side_is_soil"]:
        soil("side", entry["side"])
        for d in SIDES:
            body[d] = face("#side", d, SOIL_TINT)
            pebbles[d] = face("#side_pebbles", d)
    else:
        # Snow, and anything else that is not soil, is left exactly as it was.
        textures["side"] = entry["side"]
        for d in SIDES:
            body[d] = face("#side", d)

    textures["top"] = entry["top"]
    top_tint = GRASS_TINT if shape == "grass" else entry["top_tint"]
    body["up"] = face("#top", "up", top_tint)

    elements.append(element(body))
    elements.append(element(pebbles))

    if shape == "grass":
        textures["overlay"] = entry["overlay"]
        elements.append(element({d: face("#overlay", d, GRASS_TINT) for d in SIDES}))

    return textures, elements


def write_models():
    """One replacement model per model being replaced."""
    for soil in SOILS:
        for entry in soil["models"]:
            textures, elements = build_model(entry)
            _, _, path = replacement_id(entry).partition(":")
            write_json(os.path.join(RESOURCES, "assets", NS, "models", path + ".json"), {
                # `block/block` supplies the item transforms an inventory icon needs;
                # the geometry is spelled out here because vanilla's dirt model has no
                # tint index to inherit and nothing else has this shape.
                "parent": "minecraft:block/block",
                "textures": textures,
                "elements": elements,
            })


def write_definitions():
    """The one file the runtime reads to know what is soil and what to do with it."""
    write_json(os.path.join(RESOURCES, "assets", NS, "soils.json"), {
        "textures": {texture: sprite_ids(texture) for texture in soil_textures()},
        "overlays": OVERLAYS,
        "models": {entry["id"]: replacement_id(entry)
                   for soil in SOILS for entry in soil["models"]},
        "blocks": {soil["block"]: soil["colour"] for soil in SOILS},
    })


def write_atlas():
    """
    Adds the runtime sprite source to the block atlas.

    The file goes under `assets/minecraft/`, not the mod's own namespace: the atlas
    definition's id is built from the *atlas* id (`minecraft:blocks`), and the game reads it
    with `getResourceStack`, which collects that one id from every loaded pack and
    concatenates their sources. So this appends to vanilla's list rather than replacing it,
    and a copy under `assets/tintedsoil/` is simply never looked at.
    """
    write_json(os.path.join(RESOURCES, "assets", "minecraft", "atlases", "blocks.json"),
               {"sources": [{"type": NS + ":derived_soil"}]})


def main():
    # The soil textures are not written here. SoilSpriteSource derives them at
    # atlas-stitch time from whatever textures the player's resource packs provide, so the
    # mod follows a pack instead of shipping a frozen copy of one version's art -- and
    # nothing derived from Mojang's textures ends up in the jar.
    #
    # This is the luminance that derivation measures from vanilla's own dirt.png, verified
    # against the running client. The dormant climate colormap below is pre-divided by it,
    # which is exact for vanilla and approximate under a pack that changes dirt's contrast.
    luminance = VANILLA_MEAN_LUMINANCE

    colormap = generate_colormap(luminance)
    write_png(COLORMAP_PATH, len(colormap[0]), len(colormap), colormap)

    write_models()
    write_definitions()
    write_atlas()

    print("textures  derived at runtime by SoilSpriteSource (mean luminance %.4f on vanilla)"
          % luminance)
    print("colormap  %s" % os.path.relpath(COLORMAP_PATH, ROOT))
    print("soils     %d blocks, %d models, %d derived textures, %d overlays"
          % (len(SOILS), sum(len(s["models"]) for s in SOILS),
             len(soil_textures()), len(OVERLAYS)))
    for soil in SOILS:
        print("          %-34s votes with %s" % (soil["block"], soil["colour"]))
    # SoilColormap.java mirrors this for its degraded fallback path; printing it keeps the
    # two in sync by hand rather than by memory.
    print("dry anchor      (%.2f, %.2f, %.2f) -> SoilColormap.TEMPERATE {%d, %d, %d}"
          % (TEMPERATE_DRY + tuple(int(round(v)) for v in TEMPERATE_DRY)))


if __name__ == "__main__":
    main()
