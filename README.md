# Tinted Soil

Retints the soil that is already in your world — vanilla's dirt, coarse dirt, podzol and
grass blocks, and a worldgen mod's soils alongside them — so that where two soils meet they
blend into each other instead of cutting off at a hard line. It is most visible on hills and
cliffs, where a worldgen mod's dirt interleaves with vanilla's block by block down the same
vertical face.

It adds **no blocks, no items, no tags and no worldgen**, and writes nothing to a world. All
of it is client-side rendering: the models those blocks resolve to are swapped for retinted
ones as they load, and a tint provider colours them. Uninstalling it leaves a world exactly
as it was.

Built with [Stonecutter](https://stonecutter.kikugie.dev/) from a single `src/` tree.

| Minecraft | Fabric | NeoForge |
|-----------|:------:|:--------:|
| 1.20.1    |   ✅   | see [NeoForge on 1.20.1](#neoforge-on-1201) |
| 1.21.1    |   ✅   |    ✅    |
| 1.21.8    |   ✅   |    ✅    |

## How the colour works

Block tints are **multiplicative** — the rendered colour is `texel × tint`. Vanilla
`dirt.png` is a saturated brown, so no tint applied to it can do anything but darken it.
Tinted Soil therefore does what vanilla does for grass: it renders soil as a greyscale grain
carrying only the *pattern*, with all of the colour in the tint.

The greyscale is not shipped. It is derived at atlas-stitch time, from whatever textures the
player's resource packs actually supply, by projecting every pixel onto the texture's own
average colour — which keeps the grain and discards the hue. Two numbers come out of each
texture:

- its **average colour**, which is what a block wearing it renders as; and
- the **mean luminance** of the greyscale, which the tint is divided by.

Multiply the greyscale by `average ÷ mean` and you get the original texture back. Measured
against vanilla's own art, that round trip is exact to about one part in 255:

| Texture | Average | Mean | Tint | Worst channel error |
|---------|---------|------|------|---------------------|
| `dirt` | `#876041` | 0.7232 | `#BA845A` | 9 |
| `coarse_dirt` | `#78553A` | 0.6436 | `#BA845A` | 6 |
| `grass_block_side` | `#825C3F` | 0.6974 | `#BA845A` | 9 |
| BWG `lush_dirt` | `#534031` | 0.6630 | `#7E6049` | 4 |
| BWG `sandy_dirt` | `#D9CA9D` | 0.8610 | `#FCEBB7` | 20 |
| BWG `peat` | `#514137` | 0.6607 | `#7B6254` | 8 |

So **a soil surrounded by its own kind renders exactly as it did**, and the mod only shows
where two soils meet. The identical tint for dirt, coarse dirt and grass block side is the
same fact from the other end: they are the same material, and all of the brightness
difference between dirt and coarse dirt lives in the greyscale.

Deriving per texture is also what removed the old brightness ceiling. A tint is stored in
eight bits per channel, so on one shared greyscale no colour brighter than about `#B8B8B8`
was representable and BWG's pale `#D9CA9D` sandy dirt was unreachable. Tinted on a greyscale
made from *its own* texture it needs `#FCEBB7`, which fits.

### Pebbles, fringes, and anything that is not soil

Soil textures are not all soil. Vanilla's dirt has seven grey stones in it; vanilla's
`grass_block_side` has 61 pixels of grass fringe painted into the top four rows, and Biomes
O' Plenty paints its origin grass on the same way rather than shipping an overlay. Grey times
a brown tint is just brown, so tinting those would dissolve them into the ground.

Every pixel that sits too far off the texture's own hue is therefore pulled out into a second
sprite and drawn back over the tinted cube **untinted**, by a second element in the model.
Stones stay stone-coloured in every biome, and a painted-on fringe stays exactly the colour
its author chose. The test is distance from the hue (0.12), not neutrality: vanilla's pebbles
happen to be neutral grey but Bare Bones' are `#A09184`, and an `r == g == b` test finds none
of them.

Podzol is the one soil that needs help: vanilla composites its crust into `podzol_side.png`
rather than shipping an overlay, and the crust is close enough to the dirt hue that only half
of it separates out. So it is recovered by subtraction instead — keep a pixel where
`podzol_side` differs from `dirt`, drop it where they agree — and podzol's body stays plain
dirt, which is what keeps it blending with the ordinary ground it is made of.

### Blending

Vanilla can only blend tints that are a function of `Biome`, because that is what
`ClientLevel#getBlockTint` box-blurs. Which soil a block *is* is a property of the block, so
left alone it produces exactly the hard seam this mod exists to remove.

`SoilBlend` does the equivalent blur in block space: sample the soil in a 5×5×3 kernel around
the position and average the colours those soils render as. Every soil votes, plain dirt
included, so the result is a genuine interpolation between two dirts rather than a nudge away
from a shared base. Blocks that are not soil abstain rather than voting for dirt, so soil
meeting stone keeps its character instead of washing out. The vertical radius is 1 rather
than 2 so that a cliff face blends up and down as well as along, without tripling the cost
again.

Because the tint is divided by the *local* block's mean luminance, the average colour a block
renders is exactly the blended vote — whatever texture it happens to be wearing.

> **Performance note.** The block-space blur is `5 × 5 × 3 = 75` block reads per tinted face
> and, unlike vanilla's biome blur, is **not cached**. That is the known cost of smooth
> transitions, and it is unprofiled — if it shows up, a per-position cache keyed like
> `BlockTintCache` is the obvious next step. `SoilBlend.RADIUS` tunes it.

### The dormant climate path

`SoilColormap`, `GrassColorIndex`, `BiomeMixin` and the `BlockTintCache` that
`ClientLevelMixin` gives it are a complete second colour model, indexed by (temperature,
downfall) exactly like vanilla's `grass.png`, in which a biome that declares its own
`grass_color` has that choice inherited. Nothing calls it: soil colour now comes from the
blocks around a position rather than from the biome. It is kept because it is the whole
biome-driven path and reinstating it means blending its result into `TintedSoilColors#soilTint`
again. `tools/generate_assets.py` still regenerates its colormap.

## What gets retinted

`assets/tintedsoil/soils.json` is the configuration. It names, for each soil block, the
models it resolves to and the textures on those models that are soil rather than something
growing on it:

| Block | Votes with | Models replaced |
|-------|-----------|-----------------|
| `minecraft:dirt` | `block/dirt` | `block/dirt` |
| `minecraft:coarse_dirt` | `block/coarse_dirt` | `block/coarse_dirt` |
| `minecraft:podzol` | `block/dirt` | `block/podzol` |
| `minecraft:grass_block` | `block/dirt` | `block/grass_block`, `block/grass_block_snow` |
| `biomeswevegone:lush_dirt` | `block/lush_dirt` | `block/lush_dirt` |
| `biomeswevegone:sandy_dirt` | `block/sandy_dirt` | `block/sandy_dirt` |
| `biomeswevegone:peat` | `block/peat` | `block/peat` |
| `biomeswevegone:lush_grass_block` | `block/lush_dirt` | `block/lush_grass_block`, `…_snowy` |
| `biomesoplenty:origin_grass_block` | `block/dirt` | `block/origin_grass_block`, `…_snow` |

Every model and texture id is read out of the mod's own jar rather than guessed — Minecraft
1.21.1, Oh The Biomes We've Gone 2.6.0, Biomes O' Plenty 21.1.0.14. A block votes with the
texture of the *soil* under it, so a grass block and the dirt beside it agree on the colour
of the ground.

The file is read with `getResourceStack`, so a resource pack that ships its own copy **adds**
soils rather than replacing the list. It is a client resource rather than the block tags this
used to be configured with because models are loaded and the atlas stitched during a resource
reload, which happens long before the client has been told about a single block tag.

Still excluded: rooted dirt (it drops hanging roots), mycelium (it spreads, and grows
mushrooms), mud, and BWG's overgrown/podzol dacite.

Every JSON under `assets/` — `soils.json`, the replacement models, the atlas entry — is
emitted by `tools/generate_assets.py`. Edit the definitions at the top of that file and run:

```bash
python3 tools/generate_assets.py
```

## How the models are replaced

`ModelManager#loadBlockModels` reads every model file on the pack stack and hands back a map.
One mixin injects at its return and swaps the soil entries for models that inherit from the
generated ones under `assets/tintedsoil/models/block/soil/`. Each replacement mirrors the
geometry of the model it stands in for, face for face, with two changes: a soil face samples
the derived greyscale and gains tint index 1, and a second, untinted element draws that
texture's pebbles back on top.

Substituting the **unbaked** model rather than the baked one is what keeps everything vanilla
decided about the block. The blockstate file still applies, so dirt still picks one of four
random rotations and a grass block still switches models when it is snowed on. Nothing in the
mod knows about blockstates at all.

`ModelManager#loadBlockModels` is the same private method with the same signature on 1.20.1,
1.21.1 and 1.21.8, on both loaders, so one injection covers all five build targets. Two
things about the map it returns changed in 1.21.4 and are branched on: its values went from
`BlockModel` to `UnbakedModel`, and its keys from the file a model was read from to the
model's own id.

### Why a mixin rather than the loaders' model APIs

NeoForge has no hook for unbaked models. `ModelEvent.ModifyBakingResult` hands over models
that are already baked, so using it would mean rebuilding each block's variant dispatch —
including dirt's four rotations — by hand, and differently on 21.1 and 21.8. Fabric's
`fabric-model-loading-api-v1` does have `modifyModelOnLoad`, but it would only cover the
Fabric half, and its own shape changed across the three versions built here. One mixin
covers both loaders and preserves blockstate behaviour for free.

Doing it this way removed the mod's dependency on Fabric API entirely: render layers and
tints go through mixins for the same reason, so there is nothing left on the Fabric side that
Fabric API supplies.

### Why not ship model overrides

The obvious alternative is to ship `assets/minecraft/models/block/dirt.json` in the jar and
let it override vanilla's. A resource pack sits **above** mod resources in the stack, so the
pack the mod most wants to follow — Bare Bones, say — would silently override the retinted
models with its own untinted ones and switch the mod off on exactly the setup it was built
for.

### Tints, render layers and items

- **Tint index 0 is grass; index 1 is soil.** The mod claims index 1 and nothing else, in a
  mixin on `BlockColors#getColor`. Registering a `BlockColor` would have replaced whatever
  provider the block already had — for a modded grass block, that mod's own grass colour —
  and which of us won would come down to mod initialisation order. Leaving index 0 alone
  means vanilla still colours grass tops and BWG still colours its lush grass.
- **Render layer.** Every retinted model draws a transparent layer over a tinted cube, and in
  the solid layer that alpha is ignored and draws as opaque black. `ItemBlockRenderTypes#getChunkRenderType`
  is intercepted to put soil in `cutout_mipped`, which is also the layer vanilla already uses
  for its own grass block. Deciding it there sidesteps the fact that which blocks are soil is
  not known until `soils.json` has been read, well after both loaders' render-layer registries
  are meant to be filled in.
- **Items.** A block item renders the block's model, so it needs the soil tint too, and gets
  the block's own unblended colour. Before 1.21.4 that is a mixin on `ItemColors#getColor`;
  from 1.21.4 item tints moved into item model definitions, and vanilla's `items/dirt.json`
  declares none, so the loaded definitions are walked instead and the missing tint added.

## Building

```bash
./gradlew buildAll                 # every version and loader
./gradlew :1.21.1-fabric:build     # one target
```

`JAVA_HOME` must be a **JDK 21**; 1.20.1 targets Java 17, which the toolchain resolves
separately. Jars land in `build/libs/<mod version>/`. Stonecutter 0.9 builds inactive
versions from generated sources, so `buildAll` needs no version switching.

`./gradlew "Tinted Soil:stonecutterSwitchTo1.21.8-fabric"` switches which version your IDE
resolves against. Switch back to `1.21.1-fabric` — the VCS version — before committing.

### Layout

```
src/                        one shared source tree for all 5 targets
stonecutter.properties.toml versions and dependency coordinates
build.fabric.gradle.kts     Fabric buildscript (Loom via loom-back-compat)
build.neoforge.gradle.kts   NeoForge buildscript (ModDevGradle)
tools/generate_assets.py    regenerates soils.json, every model, the atlas and the colormap
```

Both loaders use **Mojang mappings**, which is what lets one source tree compile for both.

### Version differences

Handled with Stonecutter conditions rather than parallel source trees:

| Change | Versions | Handling |
|--------|----------|----------|
| `new ResourceLocation` → `fromNamespaceAndPath` | 1.21 | `TintedSoil#location` |
| `SpriteContents` supplier gains a loader argument | 1.21 | `SoilSpriteSource#add` |
| `Registry#get` → `getValue` | 1.21.2 | `TintedSoil#block` |
| `BlockModel` folded into `UnbakedModel`; model map keyed by id | 1.21.4 | `SoilModels` |
| `ItemColors` deleted, item tints move into item model definitions | 1.21.4 | `SoilItemTints` |
| `SpriteSourceType` → a codec in a `LateBoundIdMapper` | 1.21.6 | `SoilSpriteSources` |
| `NativeImage#setPixelRGBA` → `setPixelABGR` | 1.21.6 | `SoilSpriteSource#setPixel` |
| `RenderType` → `ChunkSectionLayer` | 1.21.6 | `SoilRenderTypeMixin` |

`ClientLevel`'s constructor changed shape in this range; that is avoided rather than branched
on, by hooking `getBlockTint` instead.

### NeoForge on 1.20.1

Not built. `net.neoforged:neoforge` starts at `20.2`; the 1.20.1 fork is published as
`net.neoforged:forge:1.20.1-47.x` and needs a different toolchain end to end — the
`net.neoforged.moddev.legacyforge` plugin, `net.minecraftforge.*` package names, SRG-named
mixin refmaps, and legacy `META-INF/mods.toml`. That is a third buildscript plus a third
branch of every loader-specific class, for one target.

To add it: register the node in `settings.gradle.kts` (there is a comment marking the spot),
add `build.legacyforge.gradle.kts`, and add `legacyforge` variants of the entrypoint classes.

## Licence

MIT — see `LICENSE`.
