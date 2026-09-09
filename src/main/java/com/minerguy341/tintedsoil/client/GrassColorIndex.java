package com.minerguy341.tintedsoil.client;

import net.minecraft.world.level.GrassColor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inverts vanilla's grass colormap: given a colour, finds the (temperature, downfall) cell
 * of {@code grass.png} that produces it.
 *
 * <p>This is how a biome's declared {@code grass_color} chooses its soil. Vanilla's grass
 * colormap and this mod's soil colormap share an index space, so asking "which climate does
 * this grass colour look like?" and sampling the soil colormap at the same cell makes soil
 * follow whatever art direction a biome author chose — including Biomes O' Plenty's and
 * Oh The Biomes We've Gone's — without this mod knowing anything about their biomes.
 *
 * <p>Only biomes that actually override their grass colour reach here, so the search runs a
 * handful of times per session and is cached per colour.
 */
public final class GrassColorIndex {
    /** Vanilla returns this from {@link GrassColor#get} before {@code grass.png} has loaded. */
    private static final int UNLOADED = -65281;
    private static final int SIZE = 256;

    private static final Map<Integer, float[]> CACHE = new ConcurrentHashMap<>();

    private GrassColorIndex() {
    }

    public static void invalidate() {
        CACHE.clear();
    }

    /**
     * @return {@code {temperature, downfall}} whose grass colour is closest to {@code rgb},
     *         or {@code null} if the grass colormap is not loaded yet.
     */
    public static float[] climateFor(int rgb) {
        float[] cached = CACHE.get(rgb);
        if (cached != null) {
            return cached;
        }

        int target = rgb & 0xFFFFFF;
        int targetR = (target >> 16) & 0xFF;
        int targetG = (target >> 8) & 0xFF;
        int targetB = target & 0xFF;

        float bestTemperature = 0.0F;
        float bestDownfall = 0.0F;
        long bestDistance = Long.MAX_VALUE;

        for (int x = 0; x < SIZE; x++) {
            float temperature = 1.0F - x / (float) (SIZE - 1);
            // Only y >= x is reachable, since downfall is scaled by temperature.
            for (int y = x; y < SIZE; y++) {
                float scaled = 1.0F - y / (float) (SIZE - 1);
                float downfall = temperature > 1.0e-6F ? scaled / temperature : 0.0F;
                if (downfall > 1.0F) {
                    continue;
                }

                int colour = GrassColor.get(temperature, downfall);
                if (colour == UNLOADED) {
                    return null; // Resources not ready; try again on a later frame.
                }

                int dr = ((colour >> 16) & 0xFF) - targetR;
                int dg = ((colour >> 8) & 0xFF) - targetG;
                int db = (colour & 0xFF) - targetB;
                long distance = (long) dr * dr + (long) dg * dg + (long) db * db;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestTemperature = temperature;
                    bestDownfall = downfall;
                    if (distance == 0) {
                        break;
                    }
                }
            }
        }

        float[] result = {bestTemperature, bestDownfall};
        CACHE.put(rgb, result);
        return result;
    }
}
