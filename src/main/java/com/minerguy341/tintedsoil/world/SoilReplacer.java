package com.minerguy341.tintedsoil.world;

import com.minerguy341.tintedsoil.TintedSoilBlocks;
import com.minerguy341.tintedsoil.TintedSoilTags;
import com.minerguy341.tintedsoil.block.SoilType;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Maps a generated block state onto its tinted replacement.
 *
 * <p>Membership is decided entirely by block tags, which means a datapack can add a soil
 * this mod has never heard of, or empty a tag to switch that half of the replacement off,
 * with no code change.
 *
 * <p>Two tag lookups decide <em>whether</em> to replace; the per-type tags that decide
 * <em>which</em> soil type to record are only consulted once a block is known to be
 * replaceable, so the common non-soil case still costs the same two lookups it always did.
 */
public final class SoilReplacer {
    private static final SoilType[] TYPES = SoilType.values();

    private SoilReplacer() {
    }

    public static BlockState replace(BlockState state) {
        if (state.isAir()) {
            return state;
        }

        if (state.is(TintedSoilTags.REPLACEABLE_GRASS)) {
            BlockState replacement = TintedSoilBlocks.TINTED_GRASS_BLOCK.defaultBlockState()
                    .setValue(SoilType.PROPERTY, typeOf(state));
            // Carry `snowy` across so snow-covered surfaces keep rendering correctly.
            if (state.hasProperty(SnowyDirtBlock.SNOWY)) {
                replacement = replacement.setValue(SnowyDirtBlock.SNOWY, state.getValue(SnowyDirtBlock.SNOWY));
            }
            return replacement;
        }

        // Coarse is checked first so a block in both tags stays bare.
        if (state.is(TintedSoilTags.REPLACEABLE_COARSE_SOIL)) {
            return TintedSoilBlocks.TINTED_COARSE_SOIL.defaultBlockState()
                    .setValue(SoilType.PROPERTY, typeOf(state));
        }

        if (state.is(TintedSoilTags.REPLACEABLE_SOIL)) {
            return TintedSoilBlocks.TINTED_SOIL.defaultBlockState()
                    .setValue(SoilType.PROPERTY, typeOf(state));
        }

        return state;
    }

    /**
     * The soil type a source block records. {@link SoilType#DEFAULT} is the fallback, so
     * an unlisted soil simply takes the biome colour with no character of its own.
     */
    private static SoilType typeOf(BlockState state) {
        // Index 0 is DEFAULT, which has no tag of its own.
        for (int i = 1; i < TYPES.length; i++) {
            if (state.is(TYPES[i].tag())) {
                return TYPES[i];
            }
        }
        return SoilType.DEFAULT;
    }
}
