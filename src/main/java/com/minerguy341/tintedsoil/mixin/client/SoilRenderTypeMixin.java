package com.minerguy341.tintedsoil.mixin.client;

import com.minerguy341.tintedsoil.client.SoilTints;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts every retinted soil block in the cutout layer.
 *
 * <p>Each retinted model draws a transparent layer over a tinted cube -- the pebbles, and
 * the grass fringe where there is one. In the solid layer that alpha is ignored and the
 * transparent region draws as opaque black over the soil underneath, so a cutout layer is
 * what lets those overlays sit on top of the cube at all. Mipmapped rather than plain
 * cutout, so a fringe does not alias into noise at distance -- the layer vanilla already
 * uses for its own grass block.
 *
 * <p>Which blocks those are is not known until {@code soils.json} has been read and the
 * atlas stitched, which is well after both loaders' render-layer registries are meant to
 * be filled in. Deciding it here instead sidesteps that ordering entirely, and covers
 * Fabric -- which has no model-JSON render type at all -- with the same code that covers
 * NeoForge.
 */
@Mixin(ItemBlockRenderTypes.class)
public abstract class SoilRenderTypeMixin {
    //? if <1.21.6 {
    @Inject(method = "getChunkRenderType", at = @At("HEAD"), cancellable = true)
    private static void tintedsoil$soilIsCutout(
            BlockState state, CallbackInfoReturnable<net.minecraft.client.renderer.RenderType> cir) {
        if (SoilTints.isSoil(state.getBlock())) {
            cir.setReturnValue(net.minecraft.client.renderer.RenderType.cutoutMipped());
        }
    }
    //?}
    //? if >=1.21.6 {
    /*@Inject(method = "getChunkRenderType", at = @At("HEAD"), cancellable = true)
    private static void tintedsoil$soilIsCutout(
            BlockState state,
            CallbackInfoReturnable<net.minecraft.client.renderer.chunk.ChunkSectionLayer> cir) {
        if (SoilTints.isSoil(state.getBlock())) {
            cir.setReturnValue(net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT_MIPPED);
        }
    }
    *///?}
}
