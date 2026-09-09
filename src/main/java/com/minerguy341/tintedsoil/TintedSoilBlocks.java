package com.minerguy341.tintedsoil;

import com.minerguy341.tintedsoil.block.TintedGrassBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.BiConsumer;

/**
 * The replacement blocks.
 *
 * <p>Each copies its behaviour wholesale from the vanilla block it stands in for, so
 * hardness, sound, tool requirements and every {@code BlockBehaviour} flag other mods
 * rely on carry over unchanged.
 *
 * <p>Coarse soil is a distinct block rather than a blockstate of {@link #TINTED_SOIL}
 * because vanilla models it that way too, and because the difference has to be visible to
 * grass spreading, hoes, loot and tags. Folding it into one block would have made every
 * one of those a per-state special case. It costs nothing conceptually: the mod is still
 * two tint indices, and coarse soil uses the same soil tint as everything else.
 */
public final class TintedSoilBlocks {
    public static final ResourceLocation TINTED_GRASS_BLOCK_ID = TintedSoil.id("tinted_grass_block");
    public static final ResourceLocation TINTED_SOIL_ID = TintedSoil.id("tinted_soil");
    public static final ResourceLocation TINTED_COARSE_SOIL_ID = TintedSoil.id("tinted_coarse_soil");

    public static final ResourceKey<Block> TINTED_GRASS_BLOCK_KEY =
            ResourceKey.create(Registries.BLOCK, TINTED_GRASS_BLOCK_ID);
    public static final ResourceKey<Block> TINTED_SOIL_KEY =
            ResourceKey.create(Registries.BLOCK, TINTED_SOIL_ID);
    public static final ResourceKey<Block> TINTED_COARSE_SOIL_KEY =
            ResourceKey.create(Registries.BLOCK, TINTED_COARSE_SOIL_ID);

    public static final ResourceKey<Item> TINTED_GRASS_BLOCK_ITEM_KEY =
            ResourceKey.create(Registries.ITEM, TINTED_GRASS_BLOCK_ID);
    public static final ResourceKey<Item> TINTED_SOIL_ITEM_KEY =
            ResourceKey.create(Registries.ITEM, TINTED_SOIL_ID);
    public static final ResourceKey<Item> TINTED_COARSE_SOIL_ITEM_KEY =
            ResourceKey.create(Registries.ITEM, TINTED_COARSE_SOIL_ID);

    /** Grass-topped surface block: tint index 0 is grass, tint index 1 is the soil underneath. */
    public static final TintedGrassBlock TINTED_GRASS_BLOCK =
            new TintedGrassBlock(copyOf(Blocks.GRASS_BLOCK, TINTED_GRASS_BLOCK_KEY));

    /** Bare soil block: a single soil tint, no grass. */
    public static final Block TINTED_SOIL =
            new Block(copyOf(Blocks.DIRT, TINTED_SOIL_KEY));

    /**
     * Bare soil that grass will not spread onto, standing in for coarse dirt.
     *
     * <p>Grass spreading in {@link com.minerguy341.tintedsoil.block.TintedGrassBlock} only
     * walks {@link #TINTED_SOIL}, so being a separate block is what keeps this one bare.
     */
    public static final Block TINTED_COARSE_SOIL =
            new Block(copyOf(Blocks.COARSE_DIRT, TINTED_COARSE_SOIL_KEY));

    public static final Item TINTED_GRASS_BLOCK_ITEM =
            new BlockItem(TINTED_GRASS_BLOCK, itemProperties(TINTED_GRASS_BLOCK_ITEM_KEY));
    public static final Item TINTED_SOIL_ITEM =
            new BlockItem(TINTED_SOIL, itemProperties(TINTED_SOIL_ITEM_KEY));
    public static final Item TINTED_COARSE_SOIL_ITEM =
            new BlockItem(TINTED_COARSE_SOIL, itemProperties(TINTED_COARSE_SOIL_ITEM_KEY));

    private TintedSoilBlocks() {
    }

    private static BlockBehaviour.Properties copyOf(Block source, ResourceKey<Block> key) {
        BlockBehaviour.Properties properties =
                //? if <1.21 {
                /*BlockBehaviour.Properties.copy(source);
                *///?} else
                BlockBehaviour.Properties.ofFullCopy(source);

        // From 1.21.2 a block's id lives in its properties and must be set before construction.
        // `ofFullCopy` also copies the source block's id, so this has to override it.
        //? if >=1.21.2 {
        /*properties = properties.setId(key);
        *///?}
        return properties;
    }

    private static Item.Properties itemProperties(ResourceKey<Item> key) {
        Item.Properties properties = new Item.Properties();
        //? if >=1.21.2 {
        /*properties = properties.useBlockDescriptionPrefix().setId(key);
        *///?}
        return properties;
    }

    /**
     * Hands every block and item to the loader's registration mechanism. Both loaders
     * construct the objects eagerly (above) and only differ in when they may be registered.
     */
    public static void register(BiConsumer<ResourceLocation, Block> blocks,
                                BiConsumer<ResourceLocation, Item> items) {
        blocks.accept(TINTED_GRASS_BLOCK_ID, TINTED_GRASS_BLOCK);
        blocks.accept(TINTED_SOIL_ID, TINTED_SOIL);
        blocks.accept(TINTED_COARSE_SOIL_ID, TINTED_COARSE_SOIL);
        items.accept(TINTED_GRASS_BLOCK_ID, TINTED_GRASS_BLOCK_ITEM);
        items.accept(TINTED_SOIL_ID, TINTED_SOIL_ITEM);
        items.accept(TINTED_COARSE_SOIL_ID, TINTED_COARSE_SOIL_ITEM);
    }
}
