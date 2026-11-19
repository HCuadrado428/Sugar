package com.sugar.entity;

import com.sugar.fx.Fx;
import com.sugar.fx.Shockwaves;
import com.sugar.temple.TempleData;
import com.sugar.temple.TempleObjectives;
import com.sugar.temple.TempleSavedData;
import com.sugar.temple.TempleService;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Capataz de la Forja: minijefe del Templo del Volcán. Sin barra de jefe. Martillazo con onda de choque
 * (se esquiva saltando), giro con el martillo y, después del giro, se queda sin aliento unos segundos.
 * Su vida depende de cuántos jugadores hay dentro del templo al aparecer. Al morir abre la Forja.
 */
public class ForgeForeman extends Monster implements GeoEntity {
    public static final int INTRO = 0, CHASE = 1, SLAM = 2, SPIN = 3, TIRED = 4;
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(ForgeForeman.class, EntityDataSerializers.INT);
    private static final RawAnimation[] ANIMS = {
            RawAnimation.begin().thenPlay("animation.forge_foreman.intro"),
            RawAnimation.begin().thenLoop("animation.forge_foreman.walk"),
            RawAnimation.begin().thenPlay("animation.forge_foreman.slam"),
            RawAnimation.begin().thenPlay("animation.forge_foreman.spin"),
            RawAnimation.begin().thenLoop("animation.forge_foreman.tired")};
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.forge_foreman.idle");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay("animation.forge_foreman.death");
    private static final int INTRO_TIME = 60, SLAM_HIT = 20, SLAM_TIME = 32, SPIN_TIME = 50, TIRED_TIME = 50;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int phaseTicks, cooldown = 30, attacks;

    public ForgeForeman(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 60;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 400.0)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    /** Vida = 200 + 18 por jugador (650 con 25, 1100 con 50). */
    public void scaleFor(int players) {
        double hp = 200 + 18.0 * Math.max(1, players);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        setHealth((float) hp);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE, INTRO);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    private void setPhase(int p) {
        entityData.set(PHASE, p);
        phaseTicks = 0;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return phase() == INTRO && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (phase() == TIRED) amount *= 1.5F;           // sin aliento recibe más
        return super.hurt(source, amount);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        phaseTicks++;
        LivingEntity target = getTarget();
        switch (phase()) {
            case INTRO -> {
                getNavigation().stop();
                if (phaseTicks == 30) {
                    level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.6F);
                    Fx.shake(level, position(), 24, 0.5F, 20);
                }
                if (phaseTicks >= INTRO_TIME) setPhase(CHASE);
            }
            case CHASE -> {
                if (cooldown > 0) cooldown--;
                if (target == null || !target.isAlive()) { getNavigation().stop(); return; }
                getLookControl().setLookAt(target, 30, 30);
                double d = distanceTo(target);
                if (phaseTicks % 10 == 0) getNavigation().moveTo(target, 1.0);
                if (cooldown <= 0) {
                    int crowd = level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(5), p -> !p.isSpectator() && !p.isCreative()).size();
                    if (crowd >= 3 || attacks % 3 == 2 && d < 8) {
                        setPhase(SPIN);
                        level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 2.0F, 0.5F);
                    } else if (d < 5.5) {
                        setPhase(SLAM);
                        level.playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 2.5F, 0.5F);
                    }
                }
            }
            case SLAM -> {
                getNavigation().stop();
                if (target != null && phaseTicks < SLAM_HIT - 4) getLookControl().setLookAt(target, 30, 30);
                setYRot(yHeadRot); yBodyRot = yHeadRot;
                if (phaseTicks == SLAM_HIT) slamHit(level);
                if (phaseTicks >= SLAM_TIME) { setPhase(CHASE); cooldown = 25; attacks++; }
            }
            case SPIN -> {
                if (target != null && phaseTicks % 10 == 0) getNavigation().moveTo(target, 0.7);
                if (phaseTicks > 7 && phaseTicks < SPIN_TIME - 7 && phaseTicks % 6 == 0) spinHit(level);
                if (phaseTicks >= SPIN_TIME) { setPhase(TIRED); getNavigation().stop(); attacks++; }
            }
            case TIRED -> {
                getNavigation().stop();
                if (phaseTicks % 20 == 0) level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2.6, getZ(), 6, 0.3, 0.2, 0.3, 0.02);
                if (phaseTicks >= TIRED_TIME) { setPhase(CHASE); cooldown = 20; }
            }
            default -> setPhase(CHASE);
        }
    }

    /** Punto delante de él donde cae el martillo. */
    private Vec3 hammerPoint() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        return position().add(-Mth.sin(yaw) * 2.6, 0.1, Mth.cos(yaw) * 2.6);
    }

    private void slamHit(ServerLevel level) {
        Vec3 at = hammerPoint();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3.0, 2.0, 3.0), e -> e != this && e.isAlive())) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            e.hurt(damageSources().mobAttack(this), 14.0F);
            e.push(0, 0.7, 0);
            e.hurtMarked = true;
        }
        Shockwaves.spawn(level, at, 0.4, 11, 5.0F, 0.6, Shockwaves.FIRE, this);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLACKSTONE.defaultBlockState()), at.x, at.y + 0.2, at.z, 50, 1.2, 0.2, 1.2, 0.2);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 14, 0.8, 0.2, 0.8, 0.1);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 3.0F, 0.5F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5F, 0.8F);
        Fx.shake(level, at, 20, 0.8F, 12);
    }

    private void spinHit(ServerLevel level) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.2, 0.5, 3.2), e -> e != this && e.isAlive())) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            e.hurt(damageSources().mobAttack(this), 6.0F);
            Vec3 away = e.position().subtract(position()).normalize().scale(1.2);
            e.push(away.x, 0.35, away.z);
            e.hurtMarked = true;
        }
        Fx.ring(level, ParticleTypes.FLAME, position().add(0, 1.2, 0), 3.2, 16, 0.1);
        level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5F, 0.5F);
    }

    @Override
    protected void tickDeath() {
        ++deathTime;
        if (level() instanceof ServerLevel sl && deathTime % 5 == 0)
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.5, getZ(), 10, 0.6, 0.8, 0.6, 0.02);
        if (deathTime >= 40 && !level().isClientSide()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(level() instanceof ServerLevel sl)) return;
        sl.playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_DEATH, SoundSource.HOSTILE, 3.0F, 0.5F);
        TempleSavedData.get(sl.getServer()).findAt(sl.dimension(), blockPosition()).ifPresent(data -> opened(sl, data));
    }

    /** Se abre la Forja: la reja de entrada y la del Gran Cofre. */
    public static void opened(ServerLevel level, TempleData data) {
        if (!data.active) return;
        TempleService.setDoors(level.getServer(), data, TempleObjectives.V_FORGE_GATE, true);
        TempleService.setDoors(level.getServer(), data, TempleObjectives.V_FORGE, true);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Phase", phase());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(PHASE, tag.getInt("Phase") == INTRO ? CHASE : tag.getInt("Phase"));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BLAZE_BURN;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (isDeadOrDying()) return state.setAndContinue(DEATH);
            int p = phase();
            if (p == CHASE) return state.setAndContinue(state.isMoving() ? ANIMS[CHASE] : IDLE);
            return state.setAndContinue(ANIMS[Math.max(0, Math.min(ANIMS.length - 1, p))]);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
