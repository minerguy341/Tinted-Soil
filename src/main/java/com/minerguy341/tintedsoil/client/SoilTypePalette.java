package com.minerguy341.tintedsoil.client;

import com.minerguy341.tintedsoil.block.SoilType;

/**
 * Characteristic colours for each soil type.
 *
 * <p>Values are the colour you want to <em>see</em>; they are divided by the base texture's
 * mean luminance on the way out, exactly like the colormap, because the textures are
 * greyscale and the tint is doing all the work.
 *
 * <p>{@link SoilType#DEFAULT} carries a real colour rather than deferring to the biome.
 * Soil colour no longer varies by climate, so ordinary dirt is a value like any other type,
 * and {@link SoilTypeBlend} can average across a neighbourhood instead of pulling away from
 * a separate base. That is what puts a gradient between two adjacent dirts rather than a
 * seam: a block surrounded by lush soil renders lush, one surrounded by plain dirt renders
 * as vanilla dirt, and anything in between lands proportionally between the two.
 */
public final class SoilTypePalette {
    private static final int[] RENDERED = new int[SoilType.values().length];

    static {
        // Unused: DEFAULT is read from SoilTextures instead, because plain soil has to be
        // whatever colour the loaded dirt texture is rather than a value fixed at build
        // time. Kept as the array's shape, and as the value that would apply if the runtime
        // derivation never ran.
        RENDERED[SoilType.DEFAULT.ordinal()] = SoilTextures.DEFAULT_SOIL_COLOR;
        // Unused: podzol's body is plain dirt -- vanilla's podzol_side is dirt.png below its
        // top few rows -- so it takes the dirt colour and wears its crust as a model layer
        // instead. Tinting toward orange-brown here would have double-counted the crust and
        // stopped podzol blending with the ordinary ground it is made of.
        RENDERED[SoilType.PODZOL.ordinal()] = SoilTextures.DEFAULT_SOIL_COLOR;
        // Measured from biomeswevegone:textures/block/lush_dirt.png -- the mean of its 249
        // soil pixels, with its 7 off-hue ones excluded the same way the tint excludes
        // vanilla's pebbles. Rendering this on the derived greyscale reproduces BWG's own
        // lush dirt darkness, so replaced lush ground matches the lush ground beside it.
        RENDERED[SoilType.LUSH.ordinal()] = 0x534031;       // BWG lush dirt
        // Darkened from #C2A878 to fit the brightness ceiling: a tint is stored as
        // `rendered / MEAN_LUMINANCE` in 8 bits, and matching vanilla's high-contrast grain
        // dropped that mean to 0.7232, so no rendered channel above ~184 survives the
        // multiply. Same hue, scaled to fit rather than clipped, which would have skewed it.
        RENDERED[SoilType.SANDY.ordinal()] = 0xB89F72;      // pale and sandy
        // Measured from biomeswevegone:textures/block/peat.png, the mean of all 256 pixels:
        // unlike the dirt textures it carries no off-hue specks at all, so nothing is
        // excluded. Browner and lighter than the near-black it replaced, which was a guess
        // at bog soil rather than a match for the block being stood in for.
        RENDERED[SoilType.PEAT.ordinal()] = 0x514137;       // BWG peat
        RENDERED[SoilType.ORIGIN.ordinal()] = 0x8A6A45;     // warm mid brown
    }

    private SoilTypePalette() {
    }

    /** @return the tint that renders this type's colour on the greyscale soil texture. */
    public static int tint(SoilType type) {
        // Plain soil tracks the texture in play; the named types are deliberate colours for
        // specific modded soils and stay put.
        int rendered = type == SoilType.DEFAULT || type == SoilType.PODZOL
                ? SoilTextures.defaultSoilColor()
                : RENDERED[type.ordinal()];
        return SoilColormap.toTint(rendered);
    }
}
