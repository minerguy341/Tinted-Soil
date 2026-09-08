package com.minerguy341.tintedsoil.mixin.client;

import com.minerguy341.tintedsoil.client.SoilColormap;
import com.minerguy341.tintedsoil.client.TintedSoilColors;
import net.minecraft.client.color.block.BlockTintCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ColorResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives the soil resolver a blend cache alongside vanilla's grass, foliage and water ones.
 *
 * <p>Vanilla builds {@code tintCaches} in the constructor with a fixed set of resolvers, so
 * an unknown resolver would fault on lookup. Intercepting {@code getBlockTint} instead of
 * the constructor keeps this version-agnostic -- the constructor's parameter list changed in
 * 1.21.8, {@code getBlockTint(BlockPos, ColorResolver)} did not -- and it covers every
 * caller, including the {@code RenderChunkRegion} used during chunk meshing.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Unique
    private volatile BlockTintCache tintedsoil$soilCache;

    @Inject(method = "getBlockTint", at = @At("HEAD"), cancellable = true)
    private void tintedsoil$soilTint(BlockPos pos, ColorResolver resolver, CallbackInfoReturnable<Integer> cir) {
        if (resolver == TintedSoilColors.SOIL_COLOR_RESOLVER) {
            cir.setReturnValue(tintedsoil$soilCache().getColor(pos));
        }
    }

    @Inject(method = "clearTintCaches", at = @At("TAIL"))
    private void tintedsoil$clearSoilCache(CallbackInfo ci) {
        BlockTintCache cache = this.tintedsoil$soilCache;
        if (cache != null) {
            cache.invalidateAll();
        }
        // Reached on resource reload too, which is when a resource pack's colormap changes.
        SoilColormap.invalidate();
    }

    @Inject(method = "onChunkLoaded", at = @At("TAIL"))
    private void tintedsoil$invalidateChunk(ChunkPos chunkPos, CallbackInfo ci) {
        BlockTintCache cache = this.tintedsoil$soilCache;
        if (cache != null) {
            cache.invalidateForChunk(chunkPos.x, chunkPos.z);
        }
    }

    @Unique
    private BlockTintCache tintedsoil$soilCache() {
        BlockTintCache cache = this.tintedsoil$soilCache;
        if (cache == null) {
            ClientLevel self = (ClientLevel) (Object) this;
            // Racing threads may each build one; they are equivalent, so the loser is simply dropped.
            cache = new BlockTintCache(pos -> self.calculateBlockTint(pos, TintedSoilColors.SOIL_COLOR_RESOLVER));
            this.tintedsoil$soilCache = cache;
        }
        return cache;
    }
}
