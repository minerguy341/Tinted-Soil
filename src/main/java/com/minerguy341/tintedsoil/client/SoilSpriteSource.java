package com.minerguy341.tintedsoil.client;

import com.minerguy341.tintedsoil.TintedSoil;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Optional;

/**
 * Builds the soil sprites out of vanilla's own dirt textures, at atlas-stitch time.
 *
 * <p>The tint is multiplicative, so the texture it multiplies has to be greyscale: any hue
 * baked in would pull every soil colour toward it. Vanilla's dirt is brown, so the brown is
 * divided out here -- each pixel is projected onto the average soil hue, which keeps the
 * grain and discards the colour. Multiplying the result by that same hue reproduces vanilla
 * dirt; multiplying by anything else gives soil of that colour with vanilla's grain.
 *
 * <p>Deriving at runtime rather than shipping the result is what makes the mod follow
 * resource packs. {@code run} is handed the live {@link ResourceManager}, so it sees the
 * player's whole pack stack: replaced ground matches whatever dirt they actually have,
 * rather than whatever dirt the mod happened to be built against.
 *
 * <p>Pebbles come out as a separate sprite. Vanilla's dirt has a few flat grey pixels that
 * read as small stones, and grey times a brown tint is just brown -- the tint would dissolve
 * them into the soil. They are pulled out here and drawn back untinted by a second model
 * layer, so they stay stone-coloured in every biome.
 */
public class SoilSpriteSource implements SpriteSource {
    private static final ResourceLocation DIRT = vanilla("dirt");
    private static final ResourceLocation COARSE_DIRT = vanilla("coarse_dirt");

    /** Bright enough that tints have room to lighten soil, not only darken it. */
    private static final double PEAK = 255.0;

    /**
     * How far off the soil hue a pixel must sit to count as a stone rather than soil.
     *
     * <p>Testing for neutral grey is not enough. Vanilla's pebbles happen to be neutral, but
     * Bare Bones' are #A09184 -- a warm grey that is not neutral at all, so a
     * {@code r == g == b} test finds none of them and paints them as soil. Measured across
     * vanilla dirt, vanilla coarse dirt and Bare Bones, soil pixels sit at most 0.066 off
     * the hue and every pebble at least 0.276, so anything in that gap separates them.
     */
    private static final double PEBBLE_THRESHOLD = 0.12;

    /** Refinement passes for the hue, so pebbles stop dragging it off true. */
    private static final int HUE_PASSES = 3;

    @Override
    public void run(ResourceManager resources, Output output) {
        BufferedImage dirt = read(resources, DIRT);
        if (dirt == null) {
            // Without plain dirt there is no hue to divide out and no mean worth
            // publishing. Adding nothing leaves whatever is already on the atlas standing.
            TintedSoil.LOGGER.warn("Could not read {}; soil sprites left as they are", DIRT);
            return;
        }
        BufferedImage coarse = read(resources, COARSE_DIRT);

        double[] hue = hueOf(dirt);

        // One scale across both textures, so coarse soil keeps its brightness relative to
        // plain soil instead of being renormalised to match it. That is what makes vanilla's
        // coarse dirt come out darker than its dirt with no second tint and no second hue.
        double peak = Math.max(peakProjection(dirt, hue),
                coarse == null ? 0.0 : peakProjection(coarse, hue));
        if (peak <= 0.0) {
            return;
        }
        double scale = PEAK / peak;

        double luminance = emit(output, dirt, hue, scale, "tinted_dirt");
        SoilTextures.setMeanLuminance(luminance);

        // The hue is this dirt's own average colour, so publishing it as the plain-soil
        // tint is what makes replaced ground match the dirt beside it. Without this the
        // grain would follow a resource pack while the colour stayed on whatever vanilla
        // looked like when the mod was built -- Bare Bones' vivid #9F6A36 rendered as
        // vanilla's duller #876041.
        SoilTextures.setDefaultSoilColor(pack(hue));
        double coarseLuminance = coarse == null
                ? 0.0 : emit(output, coarse, hue, scale, "tinted_coarse_dirt");

        TintedSoil.LOGGER.info(
                "Derived soil from {}x{} dirt: hue #{}, mean luminance {} (coarse {})",
                dirt.getWidth(), dirt.getHeight(),
                String.format("%02X%02X%02X", Math.round(hue[0]), Math.round(hue[1]), Math.round(hue[2])),
                String.format("%.4f", luminance), String.format("%.4f", coarseLuminance));
    }

