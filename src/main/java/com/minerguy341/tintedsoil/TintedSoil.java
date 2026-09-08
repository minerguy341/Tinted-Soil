package com.minerguy341.tintedsoil;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants and helpers. Everything here is loader-agnostic; the Fabric and
 * NeoForge entrypoints only handle registration timing and event wiring.
 */
public final class TintedSoil {
    public static final String MOD_ID = /*$ mod_id*/ "tintedsoil";
    public static final String MINECRAFT = /*$ minecraft*/ "1.21.1";
    public static final Logger LOGGER = LoggerFactory.getLogger("Tinted Soil");

    private TintedSoil() {
    }

    public static ResourceLocation id(String path) {
        //? if <1.21 {
        /*return new ResourceLocation(MOD_ID, path);
        *///?} else
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Runs the loader-independent setup. Called from both entrypoints after registration. */
    public static void onCommonSetup() {
        TintedSoilInteractions.register();
    }
}
