package com.sugar.entity;

import com.sugar.fx.Fx;
import com.sugar.fx.Shockwaves;
import com.sugar.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.RawAnimation;

/**
 * Matriarca del Nido: minijefe de las Fauces de Ceniza. Embiste en línea recta (si choca contra una pared se
 * queda aturdida), llama a sus crías, gira en un remolino de ceniza si la rodean y una bomba de ceniza que
 * explote cerca la aturde. Aturdida recibe el doble de daño.
 */
public class NestMatriarch extends TempleMiniBoss {
    public static final int CHARGE_AIM = 2, CHARGE = 3, SUMMON = 4, SPIN = 5, STUNNED = 6;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.nest_matriarch.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.nest_matriarch.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.nest_matriarch.charge");
    private static final RawAnimation DIZZY = RawAnimation.begin().thenLoop("animation.nest_matriarch.stunned");
    private static final RawAnimation CALL = RawAnimation.begin().thenPlay("animation.nest_matriarch.spit");
    private static final RawAnimation WHIRL = RawAnimation.begin().thenPlay("animation.nest_matriarch.tail");
    private static final RawAnimation INTRO_A = RawAnimation.begin().thenPlay("animation.nest_matriarch.intro");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay("animation.nest_matriarch.death");
    private Vec3 chargeDir = Vec3.ZERO;
    private int attacks;

