package com.minerguy341.tintedsoil.mixin.client;

import com.minerguy341.tintedsoil.client.SoilDefinitions;
import com.minerguy341.tintedsoil.client.SoilModels;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Retints soil by replacing the models its blocks resolve to, as they are loaded.
 *
 * <p>This is where the whole mod happens. Vanilla's {@code minecraft:block/dirt} samples a
 * brown texture with no tint index, so nothing downstream can recolour it; the replacement
 * samples a greyscale derived from that same brown texture, carries tint index 1 on its
 * soil faces, and draws the pixels that were never soil -- pebbles, a painted-on grass
 * fringe -- back over the top untinted. See {@link SoilModels} for the substitution and
 * {@code tools/generate_assets.py} for the models it points at.
 *
 * <h2>Why a mixin rather than either loader's model API</h2>
 *
 * <p>NeoForge has no hook for unbaked models -- {@code ModelEvent.ModifyBakingResult} hands
 * over models that are already baked, so using it would mean rebuilding each block's
 * variant dispatch, including dirt's four random rotations, by hand and differently on
 * 21.1 and 21.8. Fabric's {@code fabric-model-loading-api-v1} does have
 * {@code modifyModelOnLoad}, but it would only cover the Fabric half, and its own shape
 * changed across the three versions built here.
 *
 * <p>{@code ModelManager#loadBlockModels} is the same private method, with the same
 * signature, on 1.20.1, 1.21.1 and 1.21.8, on both loaders. One injection covers all five
 * targets, hands back the models before anything has looked at them, and leaves every
 * blockstate untouched -- the same reasoning that put {@link SpriteSourcesAccessor} in a
 * mixin rather than in NeoForge's event.
 *
 * <p>It also rules out the other obvious route, shipping model overrides under
 * {@code assets/minecraft/models/}: a resource pack sits <em>above</em> mod resources, so
 * the pack the mod most wants to follow -- Bare Bones, say -- would silently override the
 * retinted models with its own untinted ones and switch the mod off.
 */
@Mixin(ModelManager.class)
public abstract class SoilModelMixin {
    /**
     * The map's value type is {@code BlockModel} before 1.21.4 and {@code UnbakedModel}
     * after, which erase to the same thing; declaring it as {@code Object} is what lets one
     * injection serve both without a version branch. Mixin matches on the erased signature.
     */
    @Inject(method = "loadBlockModels", at = @At("RETURN"), cancellable = true)
    private static void tintedsoil$replaceSoilModels(
            ResourceManager resources, Executor executor,
            CallbackInfoReturnable<CompletableFuture<Map<ResourceLocation, Object>>> cir) {
        SoilDefinitions definitions = SoilDefinitions.load(resources);
        if (definitions.models().isEmpty()) {
            return;
        }
        cir.setReturnValue(cir.getReturnValue()
                .thenApply(models -> SoilModels.replace(models, definitions)));
    }
}
