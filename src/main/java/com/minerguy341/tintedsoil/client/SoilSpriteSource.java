package com.minerguy341.tintedsoil.client;

import com.minerguy341.tintedsoil.TintedSoil;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Splits every soil texture into a greyscale and a pebble sprite, at atlas-stitch time.
 *
 * <p>The tint is multiplicative, so the texture it multiplies has to be greyscale: any
 * hue baked in would pull every soil colour toward it. Soil textures are brown, so the
 * brown is divided out here -- each pixel is projected onto the texture's own average
 * colour, which keeps the grain and discards the colour. Multiplying the result by that
 * same average reproduces the original texture; multiplying by anything else gives soil
 * of that colour with the original's grain.
 *
 * <p>Deriving at runtime rather than shipping the result is what makes the mod follow
 * resource packs. {@code run} is handed the live {@link ResourceManager}, so it sees the
 * player's whole pack stack: retinted ground matches whatever textures they actually
 * have, rather than whatever the mod happened to be built against.
 *
 * <p>Every texture is measured separately, and its measurements published to
 * {@link SoilTints}. That is what lets a soil reproduce itself exactly: its own average
 * colour, rendered on a greyscale normalised by its own mean, is the texture it came
 * from. It is also why there is no longer a table of hand-picked colours -- BWG's lush
 * dirt averages #534031 and its peat #514137, which is what those entries used to say.
 *
 * <p>Pebbles come out as a separate sprite. Soil textures carry a few pixels that are a
 * different material -- vanilla dirt's grey stones, and the grass fringe painted into
 * {@code grass_block_side} -- and grey times a brown tint is just brown, so the tint
 * would dissolve them into the soil. They are pulled out here and drawn back untinted by
 * a second model layer, so they keep their own colour in every biome.
 */
public class SoilSpriteSource implements SpriteSource {
    /** Bright enough that tints have room to lighten soil, not only darken it. */
    private static final double PEAK = 255.0;

    /**
     * How far off the soil hue a pixel must sit to count as another material.
     *
     * <p>Testing for neutral grey is not enough. Vanilla's pebbles happen to be neutral,
     * but Bare Bones' are #A09184 -- a warm grey that is not neutral at all, so a
     * {@code r == g == b} test finds none of them and paints them as soil. Measured
     * across vanilla dirt, vanilla coarse dirt and Bare Bones, soil pixels sit at most
     * 0.066 off the hue and every pebble at least 0.276, so anything in that gap
     * separates them. The same threshold pulls out the grass fringes that vanilla and
     * BOP paint into their grass block sides.
     */
    private static final double PEBBLE_THRESHOLD = 0.12;

    /** Refinement passes for the hue, so pebbles stop dragging it off true. */
    private static final int HUE_PASSES = 3;

    @Override
    public void run(ResourceManager resources, Output output) {
        SoilDefinitions definitions = SoilDefinitions.load(resources);
        if (definitions.textures().isEmpty()) {
            TintedSoil.LOGGER.warn("No soil textures defined; nothing retinted");
            SoilTints.publish(Map.of());
            return;
        }

        Map<ResourceLocation, SoilTints.Soil> measured = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, SoilDefinitions.Sprites> entry
                : definitions.textures().entrySet()) {
            SoilTints.Soil soil = derive(resources, output, entry.getKey(), entry.getValue());
            if (soil != null) {
                measured.put(entry.getKey(), soil);
            }
        }

        for (SoilDefinitions.Overlay overlay : definitions.overlays()) {
            emitOverlay(resources, output, overlay);
        }

