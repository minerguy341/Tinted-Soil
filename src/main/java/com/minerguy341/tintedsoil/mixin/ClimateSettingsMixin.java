package com.minerguy341.tintedsoil.mixin;

import com.minerguy341.tintedsoil.duck.DownfallSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * {@code Biome.ClimateSettings} is package-private, so it is targeted by name.
 * Implementing {@link DownfallSource} onto it is what lets {@link BiomeMixin} read the
 * value out of an otherwise untypeable constructor argument.
 */
@Mixin(targets = "net.minecraft.world.level.biome.Biome$ClimateSettings")
public abstract class ClimateSettingsMixin implements DownfallSource {
    @Shadow @Final float downfall;

    @Override
    public float tintedsoil$downfall() {
        return this.downfall;
    }
}
