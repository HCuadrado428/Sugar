package com.sugar.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Lago de magma que se vacía: funciona como una puerta de evento (al "abrir" su canal desaparece),
 * pero no tiene colisión y quema a quien caiga dentro mientras está lleno.
 */
public class DrainableMagmaBlock extends EventDoorBlock {

    public DrainableMagmaBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean skipRendering(BlockState state, BlockState adjacent, net.minecraft.core.Direction direction) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        // Solo a los seres vivos: los objetos de quien muere no se queman
        if (state.getValue(OPEN) || level.isClientSide || !(entity instanceof net.minecraft.world.entity.LivingEntity)) return;
        entity.makeStuckInBlock(state, new net.minecraft.world.phys.Vec3(0.6, 0.5, 0.6));
        entity.setSecondsOnFire(8);
        entity.hurt(level.damageSources().lava(), 4.0F);
    }
}
