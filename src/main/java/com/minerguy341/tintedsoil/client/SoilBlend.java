package com.minerguy341.tintedsoil.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blurs soil colours across neighbouring blocks.
 *
 * <p>Vanilla can only blend tints that are a function of {@code Biome}, because that is
 * what {@code ClientLevel#getBlockTint} box-blurs. Which soil a block is, is a property
 * of the <em>block</em>, so left alone it produces exactly the hard seam this mod exists
 * to remove. That seam is not only a biome-border phenomenon: where a worldgen mod mixes
 * its own dirt into vanilla dirt, the two interleave block by block through the same
 * cliff face, and every boundary between them is one pixel wide.
 *
 * <p>So this box-blurs in block space: sample the soil around a position and average the
 * colours those soils render as. Every soil votes, plain dirt included, which makes the
 * result a genuine interpolation between two dirts rather than a nudge away from a
 * shared base. Blocks that are not soil abstain rather than voting for dirt, so a soil
 * patch meeting stone or air keeps its character instead of being washed out.
 *
 * <p>A block always votes for itself, so a soil block surrounded by nothing but stone
 * still renders as itself.
 */
public final class SoilBlend {
    /**
     * Horizontal blur radius, in blocks. 2 matches Minecraft's default biome blend radius.
     */
    public static final int RADIUS = 2;

    /**
     * Vertical blur radius, in blocks.
     *
     * <p>Sampling only the same Y is enough for flat ground, where soil is a shallow skin
     * over stone, but it leaves a cliff face banded: each row blends along itself and not
     * with the rows above and below, so a vertical mix of two dirts stays as sharp as it
     * started. Exposed soil is mostly what players look at, so it gets a vertical vote too.
     *
     * <p>Held below {@link #RADIUS} deliberately. Cost is {@code (2r+1)^2 * (2v+1)} block
     * reads per tinted face and this result, unlike vanilla's biome blur, is not cached, so
     * matching the horizontal radius here would be five times the current cost rather than
     * three. If profiling says it matters, a per-position cache keyed like
     * {@code BlockTintCache} is the obvious next step.
     */
    public static final int VERTICAL_RADIUS = 1;

    private SoilBlend() {
    }

    /**
     * @param fallback the colour to use when nothing nearby is soil at all
     * @return the average of the surrounding soils' colours
     */
    public static int apply(BlockAndTintGetter view, BlockPos pos, int fallback) {
        int red = 0;
        int green = 0;
        int blue = 0;
        int samples = 0;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -VERTICAL_RADIUS; dy <= VERTICAL_RADIUS; dy++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    BlockState state = view.getBlockState(cursor);
                    SoilTints.Soil soil = SoilTints.of(state.getBlock());
                    if (soil == null) {
                        continue; // Not soil: abstains rather than voting for plain dirt.
                    }
                    int colour = soil.colour();
                    red += (colour >> 16) & 0xFF;
                    green += (colour >> 8) & 0xFF;
                    blue += colour & 0xFF;
                    samples++;
                }
            }
        }

        if (samples == 0) {
            return fallback;
        }
        return (channel(red, samples) << 16) | (channel(green, samples) << 8) | channel(blue, samples);
    }

    private static int channel(int total, int samples) {
        int value = (total + samples / 2) / samples;
        return Math.min(255, Math.max(0, value));
    }
}
