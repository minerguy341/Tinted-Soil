# Tinted Soil

Replaces vanilla and worldgen-mod grass/dirt with biome-tinted blocks, so soil and grass
blend smoothly across biome borders instead of cutting off at a hard line — which is most
visible on hills and cliffs, where two soil types meet on a vertical face.

Everything is driven by **two tint indices**:

- `tintedsoil:tinted_grass_block` — two tints: grass (index 0) and the soil under it (index 1)
- `tintedsoil:tinted_soil` — one tint: soil (index 1)
- `tintedsoil:tinted_coarse_soil` — soil tint as above, but grass will not spread onto it

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
(`textures/block/tinted_soil.png`) is a light, strictly greyscale grain carrying only the
*pattern*, and all of the colour comes from a colormap
(`textures/colormap/soil.png`), indexed exactly like vanilla's `grass.png`.

The base textures are neutral by construction — `R == G == B` for every pixel. Any hue
baked into a texture would multiply against the tint and pull every biome's soil toward it,
so the tint is the only thing deciding colour.

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

### Two blends, composed

A soil colour has to answer two questions — *where* is this, and *what* is it — and each
gets its own blend.

**Where: the biome, blended across biomes.** The soil tint goes through a `ColorResolver`,
so `ClientLevel#getBlockTint` box-blurs it over the player's biome-blend radius, exactly the
way grass and water are blended. That is what turns a biome border into a gradient. Vanilla
builds its tint-cache map with a fixed set of resolvers, so `ClientLevelMixin` gives the
soil resolver a `BlockTintCache` of its own.

A biome that declares its own `grass_color` has that choice **inherited**: the colour is
matched back to a cell of vanilla's `grass.png` (`GrassColorIndex` inverts it by search) and
the soil colormap is sampled at the same cell. The two colormaps share an index space, so
asking "which climate does this grass *look* like?" makes soil follow whatever art direction
a biome author chose — Biomes O' Plenty's and BWG's included — with no per-biome data here.
Biomes without an override fall back to their own climate. The override is read directly
rather than through `getGrassColor(x, z)`, which would apply swamp's and dark forest's
positional modifiers and make soil flicker across a biome.

**What: the soil type, blended across blocks.** Vanilla can only blend tints that are a
function of `Biome`. Soil type is a property of the *block*, so left alone it would produce
exactly the hard seam this mod exists to remove — peat meeting sandy soil with a one-pixel
edge. `SoilTypeBlend` does the equivalent blur in block space: sample the types in a 5×5
horizontal kernel, mix their colours by how many of each there are, and pull the biome
colour toward the result. Blocks that are not ours abstain rather than voting for plain
dirt, so soil meeting stone keeps its character instead of washing out.

The pull is scaled by `SoilTypePalette.STRENGTH` (0.6) and by how much of the neighbourhood
is typed, so type is a *character on top of* climate rather than a replacement for it — the
same lush soil still reads colder in a snowy biome than in a jungle. Plain dirt contributes
nothing at all, which is what keeps ordinary terrain looking exactly as it did.

A peat/plain boundary across the kernel:

```
 0/25 peat  #876749      15/25 peat  #6C533C
 5/25 peat  #7E6045      20/25 peat  #634D38
10/25 peat  #755A40      25/25 peat  #594734
```

> **Performance note.** The block-space blur is `(2r+1)² = 25` block reads per tinted face
> and, unlike vanilla's biome blur, is **not cached**. That is the known cost of getting
> smooth type transitions, and it is unprofiled — if it shows up, a per-position cache keyed
> like `BlockTintCache` is the obvious next step. `SoilTypeBlend.RADIUS` tunes it.

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
- `tintedsoil:replaceable_coarse_soil` → `tinted_coarse_soil` (checked first, so a block in
  both soil tags ends up coarse)

**These tags are the configuration.** There is no config file: add a modded soil to a tag
and it starts being replaced; empty a tag with a datapack and that half switches off.

Shipped defaults, taken from the mods' own jars rather than guessed:

| Tag | Entries |
|-----|---------|
| `replaceable_grass` | `minecraft:grass_block`, `biomesoplenty:origin_grass_block`, `biomeswevegone:lush_grass_block` |
| `replaceable_soil` | `minecraft:dirt`, `minecraft:podzol`, `biomeswevegone:lush_dirt`, `biomeswevegone:sandy_dirt`, `biomeswevegone:peat` |
| `replaceable_coarse_soil` | `minecraft:coarse_dirt` |

