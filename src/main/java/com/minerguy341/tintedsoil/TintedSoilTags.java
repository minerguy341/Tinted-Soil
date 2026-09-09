package com.minerguy341.tintedsoil;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * The two tags that drive block replacement.
 *
 * <p>These are ordinary datapack tags, which makes them the mod's configuration surface:
 * add a modded soil to one of them and it starts being replaced, empty one out with a
 * datapack and that half of the replacement switches off. The shipped tag files already
 * cover vanilla, Biomes O' Plenty and Oh The Biomes We've Gone.
 */
public final class TintedSoilTags {
    /** Blocks that generate as a grass-topped surface and become the tinted grass block. */
    public static final TagKey<Block> REPLACEABLE_GRASS =
            TagKey.create(Registries.BLOCK, TintedSoil.id("replaceable_grass"));

    /** Blocks that generate as bare soil and become the tinted soil block. */
    public static final TagKey<Block> REPLACEABLE_SOIL =
            TagKey.create(Registries.BLOCK, TintedSoil.id("replaceable_soil"));

    /**
     * Blocks that become the tinted <em>coarse</em> soil block, which grass will not
     * spread onto. Checked before {@link #REPLACEABLE_SOIL}, so a block listed in both
     * ends up coarse.
     */
    public static final TagKey<Block> REPLACEABLE_COARSE_SOIL =
            TagKey.create(Registries.BLOCK, TintedSoil.id("replaceable_coarse_soil"));

    private TintedSoilTags() {
    }
}