    public NestMatriarch(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 300.0).add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.26).add(Attributes.FOLLOW_RANGE, 40.0).add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9);
    }

    @Override protected double baseHealth() { return 160; }
    @Override protected double healthPerPlayer() { return 14; }
    @Override public boolean fireImmune() { return true; }

    @Override
    protected RawAnimation animationFor(int phase, boolean moving) {
        return switch (phase) {
            case INTRO -> INTRO_A;
            case CHARGE -> RUN;
            case SUMMON -> CALL;
            case SPIN -> WHIRL;
            case STUNNED -> DIZZY;
            default -> moving ? WALK : IDLE;
        };
    }

    @Override protected RawAnimation deathAnimation() { return DEATH; }

    @Override
    protected void onIntroTick(ServerLevel level) {
        if (phaseTicks == 28) {
            level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5F, 1.3F);
            Fx.shake(level, position(), 24, 0.4F, 20);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2, getZ(), 40, 1.5, 1, 1.5, 0.05);
        }
    }

    /** Una bomba que explota cerca la deja aturdida (como a los dodongos). */
    public void bombed() {
        if (phase() == INTRO || phase() == STUNNED || isDeadOrDying()) return;
        stun(80);
    }

    private void stun(int ticks) {
        setPhase(STUNNED);
        cooldown = ticks;
        getNavigation().stop();
        level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2F, 1.4F);
        if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.CRIT, getX(), getY() + 3.2, getZ(), 20, 0.5, 0.2, 0.5, 0.2);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (phase() == STUNNED) amount *= 2.0F;
        return super.hurt(source, amount);
    }

    @Override
    protected void combat(ServerLevel level, LivingEntity target) {
        switch (phase()) {
            case CHASE -> {
                if (cooldown > 0) cooldown--;
                if (target == null || !target.isAlive()) { getNavigation().stop(); return; }
                getLookControl().setLookAt(target, 30, 30);
                if (phaseTicks % 10 == 0) getNavigation().moveTo(target, 1.0);
                if (cooldown > 0) return;
                double d = distanceTo(target);
                int crowd = playersIn(getBoundingBox().inflate(4)).size();
                if (crowd >= 3) { setPhase(SPIN); level.playSound(null, blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 2F, 0.5F); }
                else if (attacks % 3 == 2) { setPhase(SUMMON); level.playSound(null, blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 2F, 0.7F); }
                else if (d > 5) { setPhase(CHARGE_AIM); level.playSound(null, blockPosition(), SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 1.5F, 1.2F); }
                else if (d < 3.5) { setPhase(SPIN); }
            }
            case CHARGE_AIM -> {
                getNavigation().stop();
                if (target != null) { getLookControl().setLookAt(target, 60, 60); chargeDir = target.position().subtract(position()).multiply(1, 0, 1).normalize(); }
                setYRot((float) (Mth.atan2(chargeDir.z, chargeDir.x) * Mth.RAD_TO_DEG) - 90F); yBodyRot = getYRot(); yHeadRot = getYRot();
                level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.3, getZ(), 4, 0.4, 0.1, 0.4, 0.02);
                if (phaseTicks >= 18) setPhase(CHARGE);
            }
            case CHARGE -> {
                setDeltaMovement(chargeDir.x * 0.62, getDeltaMovement().y, chargeDir.z * 0.62);
                setYRot((float) (Mth.atan2(chargeDir.z, chargeDir.x) * Mth.RAD_TO_DEG) - 90F); yBodyRot = getYRot();
                for (Player p : playersIn(getBoundingBox().inflate(0.6))) {
                    p.hurt(damageSources().mobAttack(this), 10F);
                    p.push(chargeDir.x * 1.4, 0.5, chargeDir.z * 1.4);
                    p.hurtMarked = true;
                }
                if (phaseTicks % 3 == 0) level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3, getZ(), 3, 0.3, 0.1, 0.3, 0.01);
                if (horizontalCollision && phaseTicks > 4) {
                    Shockwaves.spawn(level, position(), 0.4, 7, 4F, 0.5, Shockwaves.ASH, this);
                    Fx.shake(level, position(), 20, 0.7F, 10);
                    stun(70);
                    attacks++;
                } else if (phaseTicks >= 45) { setPhase(CHASE); cooldown = 25; attacks++; }
            }
            case SUMMON -> {
                getNavigation().stop();
                if (phaseTicks == 12) {
                    long alive = level.getEntitiesOfClass(AshHatchling.class, getBoundingBox().inflate(24)).size();
                    int n = alive >= 10 ? 0 : 2 + Math.min(3, playersIn(getBoundingBox().inflate(20)).size() / 8);
                    for (int i = 0; i < n; i++) {
                        AshHatchling h = ModEntities.ASH_HATCHLING.get().create(level);
                        if (h == null) continue;
                        double a = random.nextDouble() * Math.PI * 2;
                        h.moveTo(getX() + Math.cos(a) * 3, getY() + 0.5, getZ() + Math.sin(a) * 3, random.nextFloat() * 360, 0);
                        h.finalizeSpawn(level, level.getCurrentDifficultyAt(h.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
                        h.addTag(com.sugar.temple.WaveRoom.TAG);
                        level.addFreshEntity(h);
                        level.sendParticles(ParticleTypes.LAVA, h.getX(), h.getY() + 0.5, h.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
                    }
                    if (target != null) {
                        Vec3 at = target.position();
                        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.2, at.z, 30, 1.0, 0.1, 1.0, 0.02);
                        hitAround(at, 2.0, 5F, 0.6, 0.3);
                    }
                }
                if (phaseTicks >= 28) { setPhase(CHASE); cooldown = 30; attacks++; }
            }
            case SPIN -> {
                getNavigation().stop();
                if (phaseTicks >= 6 && phaseTicks <= 22 && phaseTicks % 4 == 0) {
                    hitAround(position(), 4.0, 6F, 1.2, 0.4);
                    Fx.ring(level, ParticleTypes.LARGE_SMOKE, position().add(0, 1, 0), 3.8, 18, 0.05);
                }
                if (phaseTicks >= 28) { setPhase(CHASE); cooldown = 30; attacks++; }
            }
            case STUNNED -> {
                getNavigation().stop();
                if (phaseTicks % 10 == 0) level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 3.0, getZ(), 6, 0.4, 0.1, 0.4, 0.05);
                if (phaseTicks >= cooldown) { setPhase(CHASE); cooldown = 20; }
            }
            default -> setPhase(CHASE);
        }
    }

    @Override protected SoundEvent getAmbientSound() { return SoundEvents.BLAZE_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.EVOKER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.EVOKER_DEATH; }
    @Override public float getVoicePitch() { return 0.7F; }
}