    /**
     * Writes one texture's greyscale sprite and its pebble sprite.
     *
     * @return mean luminance of the greyscale, ignoring texels hidden behind a pebble
     */
    private double emit(Output output, BufferedImage source, double[] hue, double scale, String name) {
        int width = source.getWidth();
        int height = source.getHeight();
        NativeImage grey = new NativeImage(width, height, false);
        NativeImage pebbles = new NativeImage(width, height, false);

        double total = 0.0;
        int counted = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = source.getRGB(x, y);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                if (isPebble(r, g, b, hue)) {
                    // A stone, not soil: it keeps its own colour and takes no tint.
                    setPixel(pebbles, x, y, 0xFF000000 | (argb & 0xFFFFFF));
                    continue;
                }
                setPixel(pebbles, x, y, 0);
                int value = clamp((int) Math.round(project(r, g, b, hue) * scale));
                setPixel(grey, x, y, 0xFF000000 | (value << 16) | (value << 8) | value);
                total += value / 255.0;
                counted++;
            }
        }

        if (counted == 0) {
            grey.close();
            pebbles.close();
            return SoilTextures.DEFAULT_MEAN_LUMINANCE;
        }
        double mean = total / counted;

        // Texels behind a pebble are never seen directly, but they do feed mipmaps, so they
        // get the texture average rather than staying black and darkening it at distance.
        int fill = clamp((int) Math.round(mean * 255.0));
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = source.getRGB(x, y);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                if (isPebble(r, g, b, hue)) {
                    setPixel(grey, x, y, 0xFF000000 | (fill << 16) | (fill << 8) | fill);
                }
            }
        }

        add(output, TintedSoil.id("block/" + name), grey, width, height);
        add(output, TintedSoil.id("block/" + name + "_pebbles"), pebbles, width, height);
        return mean;
    }

    /** The scalar whose product with the soil hue lands closest to this pixel. */
    private static double project(int r, int g, int b, double[] hue) {
        double dot = r * hue[0] + g * hue[1] + b * hue[2];
        double norm = hue[0] * hue[0] + hue[1] * hue[1] + hue[2] * hue[2];
        return 255.0 * dot / norm;
    }

    private static double peakProjection(BufferedImage image, double[] hue) {
        double peak = 0.0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                if (isPebble(r, g, b, hue)) {
                    continue;
                }
                peak = Math.max(peak, project(r, g, b, hue));
            }
        }
        return peak;
    }

    /**
     * The direction the tint multiplies along: this dirt's own average soil colour.
     *
     * <p>Starts from the average of every pixel and refines. Each pass drops whatever now
     * looks like a stone and re-averages what is left, so the handful of off-hue pixels stop
     * dragging the hue toward grey. It converges immediately on real textures -- soil is the
     * overwhelming majority of every dirt texture -- but the passes make it robust to a pack
     * with far more stones than vanilla's seven.
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
                    int pr = (argb >> 16) & 0xFF;
                    int pg = (argb >> 8) & 0xFF;
                    int pb = argb & 0xFF;
                    if (hue != null && isPebble(pr, pg, pb, hue)) {
                        continue;
                    }
                    r += pr;
                    g += pg;
                    b += pb;
                    count++;
                }
            }
            if (count == 0) {
                return hue;   // everything looked like a stone: keep the previous estimate
            }
            hue = new double[]{r / count, g / count, b / count};
        }
        return hue;
    }

    /**
     * Whether a pixel is a stone rather than soil.
     *
     * <p>Soil pixels are the dirt colour at some brightness, so they lie along the hue.
     * A stone is a different colour entirely, and shows up as the part of the pixel that no
     * amount of scaling the hue can account for.
     */
    private static boolean isPebble(int r, int g, int b, double[] hue) {
        double magnitude = Math.sqrt((double) r * r + (double) g * g + (double) b * b);
        if (magnitude <= 0.0) {
            return false;   // pure black: no colour to be off-hue from
        }
        double s = project(r, g, b, hue) / 255.0;
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

    private static ResourceLocation vanilla(String name) {
        String path = "textures/block/" + name + ".png";
        //? if <1.21 {
        /*return new ResourceLocation("minecraft", path);
        *///?} else
        return ResourceLocation.withDefaultNamespace(path);
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
