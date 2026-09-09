package com.minerguy341.tintedsoil.mixin.client;

import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Opens the sprite source registry so a custom one can be added.
 *
 * <p>Vanilla keeps its registry private and offers no way in. NeoForge wraps it in
 * {@code RegisterSpriteSourceTypesEvent}, but Fabric has no equivalent at all, and the
 * event's name changed between NeoForge 21.1 and 21.8 -- so reaching the map directly is
 * both the only option on one loader and fewer moving parts on the other. One mechanism
 * covers both, leaving only the version difference to deal with.
 *
 * <p>Reflection would have avoided the mixin, but the field name is mapped: it is
 * {@code TYPES} in a Mojang-mapped dev run and an obfuscated name in a shipped jar. A mixin
 * accessor is remapped along with everything else, so it keeps working in both.
 *
 * <p>The registry itself was replaced in 1.21.6 -- a {@code BiMap} of
 * {@code SpriteSourceType} became a {@code LateBoundIdMapper} straight to the codec, and
 * {@code SpriteSourceType} stopped existing.
 */
@Mixin(SpriteSources.class)
public interface SpriteSourcesAccessor {
    //? if <1.21.6 {
    @Accessor("TYPES")
    static com.google.common.collect.BiMap<
            net.minecraft.resources.ResourceLocation,
            net.minecraft.client.renderer.texture.atlas.SpriteSourceType> tintedsoil$types() {
        throw new AssertionError("mixin");
    }
    //?}
    //? if >=1.21.6 {
    /*@Accessor("ID_MAPPER")
    static net.minecraft.util.ExtraCodecs.LateBoundIdMapper<
            net.minecraft.resources.ResourceLocation,
            com.mojang.serialization.MapCodec<
                    ? extends net.minecraft.client.renderer.texture.atlas.SpriteSource>>
            tintedsoil$idMapper() {
        throw new AssertionError("mixin");
    }
    *///?}
}
