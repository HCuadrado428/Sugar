package com.sugar.entity;

import com.sugar.SugarConfig;
import com.sugar.block.MagmaVentBlock;
import com.sugar.boss.SugarBoss;
import com.sugar.registry.ModEntities;
import com.sugar.registry.ModItems;
import com.sugar.temple.TempleData;
import com.sugar.temple.TempleService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Sierpe de Magma.
 *
 * Ciclo:
 *  ENTERRADA → (elige respiradero) EMERGIENDO → ASOMADA: solo la aturde el Martillo de Obsidiana
 *  (más golpes cuantos más jugadores). Mientras está asomada lanza un chorro de fuego que barre
 *  la arena de lado a lado; si nadie la aturde, vuelve a hundirse.
 *  ATURDIDA: recibe todo el daño durante 8 s → DESPEGUE → VUELO en círculos lanzando magma
 *  → PICADO hacia un respiradero → ENTERRADA.
 *  Con muchos jugadores asoman además cabezas señuelo en otros respiraderos.
 *  Por debajo del 40% de vida: la mitad de los respiraderos entran en erupción y caen más rocas.
 */
public class MagmaSerpent extends SugarBoss implements GeoEntity {
    public static final int BURROWED = 0, EMERGING = 1, HEAD = 2, SUBMERGING = 3,
            STUNNED = 4, TAKEOFF = 5, FLYING = 6, DIVING = 7, INTRO = 8, WAKING = 9, PLUNGE = 10;
    private static final int WAKE_TICKS = 28, PLUNGE_TICKS = 26;
    private static final int INTRO_RISE = 18 + 48, INTRO_LAP = 110, INTRO_TOTAL = INTRO_RISE + INTRO_LAP + 40;   // 48 ticks = animación "intro"

    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(MagmaSerpent.class, EntityDataSerializers.INT);

    private static final RawAnimation ANIM_HEAD = RawAnimation.begin().thenLoop("animation.magma_serpent.head_idle");
    private static final RawAnimation ANIM_STUNNED = RawAnimation.begin()
            .thenPlay("animation.magma_serpent.stun_hit").thenLoop("animation.magma_serpent.stunned");
    private static final RawAnimation ANIM_FLY = RawAnimation.begin().thenLoop("animation.magma_serpent.fly");
    private static final RawAnimation ANIM_EMERGE = RawAnimation.begin().thenPlay("animation.magma_serpent.emerge");
    private static final RawAnimation ANIM_BREATH = RawAnimation.begin().thenPlay("animation.magma_serpent.breath");

    // Lanzallamas (sincronizado con la animación "breath": coge aire 0,5 s y escupe fuego hasta los 2,8 s)
    private static final int BREATH_START = 14, FIRE_START = BREATH_START + 12, FIRE_END = BREATH_START + 56, HEAD_TIME = 104;
    private static final int EMERGE_TICKS = 22, SUBMERGE_TICKS = 20, TAKEOFF_TICKS = 40;
    private static final float TURN_SPEED = 7.0F;          // grados por tick al girar
    private static final double BREATH_RANGE = 15.0, BREATH_ANGLE = Math.toRadians(24);
    // Barrido del cuello y la cabeza (igual que la animación "breath": 70° a cada lado, izquierda → derecha → izquierda)
    private static final float SWEEP_DEG = 70.0F, ENRAGED_BODY_SWEEP = 25.0F;
    private static final float FIRE_T0 = 0.62F, FIRE_T1 = 2.8F;
    private static final double MOUTH_UP = 3.0, MOUTH_FORWARD = 2.0;     // posición de la boca en la pose de ataque
    private static final RawAnimation ANIM_DEATH = RawAnimation.begin().thenPlayAndHold("animation.magma_serpent.death");
    private static final RawAnimation ANIM_INTRO = RawAnimation.begin().thenPlayAndHold("animation.magma_serpent.intro");
    private static final RawAnimation ANIM_SUBMERGE = RawAnimation.begin().thenPlayAndHold("animation.magma_serpent.submerge");
    private static final RawAnimation ANIM_WAKE = RawAnimation.begin().thenPlayAndHold("animation.magma_serpent.wake");
    private static final RawAnimation ANIM_TAKEOFF = RawAnimation.begin().thenPlay("animation.magma_serpent.takeoff").thenLoop("animation.magma_serpent.fly");
    private static final RawAnimation ANIM_DIVE = RawAnimation.begin().thenPlayAndHold("animation.magma_serpent.dive");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int timer;
    private int hammerHits;
    private int hintCooldown;
    private boolean enraged;
    @Nullable private BlockPos home;
    @Nullable private BlockPos vent;
    private double flyAngle;
    private float breathYaw;
    private float breathPitch;
    private float breathSweep;
    private double takeoffY;
    private double plungeY;
    private boolean introLap;
    private int introTicks;

