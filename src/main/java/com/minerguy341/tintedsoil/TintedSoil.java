package com.minerguy341.tintedsoil;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants and the handful of helpers whose spelling changes between versions.
 *
 * <p>Everything here is loader-agnostic. The mod registers nothing and touches no
 * server-side state -- it retints blocks that already exist -- so the two entrypoints
 * exist only to satisfy each loader and to start the client half.
 */
public final class TintedSoil {
    public static final String MOD_ID = /*$ mod_id*/ "tintedsoil";
    public static final String MINECRAFT = /*$ minecraft*/ "1.21.1";
    public static final Logger LOGGER = LoggerFactory.getLogger("Tinted Soil");

    private TintedSoil() {
    }

    public static ResourceLocation id(String path) {
        return location(MOD_ID, path);
    }

    /** {@code ResourceLocation}'s public constructor became a factory method in 1.21. */
    public static ResourceLocation location(String namespace, String path) {
        //? if <1.21 {
        /*return new ResourceLocation(namespace, path);
        *///?} else
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    /**
     * @return the registered block, or {@code null} if no mod supplies it. Soil lists name
     *         blocks from mods that may not be installed, so absence is expected.
     */
    public static Block block(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            return null;
        }
        // `Registry#get(ResourceLocation)` was renamed to `getValue` in 1.21.2, where the
        // old name became the Holder-returning lookup.
        //? if <1.21.2 {
        return BuiltInRegistries.BLOCK.get(id);
        //?} else {
        /*return BuiltInRegistries.BLOCK.getValue(id);
        *///?}
    }
}
