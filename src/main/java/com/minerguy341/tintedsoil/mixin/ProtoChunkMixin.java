package com.minerguy341.tintedsoil.mixin;

import com.minerguy341.tintedsoil.world.SoilReplacer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The single point where soil replacement happens.
 *
 * <p>Every worldgen path -- surface rules, features, carvers, structures, and anything a
 * worldgen mod adds -- ultimately writes through {@code ProtoChunk#setBlockState}, so
 * swapping the incoming state here covers Biomes O' Plenty and Oh The Biomes We've Gone
 * without needing to know anything about them.
 *
 * <p>{@code @ModifyVariable} with {@code argsOnly} matches the sole {@code BlockState}
 * parameter by type, which sidesteps the third parameter changing from {@code boolean} to
 * {@code int} in 1.21.x.
 */
@Mixin(ProtoChunk.class)
public abstract class ProtoChunkMixin {
    @ModifyVariable(method = "setBlockState", at = @At("HEAD"), argsOnly = true)
    private BlockState tintedsoil$replaceSoil(BlockState state) {
        return SoilReplacer.replace(state);
    }
}
