package com.sugar.entity;

import com.sugar.block.MagmaVentBlock;
import com.sugar.boss.SugarBoss;
import com.sugar.registry.ModEntities;
import com.sugar.temple.TempleData;
import com.sugar.temple.TempleService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Gran Bestia de Ceniza.
 *
 * ACECHO: camina hacia los jugadores y da pisotones si se acercan. Cada pocos segundos elige:
 *  - INHALAR (3 s): abre la boca y aspira. Las Bombas de Ceniza cercanas acaban dentro; si traga
 *    suficientes (más cuantos más jugadores), queda ATURDIDA 8 s y recibe todo el daño.
 *    Si no, escupe FUEGO en abanico.
 *  - RODAR: se hace una bola y embiste rebotando contra las paredes, empujando a la gente hacia la lava.
 *  - RUGIR: invoca Crías de Ceniza desde las grietas.
 * Fuera de la aturdida su coraza solo deja pasar el 20% del daño, y rodando es invulnerable.
 * Por debajo del 40%: las grietas entran en erupción y rueda más a menudo.
 */
public class AshBeast extends SugarBoss implements GeoEntity {
    public static final int STALK = 0, INHALE = 1, FIRE = 2, STUNNED = 3, CURL = 4, ROLLING = 5, UNCURL = 6, ROAR = 7, STOMP = 8;
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(AshBeast.class, EntityDataSerializers.INT);

    private static final String P = "animation.ash_beast.";
    private static final RawAnimation A_IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation A_WALK = RawAnimation.begin().thenLoop(P + "walk");
    private static final RawAnimation A_INHALE = RawAnimation.begin().thenPlayAndHold(P + "inhale");
    private static final RawAnimation A_FIRE = RawAnimation.begin().thenLoop(P + "breath");
    private static final RawAnimation A_STUN = RawAnimation.begin().thenPlay(P + "stun_hit").thenLoop(P + "stunned");
    private static final RawAnimation A_CURL = RawAnimation.begin().thenPlay(P + "roll_start").thenLoop(P + "roll");
    private static final RawAnimation A_ROLL = RawAnimation.begin().thenLoop(P + "roll");
    private static final RawAnimation A_UNCURL = RawAnimation.begin().thenPlay(P + "roll_end");
    private static final RawAnimation A_ROAR = RawAnimation.begin().thenPlay(P + "roar");
    private static final RawAnimation A_STOMP = RawAnimation.begin().thenPlay(P + "stomp");
    private static final RawAnimation A_DEATH = RawAnimation.begin().thenPlayAndHold(P + "death");

    // Posición de la boca medida en el modelo (bloques): al inhalar y al escupir fuego
    private static final double MOUTH_UP = 2.9, MOUTH_FWD = 3.9, FIRE_UP = 1.0, FIRE_FWD = 3.5;
    private static final int INHALE_TIME = 60, FIRE_TIME = 36, STUN_TIME = 160, CURL_TIME = 12, ROLL_TIME = 120,
            UNCURL_TIME = 26, ROAR_TIME = 40, STOMP_TIME = 20;
    private static final int MAX_HATCHLINGS = 12;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Map<UUID, Integer> rollHitCooldown = new HashMap<>();
    private int timer;
    private int stompCooldown;
    private int bombHits;
    private int bounces;
    private int hintCooldown;
    private boolean enraged;
    private Vec3 rollDir = Vec3.ZERO;

