package com.sugar.entity;

import com.sugar.boss.SugarBoss;
import com.sugar.registry.ModEntities;
import com.sugar.temple.TempleData;
import com.sugar.temple.TempleService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * El Tamborilero: un torso fantasma sin cabeza, invisible, que flota sobre un tambor gigante,
 * con un corazón de sculk dentro de las costillas, y dos manos de piedra enormes.
 *
 * OCULTO: solo se ve con la Lente del Eco en la mano y apenas le afecta el daño (25%).
 *   Las manos atacan: golpe con sombra de aviso, redoble que hace botar a todo el que esté en el suelo
 *   (saltad a tiempo), agarre y lanzamiento, barrido a ras de suelo y, con poca vida, palmada.
 * Golpear una mano la va aturdiendo; cuando cae se queda unos segundos en el suelo.
 * Si las DOS manos están en el suelo a la vez, el torso se desploma, las costillas se abren y el corazón recibe todo el daño.
 */
public class EchoDrummer extends SugarBoss implements GeoEntity {
    public static final int INTRO = 0, HIDDEN = 1, EXPOSE = 2, EXPOSED = 3, RETREAT = 4, LAUGH = 5;
    public static final int ARENA_R = 15;
    private static final int INTRO_TOTAL = 150, INTRO_REVEAL = 70, EXPOSE_TIME = 16, EXPOSED_TIME = 160, RETREAT_TIME = 26, LAUGH_TIME = 32;
    private static final double HOVER_H = 6.0, EXPOSED_H = 0.6;
    private static final int[] INTRO_DRUMS = {4, 24, 40, 52, 61, 68, 73, 77, 80, 83, 85, 87, 89};

    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(EchoDrummer.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> VISIBLE = SynchedEntityData.defineId(EchoDrummer.class, EntityDataSerializers.BOOLEAN);

    private static final String P = "animation.echo_drummer.";
    private static final RawAnimation A_IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation A_INTRO = RawAnimation.begin().thenPlayAndHold(P + "intro");
    private static final RawAnimation A_EXPOSE = RawAnimation.begin().thenPlay(P + "expose").thenLoop(P + "exposed");
    private static final RawAnimation A_RETREAT = RawAnimation.begin().thenPlayAndHold(P + "retreat");
    private static final RawAnimation A_LAUGH = RawAnimation.begin().thenPlay(P + "laugh").thenLoop(P + "idle");
    private static final RawAnimation A_DEATH = RawAnimation.begin().thenPlayAndHold(P + "death");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private Vec3 home;
    @Nullable private UUID leftId, rightId;
    private int leftMissing, rightMissing;
    private int timer;
    private int cooldown = 60;
    private int rollBeats, rollTimer;
    private boolean rollLeft;
    private int laughCooldown = 400;
    private boolean enraged;
    @Nullable private UUID lookId;

    public EchoDrummer(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
        this.xpReward = 900;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBossAttributes()
                .add(Attributes.MAX_HEALTH, 300.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.PURPLE;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE, HIDDEN);
        entityData.define(VISIBLE, false);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    private void setPhase(int p) {
        entityData.set(PHASE, p);
        timer = 0;
    }

    /** Se ve sin lente: presentación, torso caído o muerte. */
    public boolean isRevealed() {
        return entityData.get(VISIBLE) || isDeadOrDying();
    }

    private void setVisible(boolean v) {
        entityData.set(VISIBLE, v);
    }

    public boolean isExposed() {
        int p = phase();
        return p == EXPOSE || p == EXPOSED;
    }

    // ---------- Geometría (la usan las manos) ----------

    public Vec3 center() {
        return home != null ? home : position();
    }

    public double floorY() {
        return center().y;
    }

    private Vec3 fwd() {
        double r = Math.toRadians(getYRot());
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    private Vec3 right() {
        Vec3 f = fwd();
        return new Vec3(-f.z, 0, f.x);
    }

    private int side(boolean left) {
        return left ? -1 : 1;
    }

    public Vec3 handHome(boolean left) {
        return center().add(right().scale(side(left) * 6.5)).add(fwd().scale(2.0)).add(0, 3.0, 0);
    }

    /** Borde del tambor por donde trepa cada mano en la presentación. */
    public Vec3 handEdge(boolean left) {
        return center().add(right().scale(side(left) * (ARENA_R + 1.5))).add(0, -0.5, 0);
    }

    /** Fondo del abismo donde esperan las manos antes de aparecer. */
    public Vec3 handPit(boolean left) {
        return handEdge(left).add(0, -10, 0);
    }

    public boolean ownsHand(DrummerHand h) {
        return h.getUUID().equals(leftId) || h.getUUID().equals(rightId);
    }

    // ---------- Manos ----------

    @Nullable
    public DrummerHand hand(boolean left) {
        UUID id = left ? leftId : rightId;
        if (id == null || !(level() instanceof ServerLevel sl)) return null;
        Entity e = sl.getEntity(id);
        return e instanceof DrummerHand h && h.isAlive() ? h : null;
    }

    private void ensureHands(ServerLevel level) {
        for (boolean left : new boolean[]{true, false}) {
            if (hand(left) != null) {
                if (left) leftMissing = 0; else rightMissing = 0;
                continue;
            }
            UUID id = left ? leftId : rightId;
            int missing = left ? ++leftMissing : ++rightMissing;
            if (id != null && missing < 40) continue;          // al cargar el mundo tardan un poco en aparecer
            spawnHand(level, left, inIntro());
        }
    }

    private void spawnHand(ServerLevel level, boolean left, boolean waiting) {
        DrummerHand h = ModEntities.DRUMMER_HAND.get().create(level);
        if (h == null) return;
        Vec3 at = waiting ? handPit(left) : handHome(left);
        h.moveTo(at.x, at.y, at.z, getYRot(), 0);
        h.setup(this, left);
        h.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.MOB_SUMMONED, null, null);
        if (waiting) h.orderWait();
        level.addFreshEntity(h);
        if (left) { leftId = h.getUUID(); leftMissing = 0; } else { rightId = h.getUUID(); rightMissing = 0; }
        if (!waiting) level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y + 0.5, at.z, 20, 1, 0.4, 1, 0.02);
    }

    /** Lo llama una mano al caer aturdida. */
    public void onHandDown(DrummerHand h) {
        if (!(level() instanceof ServerLevel level) || isDeadOrDying()) return;
        DrummerHand other = hand(!h.isLeft());
        level.playSound(null, h.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 2.0F, 0.5F);
        if (other != null && other.isDown() && phase() != EXPOSE && phase() != EXPOSED) {
            setPhase(EXPOSE);
            rollBeats = 0;
            setVisible(true);
            level.playSound(null, blockPosition(), SoundEvents.ELDER_GUARDIAN_HURT, SoundSource.HOSTILE, 4.0F, 0.4F);
            templeData().ifPresent(d -> TempleService.actionbar(level, d,
                    Component.literal("¡Su corazón ha quedado al descubierto! ¡Atacad ahora!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        } else {
            templeData().ifPresent(d -> TempleService.actionbar(level, d,
                    Component.literal("¡Una mano ha caído! Tirad la otra antes de que se levante.").withStyle(ChatFormatting.YELLOW)));
        }
    }

    /** El tambor retumba: todo el que esté apoyado en el suelo cerca sale botando. */
    public void drumBoom(ServerLevel level, Vec3 at, double radius, double lift) {
        level.playSound(null, BlockPos.containing(at), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.HOSTILE, 4.0F, 0.5F);
        for (ServerPlayer p : players(level)) {
            if (!p.onGround() || p.position().distanceTo(at) > radius || Math.abs(p.getY() - floorY()) > 1.5) continue;
            p.push((random.nextDouble() - 0.5) * 0.4, lift, (random.nextDouble() - 0.5) * 0.4);
            p.hurtMarked = true;
        }
    }

    // ---------- Presentación ----------

    /** Oscuridad y tambores que aceleran; las manos trepan del abismo y golpean; el torso sale, prende el fuego del cuello, se ríe y se desvanece. */
    @Override
    public void beginIntro() {
        if (home == null) home = position();
        setIntro(true);
        setPhase(INTRO);
        setVisible(false);
        Vec3 c = center();
        setPos(c.x, c.y - 10, c.z);
        if (level() instanceof ServerLevel level) {
            for (boolean left : new boolean[]{true, false}) {
                DrummerHand h = hand(left);
                if (h != null) h.discard();
                spawnHand(level, left, true);
            }
        }
    }

    private void introTick(ServerLevel level, @Nullable ServerPlayer look) {
        introProgress = timer / (float) INTRO_TOTAL;
        Vec3 c = center();
        if (timer == 1) {
            for (ServerPlayer p : players(level)) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 130, 0, false, false));
            level.playSound(null, BlockPos.containing(c), SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE, 4.0F, 0.5F);
        }
        for (int t : INTRO_DRUMS) {
            if (timer == t) {
                float k = (timer - 4) / 85.0F;
                level.playSound(null, BlockPos.containing(c), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.HOSTILE, 3.0F + k * 2, 0.5F + k * 0.2F);
                if (timer > 50) drumBoom(level, c, ARENA_R + 2, 0.12);
            }
        }
        if (timer == 40) for (boolean left : new boolean[]{true, false}) { DrummerHand h = hand(left); if (h != null) h.orderRise(); }
        if (timer == 72) for (boolean left : new boolean[]{true, false}) { DrummerHand h = hand(left); if (h != null && h.isFree()) h.orderBeat(); }
        if (timer < INTRO_REVEAL) {
            setPos(c.x, c.y - 10, c.z);
        } else {
            if (timer == INTRO_REVEAL) {
                setVisible(true);
                level.playSound(null, BlockPos.containing(c), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 4.0F, 0.7F);
            }
            double u = Math.min(1, (timer - INTRO_REVEAL) / 22.0);
            double e = 1 - Math.pow(1 - u, 3);
            setPos(c.x, c.y - 10 + (HOVER_H + 10) * e, c.z);
            if (timer < INTRO_REVEAL + 22 && timer % 2 == 0)
                level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 1, getZ(), 4, 1.0, 1.0, 1.0, 0.03);
        }
        if (timer == 101) {
            announceBoss();
            level.playSound(null, blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 3.0F, 0.6F);
        }
        if (timer >= 108 && timer <= 136 && (timer - 108) % 7 == 0)
            level.playSound(null, blockPosition(), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 4.0F, 0.45F);
        if (look != null && timer > 100) turnTo(look, 3);
        if (timer >= INTRO_TOTAL) {
            fade(level);
            setIntro(false);
            setPhase(HIDDEN);
            cooldown = 40;
        }
    }

    private void fade(ServerLevel level) {
        setVisible(false);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 2.5, getZ(), 80, 1.2, 1.5, 1.2, 0.05);
        level.playSound(null, blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 3.0F, 0.6F);
    }

