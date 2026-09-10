package com.minerguy341.tintedsoil.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minerguy341.tintedsoil.TintedSoil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What Tinted Soil considers soil, read from {@code assets/tintedsoil/soils.json}.
 *
 * <p>This has to be a client resource rather than the block tags the mod used to be
 * configured by. Models are loaded and the atlas is stitched during a resource reload,
 * which happens long before the client has been told about a single block tag -- tags
 * arrive from the server on join. A resource is available at exactly the moment both
 * halves of the mod need it.
 *
 * <p>The file is read with {@code getResourceStack}, so every loaded pack contributes:
 * a resource pack that ships its own {@code soils.json} <em>adds</em> soils rather than
 * replacing the shipped list, and later packs win on any key they repeat.
 *
 * <p>Nothing is cached here. Each of the four readers -- sprite derivation, the model
 * swap, item models, and the colour lookup -- parses once per reload, which is a few
 * kilobytes of JSON per resource load and saves having to invalidate a cache from four
 * places that run on different threads in an unspecified order.
 */
public final class SoilDefinitions {
    private static final ResourceLocation FILE = TintedSoil.id("soils.json");

    /** The greyscale/pebble sprite pair derived from one source texture. */
    public record Sprites(ResourceLocation soil, ResourceLocation pebbles) {
    }

    /**
     * A sprite recovered by subtracting one texture from another.
     *
     * <p>Podzol is the case this exists for: vanilla composites its crust into
     * {@code podzol_side.png} instead of shipping an overlay, and keeping only the
     * pixels where that differs from {@code dirt.png} recovers the crust on its own.
     *
     * @param under the texture to subtract, or {@code null} to copy {@code over} whole
     */
    public record Overlay(ResourceLocation sprite, ResourceLocation over, ResourceLocation under) {
    }

    /** Source texture to the sprites derived from it. */
    private final Map<ResourceLocation, Sprites> textures;
    private final List<Overlay> overlays;
    /** The model a soil block resolves to, to the model that replaces it. */
    private final Map<ResourceLocation, ResourceLocation> models;
    /** Soil block to the texture whose colour it votes with. */
    private final Map<ResourceLocation, ResourceLocation> blocks;

    private SoilDefinitions(Map<ResourceLocation, Sprites> textures, List<Overlay> overlays,
                            Map<ResourceLocation, ResourceLocation> models,
                            Map<ResourceLocation, ResourceLocation> blocks) {
        this.textures = textures;
        this.overlays = overlays;
        this.models = models;
        this.blocks = blocks;
    }

    public static final SoilDefinitions EMPTY = new SoilDefinitions(
            Map.of(), List.of(), Map.of(), Map.of());

    public Map<ResourceLocation, Sprites> textures() {
        return this.textures;
    }

    public List<Overlay> overlays() {
        return this.overlays;
    }

    public Map<ResourceLocation, ResourceLocation> models() {
        return this.models;
    }

    public Map<ResourceLocation, ResourceLocation> blocks() {
        return this.blocks;
    }

    /** Reads and merges every pack's copy. Never throws: a broken file leaves soil untouched. */
    public static SoilDefinitions load(ResourceManager resources) {
        Map<ResourceLocation, Sprites> textures = new LinkedHashMap<>();
        List<Overlay> overlays = new ArrayList<>();
        Map<ResourceLocation, ResourceLocation> models = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> blocks = new LinkedHashMap<>();

        List<Resource> stack;
        try {
            stack = resources.getResourceStack(FILE);
        } catch (Exception exception) {
            TintedSoil.LOGGER.warn("Could not list {}; no soil will be retinted", FILE, exception);
            return EMPTY;
        }

        for (Resource resource : stack) {
            try (Reader reader = resource.openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                readTextures(root, textures);
                readOverlays(root, overlays);
                readMap(root, "models", models);
                readMap(root, "blocks", blocks);
            } catch (Exception exception) {
                TintedSoil.LOGGER.warn("Could not read {} from pack '{}'", FILE,
                        resource.sourcePackId(), exception);
            }
        }

        return new SoilDefinitions(textures, overlays, models, blocks);
    }

    private static void readTextures(JsonObject root, Map<ResourceLocation, Sprites> into) {
        JsonObject section = root.getAsJsonObject("textures");
        if (section == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : section.entrySet()) {
            ResourceLocation texture = id(entry.getKey());
            JsonObject sprites = entry.getValue().getAsJsonObject();
            ResourceLocation soil = id(sprites.get("soil").getAsString());
            ResourceLocation pebbles = id(sprites.get("pebbles").getAsString());
            if (texture != null && soil != null && pebbles != null) {
                into.put(texture, new Sprites(soil, pebbles));
            }
        }
    }

    private static void readOverlays(JsonObject root, List<Overlay> into) {
        if (root.get("overlays") == null) {
            return;
        }
        for (JsonElement element : root.getAsJsonArray("overlays")) {
            JsonObject entry = element.getAsJsonObject();
            ResourceLocation sprite = id(entry.get("sprite").getAsString());
            ResourceLocation over = id(entry.get("over").getAsString());
            ResourceLocation under = entry.has("under") ? id(entry.get("under").getAsString()) : null;
            if (sprite != null && over != null) {
                into.add(new Overlay(sprite, over, under));
            }
        }
    }

    private static void readMap(JsonObject root, String name,
                                Map<ResourceLocation, ResourceLocation> into) {
        JsonObject section = root.getAsJsonObject(name);
        if (section == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : section.entrySet()) {
            ResourceLocation key = id(entry.getKey());
            ResourceLocation value = id(entry.getValue().getAsString());
            if (key != null && value != null) {
                into.put(key, value);
            }
        }
    }

    /** Malformed ids are dropped rather than fatal, so one bad line cannot break a reload. */
    private static ResourceLocation id(String value) {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null) {
            TintedSoil.LOGGER.warn("Ignoring malformed id '{}' in {}", value, FILE);
        }
        return parsed;
    }
}
