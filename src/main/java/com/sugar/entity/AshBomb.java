package com.sugar.entity;

import net.minecraft.core.BlockPos;

import com.sugar.block.AshStatueBlock;
import com.sugar.registry.ModEntities;
import com.sugar.registry.ModItems;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Bomba de Ceniza: se lanza, rebota y explota a los 3,5 s. No rompe bloques normales,
 * pero sí las Estatuas Agrietadas. Si la Gran Bestia está inhalando, la absorbe y explota dentro.
 */
public class AshBomb extends ThrowableItemProjectile {
    public static final int FUSE = 70;
    private static final int SWALLOW_DELAY = 12;

    private int age;
    private boolean resting;
    @Nullable private AshBeast eater;
    private int swallowTimer;

    public AshBomb(EntityType<? extends AshBomb> type, Level level) {
        super(type, level);
    }

    public AshBomb(Level level, LivingEntity thrower) {
        super(ModEntities.ASH_BOMB.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.ASH_BOMB.get();
    }

    public boolean isSwallowed() {
        return eater != null;
    }

    /** La Bestia la ha aspirado: viaja con su boca y explota dentro. */
    public void swallow(AshBeast beast) {
        if (eater != null) return;
        eater = beast;
        swallowTimer = SWALLOW_DELAY;
        setInvisible(true);
        setNoGravity(true);
        level().playSound(null, blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.HOSTILE, 2.5F, 0.4F);
    }

    /** Tira de la bomba hacia un punto (aspiración). */
    public void pull(Vec3 toward, double strength) {
        if (eater != null) return;
        resting = false;
        setNoGravity(false);
        Vec3 d = toward.subtract(position());
        setDeltaMovement(getDeltaMovement().scale(0.8).add(d.normalize().scale(strength)));
    }

    @Override
    public void tick() {
        if (eater != null) {
            if (!level().isClientSide) {
                if (!eater.isAlive()) { explode(); return; }
                Vec3 m = eater.mouthPos();
                setPos(m.x, m.y, m.z);
                setDeltaMovement(Vec3.ZERO);
                if (--swallowTimer <= 0) {
                    eater.onBombSwallowed(this);
                    ServerLevel sl = (ServerLevel) level();
                    sl.sendParticles(ParticleTypes.EXPLOSION, m.x, m.y, m.z, 3, 0.4, 0.4, 0.4, 0.0);
                    sl.sendParticles(ParticleTypes.LARGE_SMOKE, m.x, m.y, m.z, 20, 0.5, 0.4, 0.5, 0.05);
                    sl.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0F, 0.7F);
                    discard();
                }
            }
            return;
        }
        if (resting) setDeltaMovement(Vec3.ZERO);
        super.tick();
        age++;
        if (level().isClientSide) {
            if (age % 2 == 0) level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.35, getZ(), 0, 0.03, 0);
            if (age > FUSE - 20 && age % 3 == 0) level().addParticle(ParticleTypes.FLAME, getX(), getY() + 0.35, getZ(), 0, 0.02, 0);
        } else if (age >= FUSE) {
            explode();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        Vec3 v = getDeltaMovement();
        Direction dir = hit.getDirection();
        if (dir.getAxis() == Direction.Axis.Y) {
            v = new Vec3(v.x * 0.5, -v.y * 0.3, v.z * 0.5);
            if (dir == Direction.UP && Math.abs(v.y) < 0.08) {
                resting = true;
                Vec3 p = hit.getLocation();
                setPos(p.x, p.y, p.z);
                v = Vec3.ZERO;
                setNoGravity(true);
            }
        } else if (dir.getAxis() == Direction.Axis.X) {
            v = new Vec3(-v.x * 0.4, v.y, v.z * 0.6);
        } else {
            v = new Vec3(v.x * 0.6, v.y, -v.z * 0.4);
        }
        setDeltaMovement(v);
        if (v.lengthSqr() > 0.01) {
            level().playSound(null, blockPosition(), SoundEvents.STONE_HIT, SoundSource.NEUTRAL, 0.6F, 1.4F);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return (target instanceof AshBeast || target instanceof AshHatchling) && super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        // Choca con el cuerpo y cae al suelo
        setDeltaMovement(getDeltaMovement().multiply(-0.2, 0.2, -0.2));
    }

    private void explode() {
        if (level() instanceof ServerLevel sl) {
            sl.explode(this, getX(), getY() + 0.2, getZ(), 2.4F, Level.ExplosionInteraction.NONE);
            AshStatueBlock.breakAround(sl, position(), 3.0);
            // Una explosión también enciende los interruptores de obsidiana cercanos
            BlockPos c = blockPosition();
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-3, -2, -3), c.offset(3, 2, 3))) {
                var st = sl.getBlockState(p);
                if (st.getBlock() instanceof com.sugar.block.HammerSwitchBlock hs)
                    com.sugar.temple.TempleSavedData.get(sl.getServer()).findAt(sl.dimension(), p)
                            .filter(d -> d.active).ifPresent(d -> hs.activate(sl, p.immutable(), st, d));
            }
        }
        discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
