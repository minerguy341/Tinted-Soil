package com.minerguy341.tintedsoil.block;

import com.minerguy341.tintedsoil.TintedSoilBlocks;
import com.minerguy341.tintedsoil.mixin.SpreadingSnowyDirtBlockInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
//? if >=1.21 {
import com.mojang.serialization.MapCodec;
//?}

/**
 * The tinted grass block.
 *
 * <p>It extends {@link GrassBlock} so bone meal, pathing, mob spawning and every other
 * {@code instanceof}/inheritance-based behaviour keeps working. The one thing it cannot
 * inherit is spreading: {@code SpreadingSnowyDirtBlock#randomTick} hardcodes
 * {@code Blocks.DIRT} and {@code Blocks.GRASS_BLOCK}, so once the world is made of tinted
 * blocks vanilla's spread would find nothing to grow onto and grass would stop spreading
 * entirely. {@link #randomTick} below is a faithful port that walks tinted soil instead,
 * reusing vanilla's own (private) light and fluid checks through an invoker mixin so the
 * survival rules stay identical.
 */
public class TintedGrassBlock extends GrassBlock {
    // GrassBlock#codec() is invariant in GrassBlock, so the codec is typed to the
    // supertype rather than to this class.
    //? if >=1.21 {
    public static final MapCodec<GrassBlock> CODEC = BlockBehaviour.simpleCodec(TintedGrassBlock::new);
    //?}

    public TintedGrassBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(SoilType.PROPERTY, SoilType.DEFAULT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SoilType.PROPERTY);
    }

    //? if >=1.21 {
    @Override
    public MapCodec<GrassBlock> codec() {
        return CODEC;
    }
    //?}

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!SpreadingSnowyDirtBlockInvoker.tintedsoil$canBeGrass(state, level, pos)) {
            // Too dark or drowned: die back to bare soil, mirroring vanilla. The soil type
            // rides along so a patch does not forget it was peat just because it lost grass.
            level.setBlockAndUpdate(pos, TintedSoilBlocks.TINTED_SOIL.defaultBlockState()
                    .setValue(SoilType.PROPERTY, state.getValue(SoilType.PROPERTY)));
            return;
        }

        if (level.getMaxLocalRawBrightness(pos.above()) < 9) {
            return;
        }

        BlockState spreading = this.defaultBlockState();
        for (int attempt = 0; attempt < 4; attempt++) {
            BlockPos target = pos.offset(random.nextInt(3) - 1, random.nextInt(5) - 3, random.nextInt(3) - 1);
            BlockState targetState = level.getBlockState(target);
            if (!targetState.is(TintedSoilBlocks.TINTED_SOIL)) {
                continue;
            }
            if (!SpreadingSnowyDirtBlockInvoker.tintedsoil$canPropagate(spreading, level, target)) {
                continue;
            }
            boolean snowy = level.getBlockState(target.above()).is(Blocks.SNOW);
            // Grass takes on the soil it grows onto, so spreading never rewrites soil types.
            level.setBlockAndUpdate(target, spreading
                    .setValue(SnowyDirtBlock.SNOWY, snowy)
                    .setValue(SoilType.PROPERTY, targetState.getValue(SoilType.PROPERTY)));
        }
    }
}
