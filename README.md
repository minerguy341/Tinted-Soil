# Tinted Soil

Replaces vanilla and worldgen-mod grass/dirt with **two** biome-tinted blocks, so soil and
grass blend smoothly across biome borders instead of cutting off at a hard line — which is
most visible on hills and cliffs, where two soil types meet on a vertical face.

- `tintedsoil:tinted_grass_block` — two tints: grass (index 0) and the soil under it (index 1)
- `tintedsoil:tinted_soil` — one tint: soil (index 1)

Built with [Stonecutter](https://stonecutter.kikugie.dev/) from a single `src/` tree.

| Minecraft | Fabric | NeoForge |
|-----------|:------:|:--------:|
| 1.20.1    |   ✅   | see [NeoForge on 1.20.1](#neoforge-on-1201) |
| 1.21.1    |   ✅   |    ✅    |
| 1.21.8    |   ✅   |    ✅    |

## How the colour works

Block tints are **multiplicative** — the rendered colour is `texel × tint`. Vanilla
`dirt.png` is already a saturated brown, so no tint can push it toward pale sand; it can
only be darkened. Tinted Soil therefore does what vanilla does for grass: the texture
(`textures/block/tinted_soil.png`) is a light, desaturated grain carrying only the
*pattern*, and all of the colour comes from a colormap
(`textures/colormap/soil.png`), indexed exactly like vanilla's `grass.png`.

The colour model blends four anchors over (temperature, downfall):

| Anchor | Where | Rendered |
|--------|-------|----------|
| `TEMPERATE` | plains, forest | `#866043` — vanilla dirt |
| `HUMID` | jungle, swamp, dark forest | `#684832` |
| `SAND` | desert, savanna, badlands | `#CEB78A` |
| `COLD` | snowy and peak biomes | `#928A80` |

Sandiness is shaped by `t^1.5 · (1−d)^3` so that merely dry-ish temperate biomes (plains
sits at downfall 0.4) stay brown and only genuinely rainless hot biomes reach sand.
Resulting samples:

```
plains        T=0.80 D=0.40  #876749     desert/savanna  T=1.00 D=0.00  #CEB78A
forest        T=0.70 D=0.80  #72533A     taiga           T=0.25 D=0.80  #826F5E
jungle        T=0.95 D=0.90  #6B4B30     snowy plains    T=0.00 D=0.50  #928A80
```

**The blending is the point.** The soil tint goes through a `ColorResolver`, so
`ClientLevel#getBlockTint` box-blurs it over the player's biome-blend radius, exactly the
way grass and water are blended. That is what turns a biome border into a gradient. Vanilla
builds its tint-cache map with a fixed set of resolvers, so `ClientLevelMixin` gives the
soil resolver a `BlockTintCache` of its own.

### Retuning it

Edit the anchors at the top of `tools/generate_assets.py` and run:

```bash
python3 tools/generate_assets.py
```

That regenerates the texture, the colormap, and every JSON resource. The same model is
mirrored in `SoilColormap#fallbackColor` as a safety net if the colormap image cannot be
read — keep the two in sync (the constants are named identically in both files).

Resource packs can override `textures/colormap/soil.png` directly; it reloads with the rest
of the client's resources.

## What gets replaced

Replacement is driven by two block tags, applied during world generation:

- `tintedsoil:replaceable_grass` → `tinted_grass_block`
- `tintedsoil:replaceable_soil` → `tinted_soil`

**These tags are the configuration.** There is no config file: add a modded soil to a tag
and it starts being replaced; empty a tag with a datapack and that half switches off.

Shipped defaults, taken from the mods' own jars rather than guessed:

| Tag | Entries |
|-----|---------|
| `replaceable_grass` | `minecraft:grass_block`, `biomesoplenty:origin_grass_block`, `biomeswevegone:lush_grass_block` |
| `replaceable_soil` | `minecraft:dirt`, `biomeswevegone:lush_dirt`, `biomeswevegone:sandy_dirt`, `biomeswevegone:peat` |

Modded entries are marked `"required": false`, so the mods are optional at runtime and no
compile-time dependency on either is needed.

**Deliberately excluded:** coarse dirt, rooted dirt, podzol, mycelium, mud, and BWG's
overgrown/podzol dacite. Each is visually distinct *and* behaves differently — grass will
not spread onto coarse dirt, rooted dirt drops hanging roots — and folding them into one
tinted block would silently drop that behaviour. Add them to the tags if you would rather
have the smoother surface.

Replacement happens in `ProtoChunkMixin`, on `ProtoChunk#setBlockState`. Every worldgen
path — surface rules, features, carvers, structures, and anything a worldgen mod adds —
writes through it, which is why Biomes O' Plenty and Oh The Biomes We've Gone are covered
without the mod knowing anything about them. Only newly generated chunks are affected;
existing chunks are left alone.

## Inheriting other mods' data

The replacements join every vanilla tag that `grass_block`/`dirt` belong to (read out of the
1.21.1 client jar): `dirt`, `mineable/shovel`, `sniffer_diggable_block`,
`convertable_to_mud`, and the `*_spawnable_on` family. Membership in `minecraft:dirt` is the
important one — most other tags, and most other mods' data, reference it transitively, so
nutrient systems and similar tag-driven features apply unchanged.

Two interactions vanilla hardcodes in static maps rather than in data — shovel → path and
hoe → farmland — are registered in `TintedSoilInteractions`.

Grass spreading needed real code: `SpreadingSnowyDirtBlock#randomTick` hardcodes
`Blocks.DIRT` and `Blocks.GRASS_BLOCK`, so once a world is made of tinted blocks, vanilla's
spread would find nothing to grow onto and grass would simply stop spreading.
`TintedGrassBlock#randomTick` is a faithful port that walks tinted soil instead, calling
vanilla's own private `canBeGrass`/`canPropagate` through an invoker mixin so the survival
rules stay identical rather than approximated.

## Building

```bash
./gradlew buildAll                 # every version and loader
./gradlew :1.21.1-fabric:build     # one target
```

Jars land in `build/libs/<mod version>/`. Stonecutter 0.9 builds inactive versions from
generated sources, so `buildAll` needs no version switching.

`./gradlew "Tinted Soil:stonecutterSwitchTo1.21.8-fabric"` switches which version your IDE
resolves against.

### Layout

```
src/                        one shared source tree for all 5 targets
stonecutter.properties.toml versions and dependency coordinates
build.fabric.gradle.kts     Fabric buildscript (Loom via loom-back-compat)
build.neoforge.gradle.kts   NeoForge buildscript (ModDevGradle)
tools/generate_assets.py    regenerates textures, colormap and all JSON resources
```

Both loaders use **Mojang mappings**, which is what lets one source tree compile for both.

### Version differences

Handled with Stonecutter conditions rather than parallel source trees:

| Change | Versions | Handling |
|--------|----------|----------|
| `new ResourceLocation` → `fromNamespaceAndPath` | 1.21 | `TintedSoil#id` |
| `Properties.copy` → `ofFullCopy` | 1.21 | `TintedSoilBlocks#copyOf` |
| Block/item ids move into properties (`setId`) | 1.21.2 | `TintedSoilBlocks` |
| `Block#codec()` becomes abstract | 1.21 | `TintedGrassBlock` |
| `ItemColor`/`ItemColors` deleted | 1.21.4 | item model definitions in `assets/tintedsoil/items/` |
| `tags/blocks` → `tags/block`, `loot_tables` → `loot_table` | 1.21 | both layouts shipped |
| `match_tool` predicate shape | 1.21 | one form per loot-table folder |

`ProtoChunk#setBlockState`'s third parameter changed from `boolean` to `int`, and
`ClientLevel`'s constructor changed shape, in this range. Both are avoided rather than
branched on — `@ModifyVariable(argsOnly = true)` matches the `BlockState` argument by type,
and the `ClientLevel` hook targets `getBlockTint` instead of the constructor.

Data-folder duplication is intentional: both layouts are generated from one definition in
`tools/generate_assets.py`, so they cannot drift, and each version ignores the folder it
does not recognise.

### NeoForge on 1.20.1

Not built. `net.neoforged:neoforge` starts at `20.2`; the 1.20.1 fork is published as
`net.neoforged:forge:1.20.1-47.x` and needs a different toolchain end to end — the
`net.neoforged.moddev.legacyforge` plugin, `net.minecraftforge.*` package names,
SRG-named mixin refmaps, and legacy `META-INF/mods.toml`. That is a third buildscript plus a
third branch of every loader-specific class, for one target.

To add it: register the node in `settings.gradle.kts` (there is a comment marking the spot),
add `build.legacyforge.gradle.kts`, and add `legacyforge` variants of the four entrypoint
classes.

## Licence

MIT — see `LICENSE`.