    public MagmaSerpent(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
        this.xpReward = 800;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBossAttributes()
                .add(Attributes.MAX_HEALTH, 300.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ATTACK_DAMAGE, 8.0);
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.RED;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE, BURROWED);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    private void setPhase(int phase) {
        entityData.set(PHASE, phase);
        timer = 0;
        hammerHits = 0;
        setInvisible(phase == BURROWED);
    }

    // ---------- Presentación ----------

    /** El volcán retumba, revienta el altar, se alza rugiendo, da una vuelta volando y se zambulle. */
    @Override
    public void beginIntro() {
        setIntro(true);
        introTicks = 0;
        introLap = false;
        Vec3 c = center();
        setPos(c.x, c.y - 6.0, c.z);
        setPhase(INTRO);
        setInvisible(false);
    }

    private void introTick(ServerLevel level) {
        Vec3 c = center();
        if (timer == 1) {
            announceBoss();
            level.playSound(null, BlockPos.containing(c), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 4.0F, 0.6F);
        }
        // Retumbar: chorros de fuego en los respiraderos (solo partículas, no hacen daño)
        if (timer < 40 && timer % 4 == 0) {
            for (BlockPos v : vents(level)) {
                level.sendParticles(ParticleTypes.FLAME, v.getX() + 0.5, v.getY() + 1.2, v.getZ() + 0.5, 0, 0, 1, 0, 0.5 + random.nextDouble() * 0.3);
                level.sendParticles(ParticleTypes.LAVA, v.getX() + 0.5, v.getY() + 1.1, v.getZ() + 0.5, 1, 0.2, 0.1, 0.2, 0);
            }
            level.playSound(null, BlockPos.containing(c), SoundEvents.LAVA_POP, SoundSource.HOSTILE, 3.0F, 0.5F);
        }
        // Revienta el altar y sale con impulso
        if (timer == 18) {
            triggerAnim("main", "intro");
            level.playSound(null, BlockPos.containing(c), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0F, 0.6F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BASALT.defaultBlockState()), c.x, c.y + 0.5, c.z, 80, 1.5, 0.4, 1.5, 0.2);
            level.sendParticles(ParticleTypes.LAVA, c.x, c.y + 0.5, c.z, 30, 1.2, 0.5, 1.2, 0);
        }
        if (timer >= 18 && timer <= 40) {
            double u = backOut(Math.min(1.0, (timer - 18) / 22.0));
            setPos(c.x, c.y - 6.0 + 6.0 * u, c.z);
        }
        // Ruge y escupe una columna de fuego hacia el cielo
        if (timer == 38) level.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6.0F, 0.6F);
        if (timer >= 38 && timer <= 52) {
            Vec3 m = position().add(0, 6.2, 0);
            for (int i = 0; i < 6; i++) {
                level.sendParticles(ParticleTypes.FLAME, m.x, m.y, m.z, 0, (random.nextDouble() - 0.5) * 0.3, 1.0, (random.nextDouble() - 0.5) * 0.3, 0.7);
            }
        }
        if (timer >= INTRO_RISE) {
            Vec3 cc = center();
            flyAngle = Math.atan2(getZ() - cc.z, getX() - cc.x);
            introLap = true;
            setPhase(FLYING);
        }
    }

    // ---------- Lógica ----------

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level)) return;
        if (home == null) home = blockPosition();
        setDeltaMovement(Vec3.ZERO);
        timer++;
        if (hintCooldown > 0) hintCooldown--;

        if (inIntro()) introProgress = Math.min(1.0F, ++introTicks / (float) INTRO_TOTAL);
        switch (phase()) {
            case INTRO -> introTick(level);
            case BURROWED -> {
                if (vent != null) setPos(vent.getX() + 0.5, vent.getY() - 5.0, vent.getZ() + 0.5);
                if (introLap) { introLap = false; setIntro(false); }        // fin de la presentación: empieza el combate
                if (timer >= 50) emerge(level);
            }
            case EMERGING -> {
                // Sale con impulso y se asienta con un pequeño rebote
                double u = backOut(Math.min(1.0, timer / (double) EMERGE_TICKS));
                Vec3 top = ventTop();
                setPos(top.x, top.y - 5.0 + 5.0 * u, top.z);
                faceNearestPlayer();
                if (timer >= EMERGE_TICKS) setPhase(HEAD);
            }
            case HEAD -> {
                if (timer < BREATH_START) faceNearestPlayer();
                if (timer == BREATH_START) startBreath(level);
                if (timer > BREATH_START && timer < FIRE_START) {      // mientras coge aire, se encara al grupo
                    float yaw = Mth.approachDegrees(getYRot(), breathYaw, 9.0F);
                    setYRot(yaw); yBodyRot = yaw; yHeadRot = yaw;
                }
                if (timer >= FIRE_START && timer <= FIRE_END) breathTick(level, (timer - FIRE_START) / (float) (FIRE_END - FIRE_START));
                if (timer >= HEAD_TIME) setPhase(SUBMERGING);
            }
            case SUBMERGING -> {
                // Coge impulso y se hunde acelerando (animación "submerge")
                double u = Math.min(1.0, timer / (double) SUBMERGE_TICKS);
                Vec3 top = ventTop();
                double up = timer <= 5 ? 0.4 * Math.sin(Math.PI * timer / 10.0) : 0;
                setPos(top.x, top.y + up - 6.0 * u * u * u, top.z);
                if (timer == 10) lavaSplash(level, 1.0F);
                if (timer >= SUBMERGE_TICKS) setPhase(BURROWED);
            }
            case STUNNED -> {
                stunnedEffects(level);
                if (timer >= 160) setPhase(WAKING);
            }
            case WAKING -> {
                // Espasmos, se incorpora y ruge enfadada (animación "wake")
                if (timer <= 7 && timer % 2 == 0) {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2.0, getZ(), 3, 0.6, 0.4, 0.6, 0.02);
                }
                if (timer == 16) level.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0F, 0.9F);
                if (timer >= WAKE_TICKS) setPhase(TAKEOFF);
            }
            case TAKEOFF -> {
                if (timer == 1) takeoffY = getY();
                if (timer == 11) {                 // aletazo de despegue: levanta ceniza
                    level.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 4.0F, 0.7F);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3, getZ(), 30, 2.5, 0.2, 2.5, 0.05);
                    level.sendParticles(ParticleTypes.WHITE_ASH, getX(), getY() + 0.5, getZ(), 60, 3.0, 0.5, 3.0, 0.05);
                }
                if (timer == 24 || timer == 37) level.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 3.0F, 0.8F);
                double u = smooth(Math.min(1.0, timer / (double) TAKEOFF_TICKS));
                setPos(getX(), takeoffY + (flyHeight() - takeoffY) * u, getZ());
                if (timer >= TAKEOFF_TICKS) {
                    Vec3 c = center();
                    flyAngle = Math.atan2(getZ() - c.z, getX() - c.x);
                    setPhase(FLYING);
                }
            }
            case FLYING -> {
                Vec3 c = center();
                double radius = flyRadius();
                flyAngle += 0.6 / Math.max(6.0, radius);
                moveToward(new Vec3(c.x + Math.cos(flyAngle) * radius, flyHeight(), c.z + Math.sin(flyAngle) * radius), 0.7);
                if (!introLap && timer % (enraged ? 7 : 12) == 0) dropBolt(level);
                if (introLap && timer >= INTRO_LAP) {
                    vent = pickVent(level, null);
                    setPhase(DIVING);
                } else if (timer >= 220) {
                    vent = pickVent(level, null);
                    setPhase(DIVING);
                }
            }
            case DIVING -> {
                // Picado hacia el respiradero (animación "dive": gira hasta quedar cabeza abajo)
                Vec3 target = ventTop().add(0, 1.5, 0);
                moveToward(target, 0.8);
                if (position().distanceTo(target) < 1.0 || timer > 120) {
                    plungeY = getY();
                    setPhase(PLUNGE);
                }
            }
            case PLUNGE -> {
                // Entra de cabeza; la cola es lo último en desaparecer
                double u = Math.min(1.0, timer / (double) PLUNGE_TICKS);
                Vec3 top = ventTop();
                setPos(top.x, plungeY - 10.5 * u * u, top.z);
                if (timer == 3) lavaSplash(level, 1.4F);
                if (timer == 18) lavaSplash(level, 0.8F);
                if (timer >= PLUNGE_TICKS) setPhase(BURROWED);
            }
            default -> setPhase(BURROWED);
        }

        if (phase() != BURROWED && !inIntro() && tickCount % (enraged ? 40 : 80) == 0) rockRain(level);
        if (!enraged && getHealth() / getMaxHealth() < 0.4F) enrage(level);
    }

    private void emerge(ServerLevel level) {
        vent = pickVent(level, null);
        setPos(vent.getX() + 0.5, vent.getY() - 5.0, vent.getZ() + 0.5);
        setPhase(EMERGING);
        triggerAnim("main", "emerge");
        lavaSplash(level, 1.2F);
        level.playSound(null, vent, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0F, 0.7F);
        level.sendParticles(ParticleTypes.LAVA, vent.getX() + 0.5, vent.getY() + 1.2, vent.getZ() + 0.5, 20, 0.6, 0.3, 0.6, 0.0);

        // Señuelos: uno por cada 10 jugadores
        int decoys = scaledPlayers() / 10;
        if (decoys > 0) {
            List<BlockPos> others = new ArrayList<>(vents(level));
            others.remove(vent);
            others.removeIf(p -> MagmaVentBlock.isErupting(level, p));
            Collections.shuffle(others);
            for (int i = 0; i < Math.min(decoys, others.size()); i++) {
                MagmaDecoy decoy = new MagmaDecoy(ModEntities.MAGMA_DECOY.get(), level);
                decoy.setup(others.get(i), scaledDamage(5.0F));
                level.addFreshEntity(decoy);
            }
        }
    }

    /** Salpicadura de lava en el respiradero actual (al salir o al meterse). */
    private void lavaSplash(ServerLevel level, float size) {
        if (vent == null) return;
        double x = vent.getX() + 0.5, y = vent.getY() + 1.1, z = vent.getZ() + 0.5;
        level.sendParticles(ParticleTypes.LAVA, x, y, z, (int) (25 * size), 0.7 * size, 0.3, 0.7 * size, 0.0);
        level.sendParticles(ParticleTypes.FLAME, x, y, z, (int) (30 * size), 0.9 * size, 0.4, 0.9 * size, 0.06);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.3, z, (int) (12 * size), 0.6 * size, 0.4, 0.6 * size, 0.03);
        level.playSound(null, vent, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.HOSTILE, 2.5F * size, 0.6F);
        level.playSound(null, vent, SoundEvents.LAVA_POP, SoundSource.HOSTILE, 2.5F, 0.5F);
    }

    private void stun(ServerLevel level) {
        setPhase(STUNNED);
        setPos(ventTop());
        level.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_HURT, SoundSource.HOSTILE, 3.0F, 0.6F);
        templeData().ifPresent(d -> TempleService.actionbar(level, d,
                Component.literal("¡La Sierpe está aturdida! ¡Atacad!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
    }

    private void enrage(ServerLevel level) {
        enraged = true;
        level.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4.0F, 0.5F);
        List<BlockPos> list = new ArrayList<>(vents(level));
        list.remove(vent);
        Collections.shuffle(list);
        for (int i = 0; i < list.size() / 2; i++) MagmaVentBlock.setErupting(level, list.get(i), true);
        templeData().ifPresent(d -> TempleService.actionbar(level, d,
                Component.literal("¡El volcán despierta!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
    }

    // ---------- Ataques ----------

    private List<ServerPlayer> targets(ServerLevel level) {
        Optional<TempleData> data = templeData();
        if (data.isPresent()) return TempleService.playersInside(level, data.get()).stream().filter(p -> !p.isCreative()).toList();
        return level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(40.0),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
    }

    private Vec3 forward(float yawDeg) {
        double r = Math.toRadians(yawDeg);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    private Vec3 mouth(float yawDeg) {
        return position().add(0, MOUTH_UP, 0).add(forward(yawDeg).scale(MOUTH_FORWARD));
    }

    private void startBreath(ServerLevel level) {
        triggerAnim("main", "breath");
        List<ServerPlayer> players = targets(level);
        ServerPlayer target = players.isEmpty() ? null : players.get(random.nextInt(players.size()));
        breathYaw = target == null ? getYRot()
                : (float) Math.toDegrees(Math.atan2(target.getZ() - getZ(), target.getX() - getX())) - 90.0F;
        breathSweep = enraged ? ENRAGED_BODY_SWEEP * (random.nextBoolean() ? 1 : -1) : 0.0F;
        if (target != null) {
            Vec3 m = mouth(breathYaw);
            double dy = m.y - (target.getY() + 1.0);
            double dh = Math.sqrt(Math.pow(target.getX() - m.x, 2) + Math.pow(target.getZ() - m.z, 2));
            breathPitch = (float) Math.max(0, Math.min(50, Math.toDegrees(Math.atan2(dy, Math.max(1, dh)))));
        } else {
            breathPitch = 15;
        }
        level.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.5F, 1.3F);
    }

    /** Posición del barrido de la animación "breath" (-1..1) en el segundo t. */
    private static float sweep(float t) {
        if (t <= 0) return 0;
        if (t < FIRE_T0) return (float) -smooth(t / FIRE_T0);
        if (t <= FIRE_T1) return (float) -Math.cos(Math.PI * 2 * (t - FIRE_T0) / (FIRE_T1 - FIRE_T0));
        if (t < FIRE_T1 + 0.3F) return (float) (-1 + smooth((t - FIRE_T1) / 0.3F));
        return 0;
    }

    /** Cabeceo de la animación (grados, positivo = apunta más abajo). */
    private static float nod(float t) {
        if (t < FIRE_T0 || t > FIRE_T1) return 0;
        return (float) (8.0 * Math.sin(Math.PI * 4 * (t - FIRE_T0) / (FIRE_T1 - FIRE_T0)));
    }

    /** Escupe fuego en la dirección de la cabeza, que barre de lado a lado mientras el cuerpo se queda quieto. */
    private void breathTick(ServerLevel level, float progress) {
        float t = (timer - BREATH_START) / 20.0F;                   // segundo de la animación
        float bodyYaw = breathYaw + breathSweep * (float) Math.sin(Math.PI * progress);
        setYRot(bodyYaw);
        yBodyRot = bodyYaw;
        yHeadRot = bodyYaw;

        float sign = SugarConfig.INVERT_BREATH_SWEEP.get() ? -1.0F : 1.0F;
        float yaw = bodyYaw + sign * SWEEP_DEG * sweep(t);          // hacia dónde mira la cabeza
        float pitchDeg = breathPitch + sign * nod(t);

        double pitch = Math.toRadians(Math.max(0, Math.min(60, pitchDeg)));
        Vec3 fwd = forward(yaw);
        Vec3 dir = fwd.scale(Math.cos(pitch)).add(0, -Math.sin(pitch), 0).normalize();
        Vec3 m = mouth(yaw);

        // Chorro de llamas: partículas lanzadas en la dirección del aliento
        for (int i = 0; i < 6; i++) {
            Vec3 v = dir.add((random.nextDouble() - 0.5) * 0.18, (random.nextDouble() - 0.5) * 0.14, (random.nextDouble() - 0.5) * 0.18);
            level.sendParticles(ParticleTypes.FLAME, m.x, m.y, m.z, 0, v.x, v.y, v.z, 0.75 + random.nextDouble() * 0.35);
        }
        if (timer % 2 == 0) {
            Vec3 v = dir.add(0, 0.1, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, m.x, m.y, m.z, 0, v.x, v.y, v.z, 0.45);
        }
        if (timer % 3 == 0) {
            Vec3 p = m.add(dir.scale(3 + random.nextDouble() * 8));
            level.sendParticles(ParticleTypes.LAVA, p.x, p.y, p.z, 1, 0.3, 0.3, 0.3, 0.0);
        }
        if (timer % 6 == 0) level.playSound(null, BlockPos.containing(m), SoundEvents.FIRE_AMBIENT, SoundSource.HOSTILE, 3.0F, 0.6F);
        if (timer % 12 == 0) level.playSound(null, BlockPos.containing(m), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.0F, 0.5F);

        // Daño en cono (cubrirse detrás de un bloque protege)
        if (timer % 4 == 0) {
            for (ServerPlayer p : targets(level)) {
                Vec3 to = p.position().add(0, 1.0, 0).subtract(m);
                double dist = to.length();
                if (dist > BREATH_RANGE || dist < 0.01) continue;
                double angle = Math.acos(Math.max(-1, Math.min(1, to.normalize().dot(dir))));
                if (angle > BREATH_ANGLE + 0.15 / Math.max(1, dist)) continue;
                HitResult hit = level.clip(new ClipContext(m, p.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                if (hit.getType() != HitResult.Type.MISS) continue;
                p.hurt(damageSources().mobAttack(this), scaledDamage(2.0F));
                p.setSecondsOnFire(5);
            }
        }
    }

    private void stunnedEffects(ServerLevel level) {
        Vec3 fwd = forward(getYRot());
        Vec3 head = position().add(fwd.scale(2.9)).add(0, 1.9, 0);
        // Mareo: chispas girando sobre la cabeza
        for (int k = 0; k < 3; k++) {
            double a = timer * 0.35 + k * Math.PI * 2 / 3;
            level.sendParticles(ParticleTypes.CRIT, head.x + Math.cos(a) * 0.9, head.y, head.z + Math.sin(a) * 0.9, 1, 0, 0, 0, 0);
        }
        // Humo por la nariz al respirar
        if (timer % 15 == 0) {
            Vec3 nose = position().add(fwd.scale(3.7)).add(0, 0.9, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, nose.x, nose.y, nose.z, 0, fwd.x, 0.15, fwd.z, 0.12);
            level.sendParticles(ParticleTypes.SMOKE, nose.x, nose.y, nose.z, 4, 0.15, 0.05, 0.15, 0.01);
        }
    }

    private void dropBolt(ServerLevel level) {
        List<ServerPlayer> players = targets(level);
        if (players.isEmpty()) return;
        ServerPlayer target = players.get(random.nextInt(players.size()));
        MagmaBolt.shoot(level, this, position().add(0, 1.0, 0), target.position(), scaledDamage(6.0F));
    }

    private void rockRain(ServerLevel level) {
        List<ServerPlayer> players = new ArrayList<>(targets(level));
        if (players.isEmpty()) return;
        Collections.shuffle(players);
        int count = Math.min(6, 1 + scaledPlayers() / 8);
        for (int i = 0; i < Math.min(count, players.size()); i++) {
            FallingRock.spawnAbove(level, players.get(i), 16, scaledDamage(8.0F));
        }
    }

    // ---------- Daño ----------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (inIntro()) return false;

        int phase = phase();
        if (phase == HEAD) {
            if (source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player) {
                if (player.getMainHandItem().is(ModItems.OBSIDIAN_HAMMER.get())) {
                    hammerHits++;
                    int needed = 1 + scaledPlayers() / 15;
                    level().playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5F, 0.6F);
                    if (hammerHits >= needed) {
                        stun((ServerLevel) level());
                    } else {
                        templeData().ifPresent(d -> TempleService.actionbar((ServerLevel) level(), d,
                                Component.literal("Martillazos: " + hammerHits + "/" + needed).withStyle(ChatFormatting.GOLD)));
                    }
                } else if (hintCooldown == 0) {
                    hintCooldown = 40;
                    player.displayClientMessage(Component.literal("Su piel es demasiado dura... ¡Necesitas un Martillo de Obsidiana!")
                            .withStyle(ChatFormatting.RED), true);
                }
            }
            return false;
        }
        if (phase == STUNNED || phase == WAKING || phase == TAKEOFF || phase == FLYING) {
            return super.hurt(source, amount);
        }
        return false;
    }

    @Override
    public boolean isPickable() {
        return phase() != BURROWED && super.isPickable();
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel level) {
            for (BlockPos p : vents(level)) MagmaVentBlock.setErupting(level, p, false);
        }
        super.die(source);
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 fwd = forward(getYRot());
        BlockPos here = blockPosition();

        if (deathTime == 1) {
            sl.playSound(null, here, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 5.0F, 0.6F);
        }
        if (deathTime <= 38 && deathTime % 4 == 0) {          // se retuerce: estallidos de lava
            Vec3 p = position().add((random.nextDouble() - 0.5) * 2, 1 + random.nextDouble() * 4, (random.nextDouble() - 0.5) * 2);
            sl.sendParticles(ParticleTypes.LAVA, p.x, p.y, p.z, 6, 0.4, 0.4, 0.4, 0.0);
            sl.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 12, 0.3, 0.3, 0.3, 0.05);
            if (deathTime % 8 == 0) sl.playSound(null, here, SoundEvents.ENDER_DRAGON_HURT, SoundSource.HOSTILE, 3.0F, 0.6F + random.nextFloat() * 0.3F);
        }
        if (deathTime == 55) {                                 // la cabeza golpea el suelo
            Vec3 h = position().add(fwd.scale(2.6)).add(0, 0.5, 0);
            sl.playSound(null, here, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0F, 0.6F);
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BASALT.defaultBlockState()), h.x, h.y, h.z, 60, 1.2, 0.3, 1.2, 0.15);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, h.x, h.y, h.z, 20, 1.0, 0.3, 1.0, 0.03);
        }
        if (deathTime >= 60 && deathTime % 2 == 0) {           // se deshace en roca y lava
            for (int i = 0; i < 3; i++) {
                double t = random.nextDouble();
                Vec3 p = position().add(fwd.scale(t * 2.8)).add(0, 0.4 + (1 - t) * 3.0, 0);
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MAGMA_BLOCK.defaultBlockState()), p.x, p.y, p.z, 8, 0.5, 0.4, 0.5, 0.05);
                sl.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 4, 0.4, 0.3, 0.4, 0.02);
            }
            if (deathTime % 6 == 0) sl.playSound(null, here, SoundEvents.BASALT_BREAK, SoundSource.HOSTILE, 2.0F, 0.6F);
        }
        if (deathTime >= 80) {
            sl.sendParticles(ParticleTypes.LAVA, getX(), getY() + 1.5, getZ(), 40, 1.5, 1.2, 1.5, 0.0);
            sl.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.5, getZ(), 80, 1.5, 1.2, 1.5, 0.08);
            sl.playSound(null, here, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.0F, 0.8F);
            sl.broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    // ---------- Posición y respiraderos ----------

    private List<BlockPos> vents(ServerLevel level) {
        Optional<TempleData> data = templeData();
        if (data.isPresent()) {
            List<BlockPos> list = TempleService.positionsOf(level, data.get(), MagmaVentBlock.class);
            if (!list.isEmpty()) return list;
        }
        // Sin templo: 8 puntos alrededor del sitio de aparición
        BlockPos h = home != null ? home : blockPosition();
        List<BlockPos> list = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            list.add(h.offset((int) Math.round(Math.cos(a) * 10), -1, (int) Math.round(Math.sin(a) * 10)));
        }
        return list;
    }

    private BlockPos pickVent(ServerLevel level, @Nullable BlockPos exclude) {
        List<BlockPos> list = new ArrayList<>(vents(level));
        list.removeIf(p -> p.equals(exclude) || MagmaVentBlock.isErupting(level, p));
        if (list.isEmpty()) list = vents(level);
        return list.get(random.nextInt(list.size()));
    }

    private Vec3 ventTop() {
        BlockPos v = vent != null ? vent : blockPosition();
        return new Vec3(v.getX() + 0.5, v.getY() + 1.0, v.getZ() + 0.5);
    }

    private Vec3 center() {
        BlockPos c = templeData().map(d -> d.altar).orElse(null);
        if (c == null) c = home != null ? home : blockPosition();
        return new Vec3(c.getX() + 0.5, c.getY() + 1.0, c.getZ() + 0.5);
    }

    private double flyHeight() {
        return center().y + 12.0;
    }

    private double flyRadius() {
        if (!(level() instanceof ServerLevel level)) return 10.0;
        Vec3 c = center();
        double sum = 0;
        List<BlockPos> list = vents(level);
        for (BlockPos p : list) sum += Math.sqrt(p.distToCenterSqr(c.x, p.getY() + 0.5, c.z));
        return Math.max(6.0, sum / list.size());
    }

    private void moveToward(Vec3 target, double step) {
        Vec3 d = target.subtract(position());
        double len = d.length();
        if (len > step) d = d.scale(step / len);
        setPos(getX() + d.x, getY() + d.y, getZ() + d.z);
        if (d.horizontalDistanceSqr() > 0.0025) face(d.x, d.z);
    }

    private void faceNearestPlayer() {
        var p = level().getNearestPlayer(this, 48.0);
        if (p != null) face(p.getX() - getX(), p.getZ() - getZ());
    }

    /** Gira poco a poco hacia la dirección indicada (sin giros bruscos). */
    private void face(double dx, double dz) {
        float target = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float yaw = Mth.approachDegrees(getYRot(), target, TURN_SPEED);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private static double smooth(double x) {
        return x * x * (3 - 2 * x);
    }

    private static double backOut(double x) {
        double s = 1.4, c = s + 1;
        return 1 + c * Math.pow(x - 1, 3) + s * Math.pow(x - 1, 2);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }
    }

    // ---------- Sonidos ----------

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDER_DRAGON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDER_DRAGON_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 3.0F;
    }

    // ---------- Guardado ----------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Enraged", enraged);
        if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
        if (vent != null) tag.put("Vent", NbtUtils.writeBlockPos(vent));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        enraged = tag.getBoolean("Enraged");
        if (tag.contains("Home")) home = NbtUtils.readBlockPos(tag.getCompound("Home"));
        if (tag.contains("Vent")) vent = NbtUtils.readBlockPos(tag.getCompound("Vent"));
        setPhase(BURROWED);
    }

    // ---------- Animaciones ----------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 8, state -> {
            if (isDeadOrDying()) return state.setAndContinue(ANIM_DEATH);
            return switch (phase()) {
                case INTRO -> state.setAndContinue(ANIM_INTRO);
                case STUNNED -> state.setAndContinue(ANIM_STUNNED);
                case WAKING -> state.setAndContinue(ANIM_WAKE);
                case TAKEOFF -> state.setAndContinue(ANIM_TAKEOFF);
                case FLYING -> state.setAndContinue(ANIM_FLY);
                case DIVING, PLUNGE -> state.setAndContinue(ANIM_DIVE);
                case SUBMERGING -> state.setAndContinue(ANIM_SUBMERGE);
                default -> state.setAndContinue(ANIM_HEAD);
            };
        }).triggerableAnim("emerge", ANIM_EMERGE).triggerableAnim("breath", ANIM_BREATH).triggerableAnim("intro", ANIM_INTRO));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