    public AshBeast(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 800;
        setMaxUpStep(1.1F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBossAttributes()
                .add(Attributes.MAX_HEALTH, 320.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24);
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.WHITE;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE, STALK);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    private void setPhase(int p) {
        entityData.set(PHASE, p);
        timer = 0;
        if (p == INHALE) bombHits = 0;
        if (p == ROLLING) { bounces = 0; rollHitCooldown.clear(); }
        getNavigation().stop();
    }

    /** Solo para las pruebas automáticas. */
    public void debugSetPhase(int p) {
        setPhase(p);
    }

    // ---------- Geometría ----------

    private Vec3 forward() {
        double r = Math.toRadians(getYRot());
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    public Vec3 mouthPos() {
        return position().add(0, MOUTH_UP, 0).add(forward().scale(MOUTH_FWD));
    }

    private void turnTo(double dx, double dz, float speed) {
        float target = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float yaw = Mth.approachDegrees(getYRot(), target, speed);
        setYRot(yaw); yBodyRot = yaw; yHeadRot = yaw;
    }

    private List<ServerPlayer> targets(ServerLevel level) {
        Optional<TempleData> data = templeData();
        if (data.isPresent()) return TempleService.playersInside(level, data.get()).stream().filter(p -> !p.isCreative()).toList();
        return level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(40), p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
    }

    @Nullable
    private ServerPlayer nearest(ServerLevel level) {
        ServerPlayer best = null; double bd = Double.MAX_VALUE;
        for (ServerPlayer p : targets(level)) {
            double d = p.distanceToSqr(this);
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    // ---------- Lógica ----------

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level)) return;
        timer++;
        if (stompCooldown > 0) stompCooldown--;
        if (hintCooldown > 0) hintCooldown--;
        ServerPlayer target = nearest(level);

        switch (phase()) {
            case STALK -> stalk(level, target);
            case INHALE -> inhale(level, target);
            case FIRE -> fire(level);
            case STUNNED -> {
                if (timer % 10 == 0) {
                    Vec3 h = position().add(forward().scale(3.0)).add(0, 1.4, 0);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, h.x, h.y, h.z, 3, 0.3, 0.2, 0.3, 0.02);
                }
                for (int k = 0; k < 3; k++) {
                    double a = timer * 0.35 + k * Math.PI * 2 / 3;
                    Vec3 h = position().add(forward().scale(3.0));
                    level.sendParticles(ParticleTypes.CRIT, h.x + Math.cos(a), h.y + 2.0, h.z + Math.sin(a), 1, 0, 0, 0, 0);
                }
                if (timer >= STUN_TIME) setPhase(STALK);
            }
            case CURL -> {
                if (target != null) turnTo(target.getX() - getX(), target.getZ() - getZ(), 12);
                if (timer == 1) level.playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.HOSTILE, 2.0F, 0.5F);
                if (timer >= CURL_TIME) {
                    rollDir = target != null ? new Vec3(target.getX() - getX(), 0, target.getZ() - getZ()).normalize() : forward();
                    setPhase(ROLLING);
                }
            }
            case ROLLING -> roll(level);
            case UNCURL -> { if (timer >= UNCURL_TIME) setPhase(STALK); }
            case ROAR -> {
                if (timer == 2) level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 4.0F, 0.5F);
                if (timer == 21) { shockwave(level, 4.0, 3.0F, 0.4); summonHatchlings(level); }
                if (timer >= ROAR_TIME) setPhase(STALK);
            }
            case STOMP -> {
                if (timer == 7) shockwave(level, 5.0, 6.0F, 0.7);
                if (timer >= STOMP_TIME) setPhase(STALK);
            }
            default -> setPhase(STALK);
        }
        if (!enraged && getHealth() / getMaxHealth() < 0.4F) enrage(level);
    }

    private void stalk(ServerLevel level, @Nullable ServerPlayer target) {
        if (target == null) return;
        double dist = distanceTo(target);
        getLookControl().setLookAt(target, 20, 20);
        if (dist > 4.0) {
            if (timer % 10 == 1) getNavigation().moveTo(target, 1.0);
        } else {
            getNavigation().stop();
            turnTo(target.getX() - getX(), target.getZ() - getZ(), 8);
            if (stompCooldown <= 0) { stompCooldown = 60; setPhase(STOMP); return; }
        }
        int decide = enraged ? 80 : 120;
        if (timer >= decide) {
            double r = random.nextDouble();
            double pRoll = enraged ? 0.5 : 0.32;
            int alive = hatchlingCount(level);
            if (r < pRoll) setPhase(CURL);
            else if (r < pRoll + 0.18 && alive < MAX_HATCHLINGS) setPhase(ROAR);
            else setPhase(INHALE);
        }
    }

    private void inhale(ServerLevel level, @Nullable ServerPlayer target) {
        if (target != null && timer < 20) turnTo(target.getX() - getX(), target.getZ() - getZ(), 6);
        if (timer == 1) {
            level.playSound(null, blockPosition(), SoundEvents.GHAST_WARN, SoundSource.HOSTILE, 3.0F, 0.4F);
            templeData().ifPresent(d -> TempleService.actionbar(level, d,
                    Component.literal("¡Está inhalando! ¡Lanzadle bombas a la boca!").withStyle(ChatFormatting.YELLOW)));
        }
        Vec3 mouth = mouthPos();
        Vec3 fwd = forward();
        if (timer > 8) {
            // Aspira: arrastra bombas y empuja un poco a los jugadores hacia la boca
            for (AshBomb bomb : level.getEntitiesOfClass(AshBomb.class, getBoundingBox().inflate(10), b -> !b.isSwallowed())) {
                Vec3 to = bomb.position().subtract(mouth);
                double d = to.length();
                if (d < 1.8) { bomb.swallow(this); continue; }
                if (d < 10 && to.normalize().dot(fwd) > 0.35) bomb.pull(mouth, 0.16);
            }
            for (ServerPlayer p : targets(level)) {
                Vec3 to = p.position().subtract(mouth);
                double d = to.length();
                if (d > 1.5 && d < 10 && to.normalize().dot(fwd) > 0.5) {
                    Vec3 pull = to.normalize().scale(-0.045);
                    p.push(pull.x, 0, pull.z);
                    p.hurtMarked = true;
                }
            }
            // Partículas de ceniza entrando en la boca
            for (int i = 0; i < 4; i++) {
                double a = (random.nextDouble() - 0.5) * 1.6;
                Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
                Vec3 from = mouth.add(fwd.scale(4 + random.nextDouble() * 5)).add(side.scale(a * 4)).add(0, random.nextDouble() * 2 - 1.5, 0);
                Vec3 v = mouth.subtract(from).scale(0.12);
                level.sendParticles(ParticleTypes.WHITE_ASH, from.x, from.y, from.z, 0, v.x, v.y, v.z, 1.0);
                level.sendParticles(ParticleTypes.SMOKE, from.x, from.y, from.z, 0, v.x, v.y, v.z, 1.0);
            }
            if (timer % 12 == 0) level.playSound(null, blockPosition(), SoundEvents.BLAZE_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.4F);
        }
        if (timer >= INHALE_TIME) setPhase(FIRE);
    }

    /** Lo llama la bomba cuando explota dentro de la boca. */
    public void onBombSwallowed(AshBomb bomb) {
        if (!(level() instanceof ServerLevel level) || phase() != INHALE && phase() != FIRE) return;
        bombHits++;
        int needed = 1 + scaledPlayers() / 12;
        if (bombHits >= needed) {
            setPhase(STUNNED);
            level.playSound(null, blockPosition(), SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 4.0F, 0.6F);
            templeData().ifPresent(d -> TempleService.actionbar(level, d,
                    Component.literal("¡La Bestia está aturdida! ¡Atacad!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        } else {
            templeData().ifPresent(d -> TempleService.actionbar(level, d,
                    Component.literal("Bombas tragadas: " + bombHits + "/" + needed).withStyle(ChatFormatting.GOLD)));
        }
    }

    private void fire(ServerLevel level) {
        Vec3 fwd = forward();
        double sway = Math.toRadians(12) * Math.sin(Math.PI * 2 * timer / 32.0);   // la cabeza oscila al escupir
        Vec3 dir = new Vec3(fwd.x * Math.cos(sway) - fwd.z * Math.sin(sway), -0.08, fwd.x * Math.sin(sway) + fwd.z * Math.cos(sway)).normalize();
        Vec3 m = position().add(0, FIRE_UP, 0).add(fwd.scale(FIRE_FWD));
        for (int i = 0; i < 7; i++) {
            Vec3 v = dir.add((random.nextDouble() - 0.5) * 0.35, random.nextDouble() * 0.12, (random.nextDouble() - 0.5) * 0.35);
            level.sendParticles(ParticleTypes.FLAME, m.x, m.y, m.z, 0, v.x, v.y, v.z, 0.6 + random.nextDouble() * 0.3);
        }
        if (timer % 2 == 0) level.sendParticles(ParticleTypes.LARGE_SMOKE, m.x, m.y, m.z, 0, dir.x, 0.15, dir.z, 0.35);
        if (timer % 8 == 0) level.playSound(null, blockPosition(), SoundEvents.FIRE_AMBIENT, SoundSource.HOSTILE, 3.0F, 0.5F);
        if (timer == 1) level.playSound(null, blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 3.0F, 0.4F);
        if (timer % 4 == 0) {
            for (ServerPlayer p : targets(level)) {
                Vec3 to = p.position().add(0, 0.8, 0).subtract(m);
                double d = to.length();
                if (d > (enraged ? 12 : 10) || d < 0.01) continue;
                if (Math.acos(Math.max(-1, Math.min(1, to.normalize().dot(fwd)))) > Math.toRadians(32)) continue;
                HitResult hit = level.clip(new ClipContext(m, p.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                if (hit.getType() != HitResult.Type.MISS) continue;
                p.hurt(damageSources().mobAttack(this), scaledDamage(2.5F));
                p.setSecondsOnFire(4);
            }
        }
        if (timer >= (enraged ? FIRE_TIME + 16 : FIRE_TIME)) setPhase(STALK);
    }

    private void roll(ServerLevel level) {
        double speed = enraged ? 0.85 : 0.7;
        Vec3 v = getDeltaMovement();
        setDeltaMovement(rollDir.x * speed, v.y, rollDir.z * speed);
        turnTo(rollDir.x, rollDir.z, 30);

        if (timer % 2 == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BASALT.defaultBlockState()),
                    getX(), getY() + 0.2, getZ(), 6, 1.0, 0.1, 1.0, 0.1);
        }
        if (timer % 6 == 0) level.playSound(null, blockPosition(), SoundEvents.BASALT_STEP, SoundSource.HOSTILE, 2.0F, 0.5F);

        // Arrolla a los jugadores
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(0.6), q -> !q.isSpectator() && !q.isCreative())) {
            Integer last = rollHitCooldown.get(p.getUUID());
            if (last != null && tickCount - last < 15) continue;
            rollHitCooldown.put(p.getUUID(), tickCount);
            p.hurt(damageSources().mobAttack(this), scaledDamage(10.0F));
            Vec3 away = p.position().subtract(position()).multiply(1, 0, 1).normalize();
            p.push(away.x * 1.6 + rollDir.x * 0.6, 0.55, away.z * 1.6 + rollDir.z * 0.6);
            p.hurtMarked = true;
        }
        // Rebota contra las paredes
        if (horizontalCollision && timer > 3) {
            bounces++;
            level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5F, 0.6F);
            level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1.0, getZ(), 2, 0.5, 0.5, 0.5, 0.0);
            ServerPlayer next = randomTarget(level);
            Vec3 nd = next != null ? new Vec3(next.getX() - getX(), 0, next.getZ() - getZ()) : rollDir.scale(-1);
            double jitter = (random.nextDouble() - 0.5) * 0.6;
            nd = nd.normalize();
            rollDir = new Vec3(nd.x * Math.cos(jitter) - nd.z * Math.sin(jitter), 0, nd.x * Math.sin(jitter) + nd.z * Math.cos(jitter)).normalize();
            setDeltaMovement(rollDir.scale(0.3).add(0, 0.25, 0));
        }
        if (bounces > (enraged ? 3 : 2) || timer >= ROLL_TIME) {
            setDeltaMovement(getDeltaMovement().multiply(0.2, 1, 0.2));
            setPhase(UNCURL);
        }
    }

    @Nullable
    private ServerPlayer randomTarget(ServerLevel level) {
        List<ServerPlayer> list = targets(level);
        return list.isEmpty() ? null : list.get(random.nextInt(list.size()));
    }

    private void shockwave(ServerLevel level, double radius, float damage, double lift) {
        level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0F, 0.5F);
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BASALT.defaultBlockState()),
                    getX() + Math.cos(a) * radius * 0.7, getY() + 0.1, getZ() + Math.sin(a) * radius * 0.7, 4, 0.3, 0.05, 0.3, 0.15);
        }
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(radius, 1, radius), q -> !q.isSpectator() && !q.isCreative())) {
            if (!p.onGround()) continue;           // saltar lo esquiva
            p.hurt(damageSources().mobAttack(this), scaledDamage(damage));
            Vec3 away = p.position().subtract(position()).multiply(1, 0, 1).normalize();
            p.push(away.x * 0.8, lift, away.z * 0.8);
            p.hurtMarked = true;
        }
    }

    private int hatchlingCount(ServerLevel level) {
        return level.getEntitiesOfClass(AshHatchling.class, getBoundingBox().inflate(48)).size();
    }

    private void summonHatchlings(ServerLevel level) {
        int count = Math.min(MAX_HATCHLINGS - hatchlingCount(level), 2 + scaledPlayers() / 8);
        if (count <= 0) return;
        List<BlockPos> spots = new ArrayList<>(templeData().map(d -> TempleService.positionsOf(level, d, MagmaVentBlock.class)).orElse(List.of()));
        if (spots.isEmpty()) {
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * 2 * i / 6;
                spots.add(BlockPos.containing(getX() + Math.cos(a) * 6, getY() - 1, getZ() + Math.sin(a) * 6));
            }
        }
        Collections.shuffle(spots);
        for (int i = 0; i < count; i++) {
            BlockPos s = spots.get(i % spots.size()).above();
            AshHatchling h = ModEntities.ASH_HATCHLING.get().create(level);
            if (h == null) continue;
            h.moveTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, random.nextFloat() * 360, 0);
            h.finalizeSpawn(level, level.getCurrentDifficultyAt(s), MobSpawnType.MOB_SUMMONED, null, null);
            level.addFreshEntity(h);
            level.sendParticles(ParticleTypes.LAVA, s.getX() + 0.5, s.getY() + 0.2, s.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, s.getX() + 0.5, s.getY() + 0.5, s.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
        }
    }

    private void enrage(ServerLevel level) {
        enraged = true;
        level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 5.0F, 0.4F);
        templeData().ifPresent(d -> {
            List<BlockPos> cracks = new ArrayList<>(TempleService.positionsOf(level, d, MagmaVentBlock.class));
            Collections.shuffle(cracks);
            for (int i = 0; i < cracks.size() / 2; i++) MagmaVentBlock.setErupting(level, cracks.get(i), true);
            TempleService.actionbar(level, d, Component.literal("¡El suelo se agrieta!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        });
    }

    // ---------- Daño ----------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        int p = phase();
        if (p == ROLLING || p == CURL) {
            if (source.getEntity() instanceof ServerPlayer) level().playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.8F, 1.4F);
            return false;
        }
        if (p == STUNNED) return super.hurt(source, amount);
        if (source.getEntity() instanceof ServerPlayer player && hintCooldown == 0 && !source.is(DamageTypeTags.IS_EXPLOSION)) {
            hintCooldown = 60;
            player.displayClientMessage(Component.literal("Su coraza es muy dura... ¡Métele una bomba en la boca cuando inhale!")
                    .withStyle(ChatFormatting.RED), true);
        }
        return super.hurt(source, amount * 0.2F);
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel level) {
            templeData().ifPresent(d -> {
                for (BlockPos c : TempleService.positionsOf(level, d, MagmaVentBlock.class)) MagmaVentBlock.setErupting(level, c, false);
            });
            for (AshHatchling h : level.getEntitiesOfClass(AshHatchling.class, new AABB(blockPosition()).inflate(64))) h.crumble();
        }
        super.die(source);
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (!(level() instanceof ServerLevel sl)) return;
        BlockPos here = blockPosition();
        if (deathTime == 1) sl.playSound(null, here, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 5.0F, 0.35F);
        if (deathTime <= 32 && deathTime % 4 == 0) {
            Vec3 p = position().add((random.nextDouble() - 0.5) * 3, 1 + random.nextDouble() * 2, (random.nextDouble() - 0.5) * 3);
            sl.sendParticles(ParticleTypes.LAVA, p.x, p.y, p.z, 5, 0.4, 0.4, 0.4, 0.0);
            sl.sendParticles(ParticleTypes.WHITE_ASH, p.x, p.y, p.z, 30, 0.6, 0.6, 0.6, 0.02);
        }
        if (deathTime == 55) {
            sl.playSound(null, here, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0F, 0.5F);
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLACKSTONE.defaultBlockState()), getX(), getY() + 0.5, getZ(), 80, 2.0, 0.4, 2.0, 0.15);
        }
        if (deathTime >= 60 && deathTime % 2 == 0) {
            for (int i = 0; i < 3; i++) {
                Vec3 p = position().add((random.nextDouble() - 0.5) * 4, random.nextDouble() * 2, (random.nextDouble() - 0.5) * 4);
                sl.sendParticles(ParticleTypes.ASH, p.x, p.y, p.z, 20, 0.5, 0.5, 0.5, 0.02);
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BASALT.defaultBlockState()), p.x, p.y, p.z, 6, 0.4, 0.3, 0.4, 0.05);
            }
        }
        if (deathTime >= 80) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 60, 2.0, 1.0, 2.0, 0.05);
            sl.playSound(null, here, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.0F, 0.7F);
            sl.broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 3.0F;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F;
    }

    // ---------- Guardado ----------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Enraged", enraged);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        enraged = tag.getBoolean("Enraged");
        setPhase(STALK);
    }

    // ---------- Animaciones ----------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, state -> {
            if (isDeadOrDying()) return state.setAndContinue(A_DEATH);
            return switch (phase()) {
                case INHALE -> state.setAndContinue(A_INHALE);
                case FIRE -> state.setAndContinue(A_FIRE);
                case STUNNED -> state.setAndContinue(A_STUN);
                case CURL -> state.setAndContinue(A_CURL);
                case ROLLING -> state.setAndContinue(A_ROLL);
                case UNCURL -> state.setAndContinue(A_UNCURL);
                case ROAR -> state.setAndContinue(A_ROAR);
                case STOMP -> state.setAndContinue(A_STOMP);
                default -> state.setAndContinue(state.isMoving() ? A_WALK : A_IDLE);
            };
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
