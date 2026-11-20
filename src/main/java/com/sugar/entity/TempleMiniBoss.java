package com.sugar.entity;

import com.sugar.temple.TempleSavedData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/**
 * Base de los minijefes de las salas de oleadas (sin barra de jefe): presentación invulnerable, vida que
 * depende de los jugadores, fase sincronizada para las animaciones y, al morir, se abre la sala.
 */
public abstract class TempleMiniBoss extends Monster implements GeoEntity {
    public static final int INTRO = 0, CHASE = 1;
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(TempleMiniBoss.class, EntityDataSerializers.INT);
    protected static final int INTRO_TIME = 60;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    protected int phaseTicks, cooldown = 30;

    protected TempleMiniBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 50;
        setPersistenceRequired();
    }

    /** Vida base y por jugador. */
    protected abstract double baseHealth();

    protected abstract double healthPerPlayer();

    /** Animación del hueso principal para la fase actual (moving = si se está moviendo). */
    protected abstract RawAnimation animationFor(int phase, boolean moving);

    protected abstract RawAnimation deathAnimation();

    /** Lógica de combate del servidor (fuera de la presentación). */
    protected abstract void combat(ServerLevel level, LivingEntity target);

    protected void onIntroTick(ServerLevel level) {}

    public void scaleFor(int players) {
        double hp = baseHealth() + healthPerPlayer() * Math.max(1, players);
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

    protected void setPhase(int p) {
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
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        phaseTicks++;
        if (phase() == INTRO) {
            getNavigation().stop();
            onIntroTick(level);
            if (phaseTicks >= INTRO_TIME) setPhase(CHASE);
            return;
        }
        combat(level, getTarget());
    }

    /** Jugadores (no creativos) dentro de una caja. */
    protected List<Player> playersIn(AABB box) {
        return level().getEntitiesOfClass(Player.class, box, p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
    }

    /** Daño en un círculo, con empuje hacia fuera. */
    protected void hitAround(Vec3 at, double radius, float damage, double push, double up) {
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, 2.0, radius),
                e -> e != this && e.isAlive() && e.distanceToSqr(at) <= radius * radius + 4 && !(e instanceof TempleMiniBoss))) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            e.hurt(damageSources().mobAttack(this), damage);
            Vec3 away = e.position().subtract(at).multiply(1, 0, 1).normalize().scale(push);
            e.push(away.x, up, away.z);
            e.hurtMarked = true;
        }
    }

    @Override
    protected void tickDeath() {
        ++deathTime;
        if (level() instanceof ServerLevel sl && deathTime % 5 == 0)
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 10, 0.6, 0.6, 0.6, 0.02);
        if (deathTime >= 40 && !level().isClientSide()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl)
            TempleSavedData.get(sl.getServer()).findAt(sl.dimension(), blockPosition()).ifPresent(d -> ForgeForeman.opened(sl, d));
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
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
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (isDeadOrDying()) return state.setAndContinue(deathAnimation());
            return state.setAndContinue(animationFor(phase(), state.isMoving()));
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
