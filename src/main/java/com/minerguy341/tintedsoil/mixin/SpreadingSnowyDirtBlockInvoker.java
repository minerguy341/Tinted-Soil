package com.minerguy341.tintedsoil.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SpreadingSnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reuses vanilla's own grass-survival checks so the tinted grass block spreads and dies
 * under exactly the same light and fluid rules as {@code minecraft:grass_block}.
 *
 * <p>Both methods are {@code private static} in vanilla with signatures unchanged across
 * 1.20.1 through 1.21.8.
 */
@Mixin(SpreadingSnowyDirtBlock.class)
public interface SpreadingSnowyDirtBlockInvoker {
    @Invoker("canBeGrass")
    static boolean tintedsoil$canBeGrass(BlockState state, LevelReader level, BlockPos pos) {
        throw new AssertionError("mixin not applied");
    }

    @Invoker("canPropagate")
    static boolean tintedsoil$canPropagate(BlockState state, LevelReader level, BlockPos pos) {
        throw new AssertionError("mixin not applied");
    }
}
