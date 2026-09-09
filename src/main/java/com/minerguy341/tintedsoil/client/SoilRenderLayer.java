package com.minerguy341.tintedsoil.client;

import com.minerguy341.tintedsoil.TintedSoilBlocks;

/**
 * Puts the tinted blocks in the cutout layer on Fabric.
 *
 * <p>All three draw a transparent layer over a tinted cube: the grass block its side
 * overlay, and every soil block its pebbles. In the default solid layer that alpha is
 * ignored and the transparent region draws as opaque black over the soil underneath, so a
 * cutout layer is what lets those overlays sit on top of the cube at all.
 *
 * <p>NeoForge takes this from the {@code render_type} field of each model, so it needs no
 * code. Fabric API has no model-JSON equivalent and can only set a layer per block, which
 * is why the soil cube ends up in cutout too rather than staying solid. That costs nothing
 * visually -- its texels are fully opaque, so the alpha test always passes.
 *
 * <p>Like {@link LegacyItemColors}, this is a separate file because Stonecutter comment
 * blocks cannot nest and the entrypoint is already inside a loader condition.
 */
public final class SoilRenderLayer {
    private SoilRenderLayer() {
    }

    /** Every block whose model has a transparent layer over the tinted cube. */
    private static final net.minecraft.world.level.block.Block[] CUTOUT = {
            TintedSoilBlocks.TINTED_GRASS_BLOCK,
            TintedSoilBlocks.TINTED_DIRT,
            TintedSoilBlocks.TINTED_COARSE_DIRT,
    };

    public static void registerFabric() {
        // Fabric API moved the class from `blockrenderlayer.v1` into `client.rendering.v1`
        // and made it static in 1.21.6, when Minecraft split chunk layers out of RenderType
        // into ChunkSectionLayer.
        //? if fabric && <1.21.6 {
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlocks(
                net.minecraft.client.renderer.RenderType.cutoutMipped(), CUTOUT);
        //?}
        //? if fabric && >=1.21.6 {
        /*net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap.putBlocks(
                net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT_MIPPED, CUTOUT);
        *///?}
    }
}
