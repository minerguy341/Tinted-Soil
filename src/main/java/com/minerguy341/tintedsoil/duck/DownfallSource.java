package com.minerguy341.tintedsoil.duck;

/**
 * Exposes a biome's downfall, which vanilla keeps on the package-private
 * {@code Biome.ClimateSettings} record with no public getter.
 *
 * <p>Implemented onto both {@code Biome} and {@code Biome.ClimateSettings} by mixins.
 */
public interface DownfallSource {
    float tintedsoil$downfall();
}