    // ---------- Lógica ----------

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level)) return;
        if (home == null) home = position();
        timer++;
        setDeltaMovement(Vec3.ZERO);
        ensureHands(level);
        ServerPlayer look = lookTarget(level);

        switch (phase()) {
            case INTRO -> introTick(level, look);
            case HIDDEN, LAUGH -> hiddenTick(level, look);
            case EXPOSE -> {
                // Se desploma hasta casi el suelo (animación "expose")
                Vec3 c = center();
                double u = Math.min(1, timer / (double) EXPOSE_TIME);
                setPos(c.x, Mth.lerp(u * u, c.y + HOVER_H, c.y + EXPOSED_H), c.z);
                holdHands(EXPOSED_TIME + 20);
                if (timer == EXPOSE_TIME) {
                    level.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2.0F, 0.3F);
                    level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), c.y + 0.3, getZ(), 30, 1.5, 0.1, 1.5, 0.05);
                }
                if (timer >= EXPOSE_TIME) setPhase(EXPOSED);
            }
            case EXPOSED -> {
                Vec3 c = center();
                setPos(c.x, c.y + EXPOSED_H, c.z);
                holdHands(EXPOSED_TIME);
                if (timer % 6 == 0) level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 2.5, getZ(), 2, 1.0, 0.8, 1.0, 0.02);
                if (timer % 30 == 0) level.playSound(null, blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 3.0F, 0.6F);
                if (timer >= (enraged ? EXPOSED_TIME - 30 : EXPOSED_TIME)) {
                    setPhase(RETREAT);
                    level.playSound(null, blockPosition(), SoundEvents.WARDEN_ANGRY, SoundSource.HOSTILE, 3.0F, 0.6F);
                    for (boolean left : new boolean[]{true, false}) { DrummerHand h = hand(left); if (h != null) h.orderRecover(); }
                }
            }
            case RETREAT -> {
                // Se sacude con rabia y vuelve arriba (animación "retreat"); al final se desvanece
                Vec3 c = center();
                double u = Math.max(0, Math.min(1, (timer - 8) / (double) (RETREAT_TIME - 8)));
                setPos(c.x, Mth.lerp(u * u * (3 - 2 * u), c.y + EXPOSED_H, c.y + HOVER_H), c.z);
                if (look != null) turnTo(look, 4);
                if (timer >= RETREAT_TIME) {
                    fade(level);
                    setPhase(LAUGH);
                    level.playSound(null, blockPosition(), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 4.0F, 0.45F);
                    cooldown = 30;
                }
            }
            default -> setPhase(HIDDEN);
        }
        if (!enraged && !inIntro() && getHealth() / getMaxHealth() < 0.4F) enrage(level);
    }

    private void holdHands(int ticks) {
        for (boolean left : new boolean[]{true, false}) { DrummerHand h = hand(left); if (h != null) h.holdDown(ticks); }
    }

    private void hiddenTick(ServerLevel level, @Nullable ServerPlayer look) {
        Vec3 c = center();
        setPos(c.x, c.y + HOVER_H + Math.sin(tickCount * 0.05) * 0.3, c.z);
        if (look != null) turnTo(look, 2.5F);
        if (phase() == LAUGH) {
            if (timer % 8 == 4) level.playSound(null, blockPosition(), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 3.0F, 0.5F);
            if (timer >= LAUGH_TIME) setPhase(HIDDEN);
        }
        // Pistas para quien no lleve lente
        if (tickCount % 15 == 0) level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 2.5, getZ(), 2, 0.8, 0.8, 0.8, 0.01);
        if (laughCooldown > 0) laughCooldown--;

        if (rollBeats > 0) { drumRoll(level); return; }
        if (cooldown > 0) { cooldown--; return; }

        DrummerHand l = hand(true), r = hand(false);
        List<DrummerHand> free = new ArrayList<>();
        if (l != null && l.isFree()) free.add(l);
        if (r != null && r.isFree()) free.add(r);
        List<ServerPlayer> players = players(level);
        if (free.isEmpty() || players.isEmpty()) return;
        ServerPlayer target = players.get(random.nextInt(players.size()));
        DrummerHand h = free.get(random.nextInt(free.size()));
        double roll = random.nextDouble();

        if (free.size() == 2 && roll < 0.22) {
            rollBeats = enraged ? 10 : 6;
            rollTimer = 0;
            rollLeft = random.nextBoolean();
            level.playSound(null, blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 3.0F, 0.5F);
            templeData().ifPresent(d -> TempleService.actionbar(level, d,
                    Component.literal("¡Redoble! Saltad cuando golpee el tambor.").withStyle(ChatFormatting.LIGHT_PURPLE)));
        } else if (enraged && free.size() == 2 && roll < 0.42) {
            Vec3 at = new Vec3(target.getX(), floorY(), target.getZ());
            l.orderClap(at, -1);
            r.orderClap(at, 1);
        } else if (roll < 0.62) {
            h.orderSlam(target);
        } else if (roll < 0.82) {
            h.orderGrab(nearestTo(players, h.position()));
        } else {
            double a = Math.atan2(target.getZ() - c.z, target.getX() - c.x);
            double dir = random.nextBoolean() ? 1 : -1;
            h.orderSweep(c, a - dir * 1.4, a + dir * 2.2);
        }
        cooldown = enraged ? 30 : 50;
        if (laughCooldown == 0 && random.nextInt(4) == 0) {
            laughCooldown = 500;
            setPhase(LAUGH);
        }
    }

    private void drumRoll(ServerLevel level) {
        rollTimer++;
        int every = enraged ? 9 : 13;
        if (rollTimer % every != 1) return;
        DrummerHand h = hand(rollLeft);
        if (h != null && h.isFree()) h.orderBeat();
        rollLeft = !rollLeft;
        rollBeats--;
        if (rollBeats == 0) cooldown = 40;
    }

    private void enrage(ServerLevel level) {
        enraged = true;
        level.playSound(null, blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 4.0F, 0.7F);
        templeData().ifPresent(d -> TempleService.actionbar(level, d,
                Component.literal("¡El ritmo se acelera!").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)));
    }

    private List<ServerPlayer> players(ServerLevel level) {
        Optional<TempleData> data = templeData();
        Vec3 c = center();
        List<ServerPlayer> list = data.isPresent() ? TempleService.playersInside(level, data.get())
                : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(40), p -> p.isAlive() && !p.isSpectator());
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : list) {
            if (p.isCreative()) continue;
            double dx = p.getX() - c.x, dz = p.getZ() - c.z;
            if (dx * dx + dz * dz < (ARENA_R + 6) * (ARENA_R + 6) && Math.abs(p.getY() - c.y) < 14) out.add(p);
        }
        return out;
    }

    private ServerPlayer nearestTo(List<ServerPlayer> list, Vec3 pos) {
        ServerPlayer best = list.get(0);
        for (ServerPlayer p : list) if (p.position().distanceToSqr(pos) < best.position().distanceToSqr(pos)) best = p;
        return best;
    }

    @Nullable
    private ServerPlayer lookTarget(ServerLevel level) {
        if (lookId != null && tickCount % 100 != 0) {
            Entity e = level.getEntity(lookId);
            if (e instanceof ServerPlayer p && p.isAlive()) return p;
        }
        List<ServerPlayer> list = players(level);
        if (list.isEmpty()) { lookId = null; return null; }
        ServerPlayer p = nearestTo(list, center());
        lookId = p.getUUID();
        return p;
    }

    private void turnTo(Entity e, float speed) {
        float target = (float) Math.toDegrees(Math.atan2(e.getZ() - getZ(), e.getX() - getX())) - 90.0F;
        float yaw = Mth.approachDegrees(getYRot(), target, speed);
        setYRot(yaw); yBodyRot = yaw; yHeadRot = yaw;
    }

    // ---------- Daño ----------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (inIntro()) return false;
        int p = phase();
        if (p == EXPOSE || p == EXPOSED || p == RETREAT) return super.hurt(source, amount);
        return super.hurt(source, amount * 0.25F);
    }

    /** Solo para las pruebas automáticas. */
    public void debugSkipIntro() {
        if (phase() == INTRO) timer = INTRO_TOTAL - 1;
    }

    @Override
    public void die(DamageSource source) {
        for (boolean left : new boolean[]{true, false}) { DrummerHand h = hand(left); if (h != null) h.orderDie(); }
        setVisible(true);
        super.die(source);
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (!(level() instanceof ServerLevel sl)) return;
        if (deathTime == 1) sl.playSound(null, blockPosition(), SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 4.0F, 0.6F);
        if (deathTime % 3 == 0) sl.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 2.5, getZ(), 6, 1.0, 1.2, 1.0, 0.04);
        if (deathTime == 36) sl.playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 3.0F, 0.5F);
        if (deathTime >= 60) {
            sl.sendParticles(ParticleTypes.SOUL, getX(), getY() + 2, getZ(), 80, 1.5, 1.5, 1.5, 0.06);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 2, getZ(), 120, 2.0, 2.0, 2.0, 0.1);
            sl.playSound(null, blockPosition(), SoundEvents.ELDER_GUARDIAN_DEATH, SoundSource.HOSTILE, 4.0F, 0.6F);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public float getVoicePitch() {
        return 0.4F;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    // ---------- Guardado ----------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) { tag.putDouble("HomeX", home.x); tag.putDouble("HomeY", home.y); tag.putDouble("HomeZ", home.z); }
        if (leftId != null) tag.putUUID("LeftHand", leftId);
        if (rightId != null) tag.putUUID("RightHand", rightId);
        tag.putBoolean("Enraged", enraged);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomeX")) home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        if (tag.hasUUID("LeftHand")) leftId = tag.getUUID("LeftHand");
        if (tag.hasUUID("RightHand")) rightId = tag.getUUID("RightHand");
        enraged = tag.getBoolean("Enraged");
        setPhase(HIDDEN);
        setVisible(false);
    }

    // ---------- Animaciones ----------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, state -> {
            if (isDeadOrDying()) return state.setAndContinue(A_DEATH);
            return switch (phase()) {
                case INTRO -> state.setAndContinue(entityData.get(VISIBLE) ? A_INTRO : A_IDLE);
                case EXPOSE, EXPOSED -> state.setAndContinue(A_EXPOSE);
                case RETREAT -> state.setAndContinue(A_RETREAT);
                case LAUGH -> state.setAndContinue(A_LAUGH);
                default -> state.setAndContinue(A_IDLE);
            };
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
