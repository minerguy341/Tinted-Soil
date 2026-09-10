package com.minerguy341.tintedsoil.client;

import net.minecraft.world.level.block.Block;

import java.util.Map;

/**
 * What each soil block looks like, measured from the texture it is actually wearing.
 *
 * <p>{@link SoilSpriteSource} fills this in during atlas stitching, which happens on
 * every resource reload and always before a block is rendered. Two numbers per block:
 *
 * <ul>
 *   <li>{@code colour} -- the average colour of the block's soil texture. This is what
 *       the block <em>votes</em> with in {@link SoilBlend}, and what it renders as when
 *       everything around it is the same soil.
 *   <li>{@code meanLuminance} -- the average brightness of the greyscale derived from
 *       that texture. Dividing a colour by it produces the tint that lands the block's
 *       average rendered colour on that colour exactly.
 * </ul>
 *
 * <p>Both are per texture rather than global, which is what lets each soil reproduce
 * itself. Coarse dirt is darker than dirt because its own texture is darker, not because
 * anything says so; BWG's pale sandy dirt is reachable because it is tinted on a
 * greyscale made from sandy dirt rather than from vanilla's.
 */
public final class SoilTints {
    /** Measured from vanilla 1.21.1 dirt.png; see tools/generate_assets.py. */
    public static final double DEFAULT_MEAN_LUMINANCE = 0.7232;

    /** Mean colour of vanilla 1.21.1 dirt.png's soil pixels. */
    public static final int DEFAULT_SOIL_COLOR = 0x876041;

    /**
     * One soil block's measurements.
     *
     * @param colour        the colour this block renders as, unblended
     * @param meanLuminance the mean of the greyscale that colour is multiplied onto
     */
    public record Soil(int colour, double meanLuminance) {
        /** The tint that renders {@code colour} on this block's greyscale. */
        public int tintFor(int colour) {
            return (channel(colour >> 16) << 16) | (channel(colour >> 8) << 8) | channel(colour);
        }

        private int channel(int shifted) {
            int value = (int) Math.round((shifted & 0xFF) / this.meanLuminance);
            return Math.min(255, Math.max(0, value));
        }
    }

    /** A soil whose texture could not be read, so it renders as vanilla dirt would. */
    public static final Soil FALLBACK = new Soil(DEFAULT_SOIL_COLOR, DEFAULT_MEAN_LUMINANCE);

    /**
     * Replaced wholesale on each reload rather than mutated, so a chunk being meshed
     * while a reload is in flight sees one consistent set of measurements or the other.
     */
    private static volatile Map<Block, Soil> soils = Map.of();

    private SoilTints() {
    }

    public static void publish(Map<Block, Soil> measured) {
        soils = Map.copyOf(measured);
    }

    /** @return this block's measurements, or {@code null} if it is not soil at all. */
    public static Soil of(Block block) {
        return soils.get(block);
    }

    public static boolean isSoil(Block block) {
        return soils.containsKey(block);
    }

    /** Every soil block, for callers that need the set rather than one lookup. */
    public static Iterable<Block> blocks() {
        return soils.keySet();
    }
}
