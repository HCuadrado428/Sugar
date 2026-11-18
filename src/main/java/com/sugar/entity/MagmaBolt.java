package com.sugar.entity;

import com.sugar.boss.SugarBoss;
import com.sugar.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Bola de magma: como una bola de fuego pequeña, pero no prende fuego al templo. */
public class MagmaBolt extends SmallFireball {
    private float damage = 6.0F;

    public MagmaBolt(EntityType<? extends MagmaBolt> type, Level level) {
        super(type, level);
    }

    public static void shoot(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 target, float damage) {
        MagmaBolt bolt = new MagmaBolt(ModEntities.MAGMA_BOLT.get(), level);
        bolt.setOwner(owner);
        bolt.moveTo(from.x, from.y, from.z, 0.0F, 0.0F);
        Vec3 dir = target.subtract(from).normalize();
        bolt.setDeltaMovement(dir.scale(0.6));
        bolt.xPower = dir.x * 0.1;
        bolt.yPower = dir.y * 0.1;
        bolt.zPower = dir.z * 0.1;
        bolt.damage = damage;
        level.addFreshEntity(bolt);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide) return;
        Entity target = result.getEntity();
        if (target instanceof SugarBoss || target instanceof MagmaDecoy || target == getOwner()) return;
        target.hurt(damageSources().fireball(this, getOwner()), damage);
        target.setSecondsOnFire(4);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        // Sin fuego en los bloques: el templo no se quema
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
