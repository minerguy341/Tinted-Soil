package com.minerguy341.tintedsoil.mixin;

import com.minerguy341.tintedsoil.duck.DownfallSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Caches the biome's downfall at construction.
 *
 * <p>Temperature is public API ({@code Biome#getBaseTemperature}) but downfall is not, and
 * the soil colormap is indexed by both. The constructor's first argument is the
 * package-private {@code ClimateSettings} record, hence {@code @Coerce}; if the cast ever
 * fails the mod falls back to a mid-range downfall rather than crashing.
 *
 * <p>The constructor signature is byte-for-byte identical on 1.20.1, 1.21.1 and 1.21.8.
 */
@Mixin(Biome.class)
public abstract class BiomeMixin implements DownfallSource {
    @Unique
    private float tintedsoil$downfall = 0.5F;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void tintedsoil$captureDownfall(@Coerce Object climateSettings,
                                            BiomeSpecialEffects specialEffects,
                                            BiomeGenerationSettings generationSettings,
                                            MobSpawnSettings mobSettings,
                                            CallbackInfo ci) {
        if (climateSettings instanceof DownfallSource source) {
            this.tintedsoil$downfall = source.tintedsoil$downfall();
        }
    }

    @Override
    public float tintedsoil$downfall() {
        return this.tintedsoil$downfall;
    }
}
