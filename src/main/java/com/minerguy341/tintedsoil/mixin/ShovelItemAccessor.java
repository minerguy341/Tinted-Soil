package com.minerguy341.tintedsoil.mixin;

import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * {@code ShovelItem.FLATTENABLES} is a mutable {@code HashMap} in every supported version,
 * so the tinted blocks only need read access to the reference to register shovel pathing.
 */
@Mixin(ShovelItem.class)
public interface ShovelItemAccessor {
    @Accessor("FLATTENABLES")
    static Map<Block, BlockState> tintedsoil$getFlattenables() {
        throw new AssertionError("mixin not applied");
    }
}
