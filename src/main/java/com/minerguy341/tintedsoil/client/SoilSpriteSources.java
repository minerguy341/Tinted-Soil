package com.minerguy341.tintedsoil.client;

import com.minerguy341.tintedsoil.TintedSoil;
import com.minerguy341.tintedsoil.mixin.client.SpriteSourcesAccessor;
import net.minecraft.resources.ResourceLocation;

/**
 * Registers {@link SoilSpriteSource} so {@code assets/tintedsoil/atlases/blocks.json} can
 * name it.
 *
 * <p>Has to run before the first resource reload, since that is when atlas definitions are
 * parsed and an unknown source type is a hard error. Both client entrypoints call it during
 * client init, which is comfortably early.
 */
public final class SoilSpriteSources {
    public static final ResourceLocation ID = TintedSoil.id("derived_soil");

    //? if <1.21.6 {
    /** The registry stores a wrapper record; from 1.21.6 it stores the codec directly. */
    public static final net.minecraft.client.renderer.texture.atlas.SpriteSourceType TYPE =
            new net.minecraft.client.renderer.texture.atlas.SpriteSourceType(
                    //? if <1.21 {
                    /*com.mojang.serialization.Codec.unit(SoilSpriteSource::new)
                    *///?} else
                    com.mojang.serialization.MapCodec.unit(SoilSpriteSource::new)
            );
    //?}
    //? if >=1.21.6 {
    /*public static final com.mojang.serialization.MapCodec<SoilSpriteSource> CODEC =
            com.mojang.serialization.MapCodec.unit(SoilSpriteSource::new);
    *///?}

    private SoilSpriteSources() {
    }

    public static void register() {
        //? if <1.21.6 {
        SpriteSourcesAccessor.tintedsoil$types().put(ID, TYPE);
        //?}
        //? if >=1.21.6 {
        /*SpriteSourcesAccessor.tintedsoil$idMapper().put(ID, CODEC);
        *///?}
    }
}
