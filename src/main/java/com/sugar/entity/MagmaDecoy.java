package com.sugar.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Cabeza señuelo: igual que la Sierpe pero sin el brillo en los ojos.
 * Si un jugador la golpea, estalla en llamas y hace daño alrededor.
 */
public class MagmaDecoy extends Mob implements GeoEntity {
    private static final RawAnimation EMERGE = RawAnimation.begin().thenPlay("animation.magma_serpent.emerge")
            .thenLoop("animation.magma_serpent.head_idle");
    private static final int LIFETIME = 110;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private BlockPos vent = BlockPos.ZERO;
    private float burstDamage = 5.0F;
    private int age;

    public MagmaDecoy(EntityType<? extends MagmaDecoy> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public void setup(BlockPos vent, float burstDamage) {
        this.vent = vent;
        this.burstDamage = burstDamage;
        moveTo(vent.getX() + 0.5, vent.getY() - 4.0, vent.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            yBodyRot = getYRot();
            yHeadRot = getYRot();
            return;
        }
        age++;
        setDeltaMovement(Vec3.ZERO);
        double top = vent.getY() + 1.0;
        double bottom = vent.getY() - 4.0;
        double targetY = age < 20 ? bottom + (top - bottom) * (age / 20.0)
                : age > LIFETIME - 20 ? top - (top - bottom) * ((age - (LIFETIME - 20)) / 20.0)
                : top;
        setPos(vent.getX() + 0.5, targetY, vent.getZ() + 0.5);

        Player nearest = level().getNearestPlayer(this, 32.0);
        if (nearest != null) {
            float yaw = (float) Math.toDegrees(Math.atan2(nearest.getZ() - getZ(), nearest.getX() - getX())) - 90.0F;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }
        if (age >= LIFETIME) discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            discard();
            return true;
        }
        if (source.getEntity() instanceof Player) {
            burst();
            return true;
        }
        return false;
    }

    private void burst() {
        if (!(level() instanceof ServerLevel sl)) return;
        sl.sendParticles(ParticleTypes.FLAME, getX(), getY() + 3.0, getZ(), 80, 1.5, 1.5, 1.5, 0.08);
        sl.sendParticles(ParticleTypes.LAVA, getX(), getY() + 3.0, getZ(), 12, 1.0, 1.0, 1.0, 0.0);
        sl.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2F, 1.2F);
        for (Player p : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(3.0), p -> !p.isSpectator())) {
            p.hurt(damageSources().mobAttack(this), burstDamage);
            p.setSecondsOnFire(4);
        }
        discard();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> state.setAndContinue(EMERGE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
