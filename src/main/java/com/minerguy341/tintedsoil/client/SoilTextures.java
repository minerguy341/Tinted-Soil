package com.minerguy341.tintedsoil.client;

/**
 * The mean luminance of the soil texture currently on the atlas.
 *
 * <p>Every palette colour is quoted as the colour you want to see and divided by this on
 * the way to a tint, so it has to describe the texture actually loaded. When the texture is
 * derived at runtime from whatever {@code dirt.png} the player's resource packs provide,
 * that is no longer a constant: a darker dirt texture needs a brighter tint to land on the
 * same rendered colour.
 *
 * <p>{@link SoilSpriteSource} publishes the measured value during atlas stitching, which
 * happens before any block is rendered. The default below is the value derived from vanilla
 * 1.21.1's own dirt.png, so the mod still looks right if the sprite source never runs.
 */
public final class SoilTextures {
    /** Measured from vanilla 1.21.1 dirt.png; see tools/generate_assets.py. */
    public static final double DEFAULT_MEAN_LUMINANCE = 0.7232;

    /** Mean colour of vanilla 1.21.1 dirt.png's soil pixels. */
    public static final int DEFAULT_SOIL_COLOR = 0x876041;

    private static volatile double meanLuminance = DEFAULT_MEAN_LUMINANCE;
    private static volatile int defaultSoilColor = DEFAULT_SOIL_COLOR;

    private SoilTextures() {
    }

    public static double meanLuminance() {
        return meanLuminance;
    }

    /**
     * What plain soil should render as: the average colour of the dirt texture in play.
     *
     * <p>Not a constant, for the same reason the luminance is not. Deriving the grain from
     * a resource pack while keeping vanilla's colour gives soil that matches the pack's dirt
     * in texture and misses it entirely in hue.
     */
    public static int defaultSoilColor() {
        return defaultSoilColor;
    }

    public static void setDefaultSoilColor(int rgb) {
        defaultSoilColor = rgb & 0xFFFFFF;
    }

    public static void setMeanLuminance(double value) {
        // A texture that is entirely black would divide every tint to infinity; refusing it
        // keeps a malformed resource pack from turning all soil white.
        meanLuminance = value > 0.05 ? value : DEFAULT_MEAN_LUMINANCE;
    }
}
