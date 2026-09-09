package com.minerguy341.tintedsoil.block;

import com.minerguy341.tintedsoil.TintedSoil;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Which soil a tinted block is standing in for.
 *
 * <p>This is colour only — behaviour differences live in the blocks themselves (coarse soil
 * is its own block because grass must not spread onto it). Keeping the type in the
 * blockstate is what lets a block remember that it used to be peat rather than plain dirt,
 * long after worldgen has finished.
 *
 * <p>Membership is decided by the {@code tintedsoil:soil_type/<name>} tags, so a datapack
 * can route a modded soil to whichever type looks closest without touching code.
 */
public enum SoilType implements StringRepresentable {
    /** Plain dirt: contributes no colour of its own, leaving the biome tint alone. */
    DEFAULT("default"),
    PODZOL("podzol"),
    LUSH("lush"),
    SANDY("sandy"),
    PEAT("peat"),
    ORIGIN("origin");

    public static final EnumProperty<SoilType> PROPERTY = EnumProperty.create("soil_type", SoilType.class);

    private final String name;
    private final TagKey<Block> tag;

    SoilType(String name) {
        this.name = name;
        this.tag = TagKey.create(Registries.BLOCK, TintedSoil.id("soil_type/" + name));
    }

    /** Source blocks that map to this type. Unused for {@link #DEFAULT}, which is the fallback. */
    public TagKey<Block> tag() {
        return this.tag;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
