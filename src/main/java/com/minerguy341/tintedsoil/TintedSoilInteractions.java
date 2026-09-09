package com.minerguy341.tintedsoil;

import com.minerguy341.tintedsoil.mixin.HoeItemAccessor;
import com.minerguy341.tintedsoil.mixin.ShovelItemAccessor;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.level.block.Blocks;

/**
 * Registers the tool interactions vanilla hardcodes per block: shovel makes a path, hoe
 * makes farmland. Tag membership covers everything else other mods key off, but these two
 * live in static maps on the item classes rather than in data.
 */
public final class TintedSoilInteractions {
    private TintedSoilInteractions() {
    }

    public static void register() {
        ShovelItemAccessor.tintedsoil$getFlattenables()
                .put(TintedSoilBlocks.TINTED_GRASS_BLOCK, Blocks.DIRT_PATH.defaultBlockState());
        ShovelItemAccessor.tintedsoil$getFlattenables()
                .put(TintedSoilBlocks.TINTED_SOIL, Blocks.DIRT_PATH.defaultBlockState());
        ShovelItemAccessor.tintedsoil$getFlattenables()
                .put(TintedSoilBlocks.TINTED_COARSE_SOIL, Blocks.DIRT_PATH.defaultBlockState());

        Pair<java.util.function.Predicate<net.minecraft.world.item.context.UseOnContext>,
                java.util.function.Consumer<net.minecraft.world.item.context.UseOnContext>> toFarmland =
                Pair.of(HoeItem::onlyIfAirAbove, HoeItem.changeIntoState(Blocks.FARMLAND.defaultBlockState()));

        HoeItemAccessor.tintedsoil$getTillables().put(TintedSoilBlocks.TINTED_GRASS_BLOCK, toFarmland);
        HoeItemAccessor.tintedsoil$getTillables().put(TintedSoilBlocks.TINTED_SOIL, toFarmland);

        // Vanilla hoes turn coarse dirt into plain dirt rather than farmland; mirror that.
        HoeItemAccessor.tintedsoil$getTillables().put(TintedSoilBlocks.TINTED_COARSE_SOIL, Pair.of(
                HoeItem::onlyIfAirAbove,
                HoeItem.changeIntoState(TintedSoilBlocks.TINTED_SOIL.defaultBlockState())));
    }
}
