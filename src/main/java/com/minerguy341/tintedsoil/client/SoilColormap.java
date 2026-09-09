package com.minerguy341.tintedsoil.client;

import com.minerguy341.tintedsoil.TintedSoil;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * Loads and samples {@code tintedsoil:textures/colormap/soil.png}.
 *
 * <p>The colormap is indexed exactly like vanilla's {@code grass.png} (see
 * {@code GrassColor#get}), so it can be edited by a resource pack with the same tooling and
 * the same mental model. {@code tools/generate_assets.py} regenerates the shipped one.
 *
 * <p>The image is read with {@code ImageIO} rather than {@code NativeImage} on purpose:
 * {@code NativeImage}'s pixel accessor was renamed and its channel order changed between
 * the supported versions, while {@code BufferedImage#getRGB} is plain ARGB everywhere.
 * If anything goes wrong, {@link #fallback} reproduces the same colour model in code so the
 * mod degrades to correct-looking soil instead of magenta.
 */
public final class SoilColormap {
    private static final int SIZE = 256;

    // Anchors mirrored from tools/generate_assets.py -- keep the two in sync; the generator
    // prints TEMPERATE_DRY on every run for exactly that reason. It is derived rather than
    // chosen: it is the dry-temperate anchor that lands plains on vanilla dirt (#866043).
    private static final int[] TEMPERATE = {132, 81, 51};
    private static final int[] HUMID = {104, 72, 46};
    private static final int[] SAND = {206, 183, 138};
    private static final int[] COLD = {146, 138, 128};
    private static final double SAND_TEMP_EXP = 1.5;
    private static final double SAND_DRY_EXP = 3.0;
    private static final double COLD_EXP = 2.0;

    private static volatile int[] pixels;

    private SoilColormap() {
    }

    /** Dropped whenever the client clears its tint caches, which includes resource reloads. */
    public static void invalidate() {
        pixels = null;
    }

    /**
     * The soil colour with no world context: inventory icons, and the fallback when a block
     * is asked for its tint outside a level.
     *
     * <p>Reads the palette rather than the colormap. The colormap is pre-divided by a
     * build-time luminance, but the texture is now derived at runtime from the player's own
     * dirt.png, so only a value that goes through {@link #toTint} at read time is consistent
     * with what is actually on the atlas.
     */
    public static int defaultColor() {
        return SoilTypePalette.tint(com.minerguy341.tintedsoil.block.SoilType.DEFAULT);
    }

    public static int get(double temperature, double downfall) {
        int[] map = pixels;
        if (map == null) {
            map = load();
            pixels = map;
        }

        double scaled = downfall * temperature;
        int x = (int) ((1.0 - temperature) * (SIZE - 1));
        int y = (int) ((1.0 - scaled) * (SIZE - 1));
        int index = (y << 8) | x;
        return index < 0 || index >= map.length ? fallbackColor(temperature, downfall) : map[index];
    }

    private static int[] load() {
        ResourceLocation id = TintedSoil.id("textures/colormap/soil.png");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getResourceManager() != null) {
            try (InputStream stream = minecraft.getResourceManager().open(id)) {
                BufferedImage image = ImageIO.read(stream);
                if (image != null && image.getWidth() >= SIZE && image.getHeight() >= SIZE) {
                    int[] map = new int[SIZE * SIZE];
                    for (int y = 0; y < SIZE; y++) {
                        for (int x = 0; x < SIZE; x++) {
                            map[(y << 8) | x] = image.getRGB(x, y) & 0xFFFFFF;
                        }
                    }
                    return map;
                }
                TintedSoil.LOGGER.warn("Soil colormap {} is not at least {}x{}; using the built-in model", id, SIZE, SIZE);
            } catch (Exception exception) {
                TintedSoil.LOGGER.warn("Could not read soil colormap {}; using the built-in model", id, exception);
            }
        }
        return fallback();
    }

    private static int[] fallback() {
        int[] map = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double temperature = 1.0 - x / (double) (SIZE - 1);
                double scaled = 1.0 - y / (double) (SIZE - 1);
                double downfall = y < x ? 1.0 : (temperature > 1.0e-6 ? scaled / temperature : 0.0);
                map[(y << 8) | x] = fallbackColor(temperature, downfall);
            }
        }
        return map;
    }

    /** The colour model from tools/generate_assets.py, evaluated directly. */
    private static int fallbackColor(double temperature, double downfall) {
        double t = clamp(temperature);
        double d = clamp(downfall);

        double[] colour = lerp(TEMPERATE, HUMID, d);
        double sand = Math.pow(t, SAND_TEMP_EXP) * Math.pow(1.0 - d, SAND_DRY_EXP);
        colour = lerp(colour, SAND, sand);
        colour = lerp(colour, COLD, Math.pow(1.0 - t, COLD_EXP));

        int r = channel(colour[0]);
        int g = channel(colour[1]);
        int b = channel(colour[2]);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Converts a colour you want to see into the tint that produces it, by dividing out the
     * greyscale texture's mean luminance. The stored colormap is pre-divided the same way.
     */
    public static int toTint(int rendered) {
        int r = channel((rendered >> 16) & 0xFF);
        int g = channel((rendered >> 8) & 0xFF);
        int b = channel(rendered & 0xFF);
        return (r << 16) | (g << 8) | b;
    }

    private static int channel(double rendered) {
        // Measured from the soil texture actually on the atlas, which SoilSpriteSource
        // derives from whatever dirt.png the player's resource packs supply. A darker dirt
        // needs a brighter tint to land on the same rendered colour, so this cannot be the
        // compile-time constant it used to be.
        return Math.min(255, Math.max(0, (int) Math.round(rendered / SoilTextures.meanLuminance())));
    }

    private static double clamp(double value) {
        return value < 0.0 ? 0.0 : Math.min(value, 1.0);
    }

    private static double[] lerp(int[] from, int[] to, double t) {
        return lerp(new double[]{from[0], from[1], from[2]}, to, t);
    }

    private static double[] lerp(double[] from, int[] to, double t) {
        return new double[]{
                from[0] + (to[0] - from[0]) * t,
                from[1] + (to[1] - from[1]) * t,
                from[2] + (to[2] - from[2]) * t,
        };
    }
}
