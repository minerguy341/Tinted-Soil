package com.minerguy341.tintedsoil.mixin.client;

import com.minerguy341.tintedsoil.client.SoilTints;
import com.minerguy341.tintedsoil.client.TintedSoilColors;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Answers tint index 1 -- soil -- for the blocks this mod retints, and nothing else.
 *
 * <p>Registering a {@code BlockColor} would have meant replacing whatever provider the
 * block already had, and for a modded grass block that provider is the mod's own grass
 * colour. Which of us won would come down to mod initialisation order. Intercepting one
 * tint index instead leaves index 0 to its owner: vanilla still colours grass tops,
 * BWG still colours its lush grass, and the mod only ever answers for the index its own
 * generated models introduced.
 *
 * <p>A block that is not soil falls straight through, so this costs one map lookup on the
 * blocks the mod does not touch.
 */
@Mixin(BlockColors.class)
public abstract class SoilBlockColorMixin {
    @Inject(method = "getColor(Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;I)I",
            at = @At("HEAD"), cancellable = true)
    private void tintedsoil$soilTint(BlockState state, BlockAndTintGetter view, BlockPos pos,
                                     int tintIndex, CallbackInfoReturnable<Integer> cir) {
        if (tintIndex == TintedSoilColors.SOIL_TINT_INDEX && SoilTints.isSoil(state.getBlock())) {
            cir.setReturnValue(TintedSoilColors.soilTint(state, view, pos));
        }
    }
}
