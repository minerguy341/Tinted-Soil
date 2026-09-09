package com.minerguy341.tintedsoil.client;

import com.minerguy341.tintedsoil.block.SoilType;

/**
 * Characteristic colours for each soil type.
 *
 * <p>Values are the colour you want to <em>see</em>; they are divided by the base texture's
 * mean luminance on the way out, exactly like the colormap, because the textures are
 * greyscale and the tint is doing all the work.
 *
 * <p>{@link SoilType#DEFAULT} has no entry: plain dirt contributes nothing and leaves the
 * biome colour untouched, which is what keeps ordinary terrain looking exactly as it did.
 */
public final class SoilTypePalette {
    /**
     * How far a fully-typed block is pulled from its biome colour toward its type colour.
     * Below 1 on purpose: a lush soil in a cold biome should still read as cold, so type is
     * a character on top of climate rather than a replacement for it.
     */
    public static final float STRENGTH = 0.6F;

    private static final int[] RENDERED = new int[SoilType.values().length];

    static {
        RENDERED[SoilType.DEFAULT.ordinal()] = -1;          // sentinel: use the biome colour
        RENDERED[SoilType.PODZOL.ordinal()] = 0x6B4A28;     // dark orange-brown
        RENDERED[SoilType.LUSH.ordinal()] = 0x57452C;       // dark, rich
        RENDERED[SoilType.SANDY.ordinal()] = 0xC2A878;      // pale and sandy
        RENDERED[SoilType.PEAT.ordinal()] = 0x3B3025;       // near-black bog soil
        RENDERED[SoilType.ORIGIN.ordinal()] = 0x8A6A45;     // warm mid brown
    }

    private SoilTypePalette() {
    }

    /** @return the tint for this type, or -1 if it should defer to the biome colour. */
    public static int tint(SoilType type) {
        int rendered = RENDERED[type.ordinal()];
        return rendered < 0 ? -1 : SoilColormap.toTint(rendered);
    }
}
