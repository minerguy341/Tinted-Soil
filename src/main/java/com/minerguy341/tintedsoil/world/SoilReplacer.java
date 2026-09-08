package com.minerguy341.tintedsoil.world;

import com.minerguy341.tintedsoil.TintedSoilBlocks;
import com.minerguy341.tintedsoil.TintedSoilTags;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Maps a generated block state onto its tinted replacement.
 *
 * <p>Membership is decided entirely by the two block tags, which means a datapack can add
 * a soil this mod has never heard of, or empty a tag to switch that half of the
 * replacement off, with no code change.
 */
public final class SoilReplacer {
    private SoilReplacer() {
    }

    public static BlockState replace(BlockState state) {
        if (state.isAir()) {
            return state;
        }

        if (state.is(TintedSoilTags.REPLACEABLE_GRASS)) {
            BlockState replacement = TintedSoilBlocks.TINTED_GRASS_BLOCK.defaultBlockState();
            // Carry `snowy` across so snow-covered surfaces keep rendering correctly.
            if (state.hasProperty(SnowyDirtBlock.SNOWY)) {
                replacement = replacement.setValue(SnowyDirtBlock.SNOWY, state.getValue(SnowyDirtBlock.SNOWY));
            }
            return replacement;
        }

        if (state.is(TintedSoilTags.REPLACEABLE_SOIL)) {
            return TintedSoilBlocks.TINTED_SOIL.defaultBlockState();
        }

        return state;
    }
}
