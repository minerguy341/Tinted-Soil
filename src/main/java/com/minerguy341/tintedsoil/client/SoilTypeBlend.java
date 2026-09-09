package com.minerguy341.tintedsoil.client;

import com.minerguy341.tintedsoil.block.SoilType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blurs soil types across neighbouring blocks.
 *
 * <p>Vanilla can only blend tints that are a function of {@code Biome}, because that is what
 * {@code ClientLevel#getBlockTint} box-blurs. Soil type is a property of the <em>block</em>,
 * so left alone it would produce exactly the hard seam this mod exists to remove — a wall of
 * peat meeting a wall of sandy soil with a one-pixel edge between them. This does the
 * equivalent blur in block space: sample the types around a position, mix their colours by
 * how many of each there are, and pull the biome colour toward the result.
 *
 * <p>Cost is {@code (2r+1)^2} block reads per tinted face, which is the same order as
 * vanilla's biome blur but uncached — see the note on {@link #RADIUS}. Blocks that are not
 * ours abstain rather than voting for plain dirt, so a soil patch meeting stone keeps its
 * character instead of being washed out.
 */
public final class SoilTypeBlend {
    /**
     * Horizontal blur radius, in blocks. 2 matches Minecraft's default biome blend radius.
     *
     * <p>Only the same Y level is sampled. Soil strata are a few blocks deep before hitting
     * stone, so a vertical blur would mostly average in blocks that are not soil at all,
     * and it would triple the cost. Unlike vanilla's biome blur this result is not cached;
     * if profiling shows it matters, a per-position cache keyed like {@code BlockTintCache}
     * is the obvious next step.
     */
    public static final int RADIUS = 2;

    private static final SoilType[] TYPES = SoilType.values();

    private SoilTypeBlend() {
    }

    /**
     * @param base the biome-blended soil tint
     * @return {@code base} pulled toward the surrounding soil types' colours
     */
    public static int apply(BlockAndTintGetter view, BlockPos pos, int base) {
        float[] weights = new float[TYPES.length];
        int samples = 0;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int y = pos.getY();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                cursor.set(pos.getX() + dx, y, pos.getZ() + dz);
                BlockState state = view.getBlockState(cursor);
                if (!state.hasProperty(SoilType.PROPERTY)) {
                    continue; // Not one of ours: abstains rather than voting for plain dirt.
                }
                weights[state.getValue(SoilType.PROPERTY).ordinal()]++;
                samples++;
            }
        }

        if (samples == 0) {
            return base;
        }

        float typed = 0.0F;
        float red = 0.0F;
        float green = 0.0F;
        float blue = 0.0F;
        for (int i = 0; i < TYPES.length; i++) {
            float weight = weights[i];
            if (weight == 0.0F) {
                continue;
            }
            int tint = SoilTypePalette.tint(TYPES[i]);
            if (tint < 0) {
                continue; // DEFAULT: leaves the biome colour alone.
            }
            red += weight * ((tint >> 16) & 0xFF);
            green += weight * ((tint >> 8) & 0xFF);
            blue += weight * (tint & 0xFF);
            typed += weight;
        }

        if (typed == 0.0F) {
            return base; // Ordinary dirt all round: the biome colour, untouched.
        }

        // Scaled by how much of the neighbourhood is typed, so the pull fades out gradually
        // toward plain terrain instead of stopping at the last typed block.
        float strength = SoilTypePalette.STRENGTH * (typed / samples);
        return lerp(base, red / typed, green / typed, blue / typed, strength);
    }

    private static int lerp(int base, float red, float green, float blue, float amount) {
        int r = channel((base >> 16) & 0xFF, red, amount);
        int g = channel((base >> 8) & 0xFF, green, amount);
        int b = channel(base & 0xFF, blue, amount);
        return (r << 16) | (g << 8) | b;
    }

    private static int channel(int from, float to, float amount) {
        int value = Math.round(from + (to - from) * amount);
        return Math.min(255, Math.max(0, value));
    }
}
