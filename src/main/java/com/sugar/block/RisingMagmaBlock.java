package com.sugar.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Magma del templo: no fluye, no destruye objetos y se atraviesa lentamente mientras quema.
 * Hace daño mágico para que la resistencia al fuego no lo anule.
 */
public class RisingMagmaBlock extends Block {

    public RisingMagmaBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(entity instanceof LivingEntity living)) return;
        entity.makeStuckInBlock(state, new Vec3(0.6, 0.4, 0.6));
        if (!level.isClientSide && living.tickCount % 10 == 0) {
            living.hurt(level.damageSources().magic(), 3.0F);
            living.setSecondsOnFire(3);
        }
    }
}
