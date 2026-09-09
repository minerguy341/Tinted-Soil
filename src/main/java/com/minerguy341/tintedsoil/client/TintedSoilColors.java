package com.minerguy341.tintedsoil.client;

import com.minerguy341.tintedsoil.TintedSoilBlocks;
import com.minerguy341.tintedsoil.duck.DownfallSource;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.biome.Biome;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tint providers for the two blocks.
 *
 * <p>Tint index 0 is grass and reuses vanilla's own grass resolver, so the grass half of a
 * tinted grass block is pixel-identical to {@code minecraft:grass_block}. Tint index 1 is
 * soil and goes through {@link #SOIL_COLOR_RESOLVER}.
 *
 * <p>Routing the soil colour through a {@link ColorResolver} rather than sampling the biome
 * at the block position is the whole point of the mod: {@code ClientLevel#getBlockTint}
 * box-blurs the resolver over the player's biome blend radius, which is what turns a hard
 * biome border into a gradient.
 */
public final class TintedSoilColors {
    public static final int GRASS_TINT_INDEX = 0;
    public static final int SOIL_TINT_INDEX = 1;

    /** Per-biome soil colour, resolved once and reused; cleared with the colormap. */
    private static final Map<Biome, Integer> BIOME_SOIL = new ConcurrentHashMap<>();

    public static final ColorResolver SOIL_COLOR_RESOLVER =
            (biome, x, z) -> BIOME_SOIL.computeIfAbsent(biome, TintedSoilColors::resolveBiomeSoil);

    /**
     * A biome's soil colour.
     *
     * <p>If the biome declares its own {@code grass_color}, that choice is inherited: the
     * colour is matched back to a cell of vanilla's grass colormap and the soil colormap is
     * sampled at the same cell. Biomes O' Plenty and Oh The Biomes We've Gone set grass
     * colours on their distinctive biomes, so their soil follows their art direction with no
     * per-biome data on this end. Everything else falls back to the biome's own climate.
     *
     * <p>The override is read rather than {@code getGrassColor(x, z)} on purpose — the
     * latter applies swamp's and dark forest's positional colour modifiers, which would make
     * soil flicker between shades across a biome.
     */
    private static int resolveBiomeSoil(Biome biome) {
        Integer override = biome.getSpecialEffects().getGrassColorOverride().orElse(null);
        if (override != null) {
            float[] climate = GrassColorIndex.climateFor(override);
            if (climate != null) {
                return SoilColormap.get(climate[0], climate[1]);
            }
        }

        float temperature = Mth.clamp(biome.getBaseTemperature(), 0.0F, 1.0F);
        float downfall = 0.5F;
        // Biome is final, so the interface it gains from BiomeMixin is invisible to javac
        // and the test has to go through Object.
        if ((Object) biome instanceof DownfallSource source) {
            downfall = Mth.clamp(source.tintedsoil$downfall(), 0.0F, 1.0F);
        }
        return SoilColormap.get(temperature, downfall);
    }

    /** Dropped whenever the client clears its tint caches, which includes resource reloads. */
    public static void invalidate() {
        BIOME_SOIL.clear();
        GrassColorIndex.invalidate();
    }

    public static final BlockColor GRASS_BLOCK_COLOR = (state, view, pos, tintIndex) -> {
        if (tintIndex == SOIL_TINT_INDEX) {
            return soilTint(view, pos);
        }
        return view != null && pos != null
                ? BiomeColors.getAverageGrassColor(view, pos)
                : GrassColor.getDefaultColor();
    };

    public static final BlockColor SOIL_BLOCK_COLOR = (state, view, pos, tintIndex) -> soilTint(view, pos);

    // 1.21.4 replaced item colour providers with `tints` entries in item model definitions,
    // and 1.21.8 deleted ItemColor outright, so inventory tinting is data-driven there --
    // see assets/tintedsoil/items/*.json.
    //? if <1.21.2 {
    /** Inventory icons have no world context, so they use the plains entry of the colormap. */
    public static final net.minecraft.client.color.item.ItemColor GRASS_ITEM_COLOR =
            (stack, tintIndex) -> tintIndex == SOIL_TINT_INDEX ? SoilColormap.defaultColor() : GrassColor.getDefaultColor();

    public static final net.minecraft.client.color.item.ItemColor SOIL_ITEM_COLOR =
            (stack, tintIndex) -> SoilColormap.defaultColor();
    //?}

    private TintedSoilColors() {
    }

    /**
     * Soil colour comes from the surrounding soil types alone.
     *
     * <p>Climate no longer varies it, so every vanilla biome renders the same standard
     * dirt and only a worldgen mod's own soil blocks shift the colour -- blurred across
     * block boundaries by {@link SoilTypeBlend} so a mixed cliff face reads as a gradient.
     *
     * <p>{@link #SOIL_COLOR_RESOLVER} is therefore unused for now. It is left in place
     * rather than deleted because it is the whole biome-driven path, and reinstating it is
     * a matter of passing {@code view.getBlockTint(pos, SOIL_COLOR_RESOLVER)} here again in
     * place of the constant.
     */
    private static int soilTint(BlockAndTintGetter view, BlockPos pos) {
        if (view == null || pos == null) {
            return SoilColormap.defaultColor();
        }
        return SoilTypeBlend.apply(view, pos, SoilColormap.defaultColor());
    }

    /**
     * Registers the block providers through whatever callback the loader supplies, so the
     * Fabric and NeoForge client entrypoints stay down to a couple of lines each.
     */
    public static void registerBlockColors(BlockColorRegistrar blocks) {
        blocks.register(GRASS_BLOCK_COLOR, TintedSoilBlocks.TINTED_GRASS_BLOCK);
        blocks.register(SOIL_BLOCK_COLOR, TintedSoilBlocks.TINTED_DIRT, TintedSoilBlocks.TINTED_COARSE_DIRT);
    }

    //? if <1.21.2 {
    public static void registerItemColors(ItemColorRegistrar items) {
        items.register(GRASS_ITEM_COLOR, TintedSoilBlocks.TINTED_GRASS_BLOCK_ITEM);
        items.register(SOIL_ITEM_COLOR, TintedSoilBlocks.TINTED_DIRT_ITEM,
                TintedSoilBlocks.TINTED_COARSE_DIRT_ITEM);
    }

    @FunctionalInterface
    public interface ItemColorRegistrar {
        void register(net.minecraft.client.color.item.ItemColor color, net.minecraft.world.level.ItemLike... items);
    }
    //?}

    @FunctionalInterface
    public interface BlockColorRegistrar {
        void register(BlockColor color, net.minecraft.world.level.block.Block... blocks);
    }
}
