package com.sugar.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
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
 * Keese: murciélago de un solo ojo que revolotea por las salas y se lanza en picado. Variantes: fuego (quema),
 * ceniza (ciega un momento) y eco (oscurece; solo se ve con la Lente del Eco).
 */
public class Keese extends Monster implements GeoEntity {
    public static final int FIRE = 0, ASH = 1, ECHO = 2;
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(Keese.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DIVING = SynchedEntityData.defineId(Keese.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.keese.fly");
    private static final RawAnimation DIVE = RawAnimation.begin().thenLoop("animation.keese.dive");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private BlockPos home;
    private Vec3 wander = Vec3.ZERO;
    private int cooldown = 20, retreat;

    public Keese(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setNoGravity(true);
        this.xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 16.0);
    }

    public void setVariant(int v) {
        entityData.set(VARIANT, Mth.clamp(v, 0, 2));
    }

    public int variant() {
        return entityData.get(VARIANT);
    }

    public boolean diving() {
        return entityData.get(DIVING);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(VARIANT, FIRE);
        entityData.define(DIVING, false);
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean causeFallDamage(float distance, float mult, DamageSource source) {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return variant() == FIRE;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        ServerLevel level = (ServerLevel) level();
        if (home == null) home = blockPosition();
        LivingEntity target = getTarget();
        Vec3 desired;
        if (cooldown > 0) cooldown--;
        if (retreat > 0) {
            retreat--;
            Vec3 away = target != null ? position().subtract(target.position()).multiply(1, 0, 1).normalize() : Vec3.ZERO;
            desired = away.scale(0.25).add(0, 0.18, 0);
            entityData.set(DIVING, false);
        } else if (target != null && target.isAlive() && cooldown == 0 && distanceTo(target) < 16) {
            desired = target.getEyePosition().subtract(0, 0.4, 0).subtract(position()).normalize().scale(0.42);
            entityData.set(DIVING, true);
            if (getBoundingBox().inflate(0.35).intersects(target.getBoundingBox())) bite(level, target);
        } else {
            entityData.set(DIVING, false);
            if (tickCount % 50 == 0 || wander == Vec3.ZERO)
                wander = Vec3.atCenterOf(home).add(random.nextGaussian() * 3, random.nextDouble() * 2 - 0.5, random.nextGaussian() * 3);
            Vec3 d = wander.subtract(position());
            desired = d.length() > 0.2 ? d.normalize().scale(Math.min(0.16, d.length() * 0.08)) : Vec3.ZERO;
            desired = desired.add(0, Math.sin(tickCount * 0.25) * 0.03, 0);
        }
        setDeltaMovement(getDeltaMovement().lerp(desired, 0.25));
        Vec3 v = getDeltaMovement();
        if (v.horizontalDistanceSqr() > 0.0004) { setYRot((float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90F); yBodyRot = getYRot(); yHeadRot = getYRot(); }
        if (tickCount % 6 == 0) {
            switch (variant()) {
                case FIRE -> level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.3, getZ(), 1, 0.1, 0.1, 0.1, 0.005);
                case ASH -> level.sendParticles(ParticleTypes.ASH, getX(), getY() + 0.3, getZ(), 3, 0.2, 0.1, 0.2, 0.01);
                default -> level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 0.3, getZ(), 1, 0.1, 0.1, 0.1, 0.005);
            }
        }
    }

    private void bite(ServerLevel level, LivingEntity target) {
        if (target instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        target.hurt(damageSources().mobAttack(this), variant() == ECHO ? 4F : 3F);
        switch (variant()) {
            case FIRE -> target.setSecondsOnFire(3);
            case ASH -> target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
            default -> target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
        }
        level.playSound(null, blockPosition(), SoundEvents.BAT_HURT, getSoundSource(), 1.0F, 0.6F);
        cooldown = 40 + random.nextInt(20);
        retreat = 18;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override protected SoundEvent getAmbientSound() { return SoundEvents.BAT_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.BAT_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.BAT_DEATH; }
    @Override public float getVoicePitch() { return 0.7F; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", variant());
        if (home != null) tag.putLong("Home", home.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setVariant(tag.getInt("Variant"));
        if (tag.contains("Home")) home = BlockPos.of(tag.getLong("Home"));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, s -> s.setAndContinue(diving() ? DIVE : FLY)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
