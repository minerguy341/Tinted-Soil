package com.minerguy341.tintedsoil.client;

import com.minerguy341.tintedsoil.duck.DownfallSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The soil tint, for the one index this mod claims.
 *
 * <p>Tint index 0 is grass and is left entirely alone: vanilla already uses it for
 * {@code grass_block}, and a worldgen mod already registers its own provider for its own
 * grass. Claiming only index 1 is what lets the retinted models keep every grass colour
 * exactly as its owner meant it, whatever order the mods happened to initialise in.
 *
 * <p>The colour itself comes from the blocks around the position rather than from the
 * biome -- see {@link SoilBlend}. The biome-driven path below is dormant but intact; see
 * {@link #SOIL_COLOR_RESOLVER}.
 */
public final class TintedSoilColors {
    public static final int GRASS_TINT_INDEX = 0;
    public static final int SOIL_TINT_INDEX = 1;

    /** Per-biome soil colour, resolved once and reused; cleared with the colormap. */
    private static final Map<Biome, Integer> BIOME_SOIL = new ConcurrentHashMap<>();

    /**
     * The climate-driven soil colour, blurred across biome borders by
     * {@code ClientLevel#getBlockTint}.
     *
     * <p>Unused: soil colour now comes from the textures of the blocks around a position,
     * so every vanilla biome renders the same standard dirt and only a worldgen mod's own
     * soils shift it. This is left in place rather than deleted because it is the whole
     * biome-driven path -- colormap, climate inversion, per-biome cache and the
     * {@code BlockTintCache} that {@code ClientLevelMixin} gives it -- and reinstating it
     * is a matter of blending its result into {@link #soilTint} again.
     */
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
     * <p>The override is read rather than {@code getGrassColor(x, z)} on purpose -- the
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

    private TintedSoilColors() {
    }

    /**
     * The tint for a soil block's soil faces.
     *
     * @return the tint, or {@code -1} (no tint) if this block is not soil
     */
    public static int soilTint(BlockState state, BlockAndTintGetter view, BlockPos pos) {
        SoilTints.Soil soil = SoilTints.of(state.getBlock());
        if (soil == null) {
            return -1;
        }
        int rendered = view != null && pos != null
                ? SoilBlend.apply(view, pos, soil.colour())
                : soil.colour();
        // Dividing by this block's own mean luminance is what makes the average colour it
        // renders equal the colour asked for, whichever texture it is wearing.
        return soil.tintFor(rendered);
    }

    /** The soil colour with no world context: inventory icons and dropped items. */
    public static int itemTint(net.minecraft.world.level.block.Block block) {
        SoilTints.Soil soil = SoilTints.of(block);
        return soil == null ? -1 : soil.tintFor(soil.colour());
    }
}
