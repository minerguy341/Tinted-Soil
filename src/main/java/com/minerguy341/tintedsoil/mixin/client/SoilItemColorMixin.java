package com.minerguy341.tintedsoil.mixin.client;

import com.minerguy341.tintedsoil.client.TintedSoilColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps soil items looking like soil in the inventory.
 *
 * <p>A block item renders the block's model, and that model is now retinted -- a
 * greyscale carrying tint index 1. Nothing in an inventory has a position to blend
 * around, so the item takes the block's own colour, which is the colour it renders as on
 * flat ground of its own kind.
 *
 * <p>The two branches exist because 1.21.4 moved item tints out of code and into item
 * model definitions; {@code SoilItemTints} is that half.
 */
//? if <1.21.4 {
@Mixin(net.minecraft.client.color.item.ItemColors.class)
public abstract class SoilItemColorMixin {
    @Inject(method = "getColor", at = @At("HEAD"), cancellable = true)
    private void tintedsoil$soilItemTint(net.minecraft.world.item.ItemStack stack, int tintIndex,
                                         CallbackInfoReturnable<Integer> cir) {
        if (tintIndex != TintedSoilColors.SOIL_TINT_INDEX) {
            return;
        }
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem item) {
            int tint = TintedSoilColors.itemTint(item.getBlock());
            if (tint != -1) {
                cir.setReturnValue(tint);
            }
        }
    }
}
//?}
//? if >=1.21.4 {
/*@Mixin(net.minecraft.client.resources.model.ClientItemInfoLoader.class)
public abstract class SoilItemColorMixin {
    @Inject(method = "scheduleLoad", at = @At("RETURN"), cancellable = true)
    private static void tintedsoil$tintSoilItems(
            net.minecraft.server.packs.resources.ResourceManager resources,
            java.util.concurrent.Executor executor,
            CallbackInfoReturnable<java.util.concurrent.CompletableFuture<
                    net.minecraft.client.resources.model.ClientItemInfoLoader.LoadedClientInfos>> cir) {
        com.minerguy341.tintedsoil.client.SoilDefinitions definitions =
                com.minerguy341.tintedsoil.client.SoilDefinitions.load(resources);
        if (definitions.blocks().isEmpty()) {
            return;
        }
        cir.setReturnValue(cir.getReturnValue()
                .thenApply(infos -> com.minerguy341.tintedsoil.client.SoilItemTints
                        .retint(infos, definitions)));
    }
}
*///?}