        publish(definitions, measured);
    }

    /**
     * Maps each soil block onto the measurements of the texture it votes with.
     *
     * <p>A block whose mod is not installed simply is not in the registry, and a texture
     * that could not be read leaves the block on {@link SoilTints#FALLBACK} rather than
     * dropping it: its model has already been replaced by then, and a block that is not
     * in the map would render its greyscale untinted -- grey soil rather than brown.
     */
    private void publish(SoilDefinitions definitions, Map<ResourceLocation, SoilTints.Soil> measured) {
        Map<Block, SoilTints.Soil> soils = new HashMap<>();
        for (Map.Entry<ResourceLocation, ResourceLocation> entry : definitions.blocks().entrySet()) {
            Block block = TintedSoil.block(entry.getKey());
            if (block == null) {
                continue;   // the mod that adds it is not installed
            }
            soils.put(block, measured.getOrDefault(entry.getValue(), SoilTints.FALLBACK));
        }
        SoilTints.publish(soils);
        TintedSoil.LOGGER.info("Retinting {} soil blocks from {} textures",
                soils.size(), measured.size());
    }

    /**
     * Writes one texture's greyscale and pebble sprites.
     *
     * @return the colour that texture renders as and the mean of its greyscale
     */
    private SoilTints.Soil derive(ResourceManager resources, Output output,
                                  ResourceLocation texture, SoilDefinitions.Sprites sprites) {
        BufferedImage source = read(resources, file(texture));
        if (source == null) {
            // Missing because the mod that ships it is not installed, most of the time.
            // Nothing is emitted, and the model that would have used it is never baked.
            return null;
        }

        double[] hue = hueOf(source);
        if (hue == null) {
            return null;
        }
        double peak = peakProjection(source, hue);
        if (peak <= 0.0) {
            return null;
        }
        double scale = PEAK / peak;

        int width = source.getWidth();
        int height = source.getHeight();
        NativeImage grey = new NativeImage(width, height, false);
        NativeImage pebbles = new NativeImage(width, height, false);

        double total = 0.0;
        int counted = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = source.getRGB(x, y);
                if (isPebble(argb, hue)) {
                    // Another material: it keeps its own colour and takes no tint.
                    setPixel(pebbles, x, y, 0xFF000000 | (argb & 0xFFFFFF));
                    continue;
                }
                setPixel(pebbles, x, y, 0);
                int value = clamp((int) Math.round(project(argb, hue) * scale));
                setPixel(grey, x, y, 0xFF000000 | (value << 16) | (value << 8) | value);
                total += value / 255.0;
                counted++;
            }
        }

        if (counted == 0) {
            grey.close();
            pebbles.close();
            TintedSoil.LOGGER.warn("{} has no soil pixels at all; left untouched", texture);
            return null;
        }
        double mean = total / counted;

        // Texels behind a pebble are never seen directly, but they do feed mipmaps, so
        // they get the texture average rather than staying black and darkening it at
        // distance.
        int fill = clamp((int) Math.round(mean * 255.0));
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isPebble(source.getRGB(x, y), hue)) {
                    setPixel(grey, x, y, 0xFF000000 | (fill << 16) | (fill << 8) | fill);
                }
            }
        }

        add(output, sprites.soil(), grey, width, height);
        add(output, sprites.pebbles(), pebbles, width, height);

        int colour = pack(hue);
        TintedSoil.LOGGER.debug("Derived {}: {}x{} colour #{} mean luminance {}",
                texture, width, height, String.format("%06X", colour), String.format("%.4f", mean));
        return new SoilTints.Soil(colour, mean);
    }

    /**
     * Recovers a soil's overlay when it has none of its own.
     *
     * <p>Podzol is dirt with a hat: {@code podzol_side.png} is {@code dirt.png} byte for
     * byte below its top few rows, and vanilla ships no overlay for it because it
     * composites the two into one texture instead. Subtracting one from the other
     * recovers the crust -- keep a pixel where the two differ, drop it where they agree.
     *
     * <p>Doing that rather than tinting toward a podzol colour keeps podzol's dirt as
     * dirt: it blends with the ground around it like any other soil, and the crust sits
     * on top untinted, exactly as vanilla's does.
     */
    private void emitOverlay(ResourceManager resources, Output output,
                             SoilDefinitions.Overlay overlay) {
        BufferedImage over = read(resources, file(overlay.over()));
        if (over == null) {
            return;
        }
        BufferedImage under = overlay.under() == null ? null : read(resources, file(overlay.under()));
        if (overlay.under() != null && under == null) {
            TintedSoil.LOGGER.warn("Cannot subtract missing {} from {}; overlay skipped",
                    overlay.under(), overlay.over());
            return;
        }
        if (under != null && (under.getWidth() != over.getWidth()
                || under.getHeight() != over.getHeight())) {
            TintedSoil.LOGGER.warn("{} and {} are different sizes; overlay skipped",
                    overlay.over(), overlay.under());
            return;
        }

        int width = over.getWidth();
        int height = over.getHeight();
        NativeImage image = new NativeImage(width, height, false);
        int kept = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int colour = over.getRGB(x, y) & 0xFFFFFF;
                if (under != null && colour == (under.getRGB(x, y) & 0xFFFFFF)) {
                    setPixel(image, x, y, 0);
                } else {
                    setPixel(image, x, y, 0xFF000000 | colour);
                    kept++;
                }
            }
        }
        add(output, overlay.sprite(), image, width, height);
        TintedSoil.LOGGER.debug("Derived {}: {} of {} pixels kept",
                overlay.sprite(), kept, width * height);
    }

    /** The scalar whose product with the soil hue lands closest to this pixel. */
    private static double project(int argb, double[] hue) {
        double dot = ((argb >> 16) & 0xFF) * hue[0]
                + ((argb >> 8) & 0xFF) * hue[1]
                + (argb & 0xFF) * hue[2];
        double norm = hue[0] * hue[0] + hue[1] * hue[1] + hue[2] * hue[2];
        return 255.0 * dot / norm;
    }

    private static double peakProjection(BufferedImage image, double[] hue) {
        double peak = 0.0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if (!isPebble(argb, hue)) {
                    peak = Math.max(peak, project(argb, hue));
                }
            }
        }
        return peak;
    }

    /**
     * The direction the tint multiplies along: this texture's own average soil colour.
     *
     * <p>Starts from the average of every pixel and refines. Each pass drops whatever now
     * looks like another material and re-averages what is left, so the off-hue pixels stop
     * dragging the hue toward them. It converges immediately on a plain dirt texture --
     * soil is the overwhelming majority of it -- and the passes are what make it right for
     * a grass block side, where a quarter of the pixels are fringe rather than soil.
     */
    private static double[] hueOf(BufferedImage image) {
        double[] hue = null;
        for (int pass = 0; pass <= HUE_PASSES; pass++) {
            double r = 0.0;
            double g = 0.0;
            double b = 0.0;
            int count = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    if (hue != null && isPebble(argb, hue)) {
                        continue;
                    }
                    r += (argb >> 16) & 0xFF;
                    g += (argb >> 8) & 0xFF;
                    b += argb & 0xFF;
                    count++;
                }
            }
            if (count == 0) {
                return hue;   // everything looked off-hue: keep the previous estimate
            }
            hue = new double[]{r / count, g / count, b / count};
        }
        return hue;
    }

    /**
     * Whether a pixel is another material rather than soil.
     *
     * <p>Soil pixels are the soil colour at some brightness, so they lie along the hue.
     * A stone or a grass fringe is a different colour entirely, and shows up as the part
     * of the pixel that no amount of scaling the hue can account for.
     */
    private static boolean isPebble(int argb, double[] hue) {
        double r = (argb >> 16) & 0xFF;
        double g = (argb >> 8) & 0xFF;
        double b = argb & 0xFF;
        double magnitude = Math.sqrt(r * r + g * g + b * b);
        if (magnitude <= 0.0) {
            return false;   // pure black: no colour to be off-hue from
        }
        double s = project(argb, hue) / 255.0;
        double dr = r - s * hue[0];
        double dg = g - s * hue[1];
        double db = b - s * hue[2];
        return Math.sqrt(dr * dr + dg * dg + db * db) / magnitude > PEBBLE_THRESHOLD;
    }

    private static int pack(double[] colour) {
        return (clamp((int) Math.round(colour[0])) << 16)
                | (clamp((int) Math.round(colour[1])) << 8)
                | clamp((int) Math.round(colour[2]));
    }

    /** A texture id names a sprite; the file it comes from needs the folder and suffix. */
    private static ResourceLocation file(ResourceLocation texture) {
        return TintedSoil.location(texture.getNamespace(),
                "textures/" + texture.getPath() + ".png");
    }

    private static BufferedImage read(ResourceManager resources, ResourceLocation id) {
        // ImageIO rather than NativeImage.read: BufferedImage#getRGB is plain ARGB on every
        // supported version, while NativeImage's accessor was renamed and its channel order
        // is not what it looks like. Same reasoning as SoilColormap.
        Optional<Resource> resource = resources.getResource(id);
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream stream = resource.get().open()) {
            return ImageIO.read(stream);
        } catch (Exception exception) {
            TintedSoil.LOGGER.warn("Could not read {}", id, exception);
            return null;
        }
    }

    private static void add(Output output, ResourceLocation id, NativeImage image, int width, int height) {
        FrameSize frame = new FrameSize(width, height);
        // The supplier gained a SpriteResourceLoader argument in 1.21, and the metadata
        // parameter went from AnimationMetadataSection to the broader ResourceMetadata.
        //? if <1.21 {
        /*output.add(id, () -> new SpriteContents(id, frame, image,
                net.minecraft.client.resources.metadata.animation.AnimationMetadataSection.EMPTY));
        *///?} else {
        output.add(id, loader -> new SpriteContents(id, frame, image,
                net.minecraft.server.packs.resources.ResourceMetadata.EMPTY));
        //?}
    }

    private static void setPixel(NativeImage image, int x, int y, int argb) {
        // NativeImage is ABGR in memory whatever the accessor happens to be called.
        int abgr = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
        //? if <1.21.6 {
        image.setPixelRGBA(x, y, abgr);
        //?} else {
        /*image.setPixelABGR(x, y, abgr);
        *///?}
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }

    //? if <1.21.6 {
    @Override
    public net.minecraft.client.renderer.texture.atlas.SpriteSourceType type() {
        return SoilSpriteSources.TYPE;
    }
    //?}
    //? if >=1.21.6 {
    /*@Override
    public com.mojang.serialization.MapCodec<? extends SpriteSource> codec() {
        return SoilSpriteSources.CODEC;
    }
    *///?}
}
