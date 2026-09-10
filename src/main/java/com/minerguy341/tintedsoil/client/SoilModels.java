package com.minerguy341.tintedsoil.client;

import com.minerguy341.tintedsoil.TintedSoil;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Swaps each soil block's model for the retinted one, in the map of freshly loaded models.
 *
 * <p>The replacement is a model whose only content is a parent, pointing at the generated
 * model under {@code assets/tintedsoil/models/block/soil/}. Everything else -- geometry,
 * textures, tint indices -- comes from that file, and the normal pipeline resolves it,
 * so nothing is assembled in code beyond the one indirection.
 *
 * <p>Substituting the <em>unbaked</em> model rather than the baked one is what keeps
 * everything vanilla decided about the block: the blockstate file still applies, so dirt
 * still picks one of four random rotations and a grass block still switches models when
 * it is snowed on. Nothing here knows about blockstates at all.
 *
 * <p>Two things about that map changed in 1.21.4, and nothing else did. Its values went
 * from {@code BlockModel} to {@code UnbakedModel}, which erase to the same thing, so they
 * are passed through as {@code Object} and only {@link #inherit} knows the difference. Its
 * keys went from the file a model was read from ({@code minecraft:models/block/dirt.json})
 * to the model's own id ({@code minecraft:block/dirt}), which {@link #key} converts.
 */
public final class SoilModels {
    private SoilModels() {
    }

    /**
     * @param models      every model the client just loaded, keyed by id
     * @param definitions the soil list, giving each soil model its replacement
     * @return {@code models} with soil models replaced, or {@code models} itself if none
     *         of them are present
     */
    public static Map<ResourceLocation, Object> replace(Map<ResourceLocation, Object> models,
                                                        SoilDefinitions definitions) {
        Map<ResourceLocation, Object> replaced = null;
        int count = 0;

        for (Map.Entry<ResourceLocation, ResourceLocation> entry : definitions.models().entrySet()) {
            ResourceLocation original = key(entry.getKey());
            // Both halves have to be there. A soil from a mod that is not installed has no
            // model to replace, and a replacement that failed to load would leave a live
            // block pointing at nothing.
            if (!models.containsKey(original) || !models.containsKey(key(entry.getValue()))) {
                continue;
            }
            if (replaced == null) {
                replaced = new HashMap<>(models);
            }
            replaced.put(original, inherit(entry.getValue()));
            count++;
        }

        if (replaced == null) {
            TintedSoil.LOGGER.warn("Found none of the {} soil models; soil is left as it was",
                    definitions.models().size());
            return models;
        }
        TintedSoil.LOGGER.info("Retinted {} of {} soil models", count, definitions.models().size());
        return replaced;
    }

    /**
     * The key a model is filed under in the loaded map.
     *
     * <p>Before 1.21.4 that is the file it came from, so the id has to be put back through
     * the same converter {@code ModelBakery} uses to find it again.
     */
    private static ResourceLocation key(ResourceLocation modelId) {
        //? if <1.21.4 {
        return net.minecraft.client.resources.model.ModelBakery.MODEL_LISTER.idToFile(modelId);
        //?} else {
        /*return modelId;
        *///?}
    }

    /**
     * A model that is nothing but a parent, which the resolver then fills in.
     *
     * <p>Its name is left alone: before 1.21.4 the bakery stamps the id it was asked for
     * onto whatever model it finds, so log messages already point at the right block.
     */
    private static Object inherit(ResourceLocation parent) {
        //? if <1.21.4 {
        return net.minecraft.client.renderer.block.model.BlockModel.fromString(
                "{\"parent\": \"" + parent + "\"}");
        //?} else {
        /*// Every other member of UnbakedModel has a default, so a parent is the whole of it.
        return new net.minecraft.client.resources.model.UnbakedModel() {
            @Override
            public ResourceLocation parent() {
                return parent;
            }
        };
        *///?}
    }
}
