package com.sugar.entity;

import com.sugar.fx.Fx;
import com.sugar.fx.Shockwaves;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.RawAnimation;

/**
 * Caballero del Eco: minijefe del Santuario del Eco. Lento y blindado: de frente su armadura para casi todo el
 * daño (mejor rodearlo o atacarle mientras golpea). Hachazo que hace temblar el suelo y barrido. A media vida
 * la armadura salta en pedazos: queda el esqueleto, más rápido y sin defensa.
 */
public class EchoKnight extends TempleMiniBoss {
    public static final int CHOP = 2, SWEEP = 3, BREAK = 4;
    private static final String P = "animation.echo_knight.";
    private static final RawAnimation IDLE = a("idle", true), WALK = a("walk", true), CHOP_A = a("chop", false), SWEEP_A = a("sweep", false),
            INTRO_A = a("intro", false), BREAK_A = a("armorbreak", false), IDLE2 = a("idle2", true), RUN = a("run", true),
            CHOP2 = a("chop2", false), SWEEP2 = a("sweep2", false), DEATH = a("death", false);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> BROKEN =
            net.minecraft.network.syncher.SynchedEntityData.defineId(EchoKnight.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private int attacks;

    private static RawAnimation a(String n, boolean loop) {
        return loop ? RawAnimation.begin().thenLoop(P + n) : RawAnimation.begin().thenPlay(P + n);
    }

    public EchoKnight(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 400.0).add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.18).add(Attributes.FOLLOW_RANGE, 40.0).add(Attributes.ARMOR, 14.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override protected double baseHealth() { return 200; }
    @Override protected double healthPerPlayer() { return 16; }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(BROKEN, false);
    }

    public boolean broken() {
        return entityData.get(BROKEN);
    }

    @Override
    protected RawAnimation animationFor(int phase, boolean moving) {
        boolean b = broken();
        return switch (phase) {
            case INTRO -> INTRO_A;
            case CHOP -> b ? CHOP2 : CHOP_A;
            case SWEEP -> b ? SWEEP2 : SWEEP_A;
            case BREAK -> BREAK_A;
            default -> moving ? (b ? RUN : WALK) : (b ? IDLE2 : IDLE);
        };
    }

    @Override protected RawAnimation deathAnimation() { return DEATH; }

    @Override
    protected void onIntroTick(ServerLevel level) {
        if (phaseTicks == 32) {
            level.playSound(null, blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.5F, 1.4F);
            Fx.shake(level, position(), 24, 0.4F, 20);
            level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 2, getZ(), 30, 1, 1, 1, 0.05);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean broken = broken();
        if (!broken && phase() != CHOP && phase() != SWEEP && phase() != INTRO) {
            Entity src = source.getEntity();
            if (src != null) {
                Vec3 to = src.position().subtract(position()).multiply(1, 0, 1).normalize();
                float yaw = yBodyRot * Mth.DEG_TO_RAD;
                Vec3 facing = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
                if (to.dot(facing) > 0.35) {                      // de frente: la armadura para casi todo
                    amount *= 0.25F;
                    level().playSound(null, blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0F, 0.7F);
                }
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    protected void combat(ServerLevel level, LivingEntity target) {
        boolean broken = broken();
        if (!broken && getHealth() <= getMaxHealth() * 0.5F && phase() != BREAK) {
            setPhase(BREAK);
            getNavigation().stop();
            level.playSound(null, blockPosition(), SoundEvents.ANVIL_BREAK, SoundSource.HOSTILE, 2F, 0.6F);
            return;
        }
        switch (phase()) {
            case CHASE -> {
                if (cooldown > 0) cooldown--;
                if (target == null || !target.isAlive()) { getNavigation().stop(); return; }
                getLookControl().setLookAt(target, 30, 30);
                if (phaseTicks % 10 == 0) getNavigation().moveTo(target, 1.0);
                if (cooldown > 0) return;
                double d = distanceTo(target);
                int crowd = playersIn(getBoundingBox().inflate(4)).size();
                if (d < 4.5 && (crowd >= 2 || attacks % 2 == 1)) setPhase(SWEEP);
                else if (d < 5) setPhase(CHOP);
            }
            case CHOP -> {
                getNavigation().stop();
                int hit = broken ? 12 : 19, end = broken ? 21 : 30;
                if (target != null && phaseTicks < hit - 4) getLookControl().setLookAt(target, 30, 30);
                setYRot(yHeadRot); yBodyRot = yHeadRot;
                if (phaseTicks == hit) {
                    float yaw = yBodyRot * Mth.DEG_TO_RAD;
                    Vec3 at = position().add(-Mth.sin(yaw) * 2.8, 0.1, Mth.cos(yaw) * 2.8);
                    hitAround(at, 2.4, broken ? 12F : 16F, 0.8, 0.5);
                    Shockwaves.spawn(level, at, 0.35, 9, 4F, 0.5, Shockwaves.ECHO, this);
                    level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y + 0.3, at.z, 16, 0.8, 0.2, 0.8, 0.05);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2.5F, 0.5F);
                    Fx.shake(level, at, 18, 0.6F, 10);
                }
                if (phaseTicks >= end) { setPhase(CHASE); cooldown = broken ? 10 : 25; attacks++; }
            }
            case SWEEP -> {
                getNavigation().stop();
                int hit = broken ? 10 : 15, end = broken ? 18 : 24;
                if (phaseTicks == hit) {
                    hitAround(position(), 4.2, broken ? 8F : 10F, 1.4, 0.3);
                    Fx.ring(level, ParticleTypes.SWEEP_ATTACK, position().add(0, 1.2, 0), 3.4, 10, 0);
                    level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2F, 0.6F);
                }
                if (phaseTicks >= end) { setPhase(CHASE); cooldown = broken ? 10 : 25; attacks++; }
            }
            case BREAK -> {
                getNavigation().stop();
                if (phaseTicks == 15) {
                    entityData.set(BROKEN, true);
                    hitAround(position(), 5.0, 6F, 1.6, 0.5);
                    level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                            net.minecraft.world.level.block.Blocks.DEEPSLATE_BRICKS.defaultBlockState()), getX(), getY() + 2, getZ(), 80, 0.8, 1, 0.8, 0.3);
                    level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5F, 1.2F);
                    getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
                    getAttribute(Attributes.ARMOR).setBaseValue(4.0);
                }
                if (phaseTicks >= 30) { setPhase(CHASE); cooldown = 10; }
            }
            default -> setPhase(CHASE);
        }
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Broken", broken());
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(BROKEN, tag.getBoolean("Broken"));
        if (broken()) { getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3); getAttribute(Attributes.ARMOR).setBaseValue(4.0); }
    }

    @Override protected SoundEvent getAmbientSound() { return broken() ? SoundEvents.SKELETON_AMBIENT : SoundEvents.WARDEN_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return broken() ? SoundEvents.SKELETON_HURT : SoundEvents.IRON_GOLEM_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.WARDEN_DEATH; }
}