Modded entries are marked `"required": false`, so the mods are optional at runtime and no
compile-time dependency on either is needed.

### Soil types

A replaced block records *which* soil it stood in for, in a `soil_type` blockstate property,
and keeps it for good — long after worldgen has finished. Grass carries the type across as
it spreads and dies back, so a patch never forgets it was peat just because it lost its
grass. Assignment is by tag, so a datapack can route a modded soil to whichever type looks
closest:

| `soil_type` | Sources | Character |
|-------------|---------|-----------|
| `default` | everything unlisted | none — the biome colour, untouched |
| `podzol` | `minecraft:podzol` | dark orange-brown |
| `lush` | `biomeswevegone:lush_dirt`, `lush_grass_block` | dark, rich |
| `sandy` | `biomeswevegone:sandy_dirt` | pale and sandy |
| `peat` | `biomeswevegone:peat` | near-black bog soil |
| `origin` | `biomesoplenty:origin_grass_block` | warm mid brown |

This is colour only. Behaviour differences live in the blocks: coarse soil is its own block
because grass must not spread onto it. Breaking and replacing a block loses its type and
gives you plain soil, as vanilla does with grass.

Coarse dirt and podzol both map to **soil**, not grass: neither has a grass overlay, so
sending them to the grass block would paint grass over badlands and old-growth taiga floors.

Coarse dirt keeps its own block so that grass will not spread onto it, as in vanilla. That
also lets it reproduce coarse dirt's exact tag set, `armadillo_spawnable_on` included, and
lets a hoe turn it back into plain tinted soil rather than straight to farmland. It is a
third *block*, not a third tint — it uses the same soil tint as everything else.

Podzol has no such block and folds into plain tinted soil, which trades two vanilla
behaviours for the smoother surface:

- tinted grass now spreads onto ground that used to be podzol, where vanilla grass never
  could, so old-growth taiga floors green over in time;
- mushrooms lose podzol's grow-at-any-light-level rule, which keys off
  `minecraft:mushroom_grow_block`. Adding tinted soil to that tag is *not* the fix — it
  would let mushrooms grow on every soil block in the world.

Drop `minecraft:podzol` from `replaceable_soil` to get the vanilla behaviour back. Giving
podzol the same treatment as coarse dirt — its own block — would fix both, at the cost of a
fourth block.

**Still excluded:** rooted dirt (drops hanging roots), mycelium (spreads, and grows
mushrooms), mud, and BWG's overgrown/podzol dacite. Add them to the tags if you would
rather have the smoother surface there too.

Replacement happens in `ProtoChunkMixin`, on `ProtoChunk#setBlockState`. Every worldgen
path — surface rules, features, carvers, structures, and anything a worldgen mod adds —
writes through it, which is why Biomes O' Plenty and Oh The Biomes We've Gone are covered
without the mod knowing anything about them. Only newly generated chunks are affected;
existing chunks are left alone.

## Inheriting other mods' data

Each replacement joins the vanilla tags of the block it stands in for, read out of the
1.21.1 client jar: `dirt`, `mineable/shovel`, `sniffer_diggable_block`,
`convertable_to_mud`, `armadillo_spawnable_on` and the `*_spawnable_on` family. Membership
in `minecraft:dirt` is the important one — most other tags, and most other mods' data,
reference it transitively, so nutrient systems and similar tag-driven features apply
unchanged.

Plain tinted soil deliberately mirrors `dirt` only. Podzol folds into it, and inheriting
podzol's tags as well would *widen* them rather than preserve behaviour — foxes would spawn
on all soil, mushrooms would grow on it everywhere.

Interactions vanilla hardcodes in static maps rather than in data are registered in
`TintedSoilInteractions`: shovel → path for all three, hoe → farmland for grass and soil,
and hoe → plain soil for coarse, matching vanilla's coarse-dirt-to-dirt behaviour.

Grass spreading needed real code: `SpreadingSnowyDirtBlock#randomTick` hardcodes
`Blocks.DIRT` and `Blocks.GRASS_BLOCK`, so once a world is made of tinted blocks, vanilla's
spread would find nothing to grow onto and grass would simply stop spreading.
`TintedGrassBlock#randomTick` is a faithful port that walks tinted soil instead, calling
vanilla's own private `canBeGrass`/`canPropagate` through an invoker mixin so the survival
rules stay identical rather than approximated. It only ever spreads onto `tinted_soil`,
never `tinted_coarse_soil` — that omission is the whole mechanism keeping coarse soil bare.

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
