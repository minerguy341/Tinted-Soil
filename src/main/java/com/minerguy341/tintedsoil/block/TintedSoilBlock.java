package com.minerguy341.tintedsoil.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21 {
import com.mojang.serialization.MapCodec;
//?}

/**
 * Bare soil that remembers which soil type it replaced.
 *
 * <p>Used for both plain and coarse tinted soil; the two differ in behaviour (grass spread,
 * hoe result) via which instance they are, not via this class.
 */
public class TintedSoilBlock extends Block {
    //? if >=1.21 {
    public static final MapCodec<TintedSoilBlock> CODEC = BlockBehaviour.simpleCodec(TintedSoilBlock::new);
    //?}

    public TintedSoilBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(SoilType.PROPERTY, SoilType.DEFAULT));
    }

    //? if >=1.21 {
    @Override
    public MapCodec<TintedSoilBlock> codec() {
        return CODEC;
    }
    //?}

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SoilType.PROPERTY);
    }
}
