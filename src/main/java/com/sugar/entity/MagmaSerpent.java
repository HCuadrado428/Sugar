package com.sugar.entity;

import com.sugar.block.MagmaVentBlock;
import com.sugar.boss.SugarBoss;
import com.sugar.registry.ModEntities;
import com.sugar.registry.ModItems;
import com.sugar.temple.TempleData;
import com.sugar.temple.TempleService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Sierpe de Magma.
 *
 * Ciclo:
 *  ENTERRADA → (elige respiradero) EMERGIENDO → ASOMADA: solo la aturde el Martillo de Obsidiana
 *  (más golpes cuantos más jugadores). Si nadie la aturde, escupe magma y vuelve a hundirse.
 *  ATURDIDA: recibe todo el daño durante 8 s → DESPEGUE → VUELO en círculos lanzando magma
 *  → PICADO hacia un respiradero → ENTERRADA.
 *  Con muchos jugadores asoman además cabezas señuelo en otros respiraderos.
 *  Por debajo del 40% de vida: la mitad de los respiraderos entran en erupción y caen más rocas.
 */
public class MagmaSerpent extends SugarBoss implements GeoEntity {
    public static final int BURROWED = 0, EMERGING = 1, HEAD = 2, SUBMERGING = 3,
            STUNNED = 4, TAKEOFF = 5, FLYING = 6, DIVING = 7;

    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(MagmaSerpent.class, EntityDataSerializers.INT);

    private static final RawAnimation ANIM_HEAD = RawAnimation.begin().thenLoop("animation.magma_serpent.head_idle");
    private static final RawAnimation ANIM_STUNNED = RawAnimation.begin().thenLoop("animation.magma_serpent.stunned");
    private static final RawAnimation ANIM_FLY = RawAnimation.begin().thenLoop("animation.magma_serpent.fly");
    private static final RawAnimation ANIM_EMERGE = RawAnimation.begin().thenPlay("animation.magma_serpent.emerge");
    private static final RawAnimation ANIM_SPIT = RawAnimation.begin().thenPlay("animation.magma_serpent.spit");
    private static final RawAnimation ANIM_DEATH = RawAnimation.begin().thenPlayAndHold("animation.magma_serpent.death");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int timer;
    private int hammerHits;
    private int hintCooldown;
    private boolean enraged;
    @Nullable private BlockPos home;
    @Nullable private BlockPos vent;
    private double flyAngle;

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

    // ---------- Lógica ----------

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level)) return;
        if (home == null) home = blockPosition();
        setDeltaMovement(Vec3.ZERO);
        timer++;
        if (hintCooldown > 0) hintCooldown--;

        switch (phase()) {
            case BURROWED -> {
                if (vent != null) setPos(vent.getX() + 0.5, vent.getY() - 5.0, vent.getZ() + 0.5);
                if (timer >= 50) emerge(level);
            }
            case EMERGING -> {
                moveToward(ventTop(), 0.3);
                if (timer >= 20) setPhase(HEAD);
            }
            case HEAD -> {
                faceNearestPlayer();
                if (timer == 13 || timer == 43) triggerAnim("main", "spit");   // coge aire y lanza la cabeza
                if (timer == 25 || timer == 55) spit(level);
                if (timer >= 90) setPhase(SUBMERGING);
            }
            case SUBMERGING -> {
                moveToward(ventTop().add(0, -6.0, 0), 0.35);
                if (timer >= 20) setPhase(BURROWED);
            }
            case STUNNED -> {
                if (timer % 10 == 0) {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 4.5, getZ(), 6, 0.6, 0.3, 0.6, 0.01);
                }
                if (timer >= 160) setPhase(TAKEOFF);
            }
            case TAKEOFF -> {
                moveToward(new Vec3(getX(), flyHeight(), getZ()), 0.4);
                if (timer >= 40) {
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
                if (timer % (enraged ? 7 : 12) == 0) dropBolt(level);
                if (timer >= 220) {
                    vent = pickVent(level, null);
                    setPhase(DIVING);
                }
            }
            case DIVING -> {
                Vec3 target = ventTop();
                moveToward(target, 0.8);
                if (position().distanceTo(target) < 1.0 || timer > 120) setPhase(SUBMERGING);
            }
            default -> setPhase(BURROWED);
        }

        if (phase() != BURROWED && tickCount % (enraged ? 40 : 80) == 0) rockRain(level);
        if (!enraged && getHealth() / getMaxHealth() < 0.4F) enrage(level);
    }

    private void emerge(ServerLevel level) {
        vent = pickVent(level, null);
        setPos(vent.getX() + 0.5, vent.getY() - 5.0, vent.getZ() + 0.5);
        setPhase(EMERGING);
        triggerAnim("main", "emerge");
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

    private void spit(ServerLevel level) {
        List<ServerPlayer> players = new ArrayList<>(targets(level));
        if (players.isEmpty()) return;
        Collections.shuffle(players);
        Vec3 mouth = position().add(0, 4.2, 0).add(getLookAngle().scale(1.5));
        for (int i = 0; i < Math.min(3, players.size()); i++) {
            MagmaBolt.shoot(level, this, mouth, players.get(i).getEyePosition(), scaledDamage(6.0F));
        }
        level.playSound(null, blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.0F, 0.6F);
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
        if (phase == STUNNED || phase == TAKEOFF || phase == FLYING) {
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
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LAVA, getX(), getY() + 2.5, getZ(), 4, 1.0, 1.5, 1.0, 0.0);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2.5, getZ(), 6, 1.0, 1.5, 1.0, 0.02);
            if (deathTime >= 60) {
                sl.broadcastEntityEvent(this, (byte) 60);
                remove(RemovalReason.KILLED);
            }
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

    private void face(double dx, double dz) {
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
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
        controllers.add(new AnimationController<>(this, "main", 6, state -> {
            if (isDeadOrDying()) return state.setAndContinue(ANIM_DEATH);
            return switch (phase()) {
                case STUNNED -> state.setAndContinue(ANIM_STUNNED);
                case TAKEOFF, FLYING, DIVING -> state.setAndContinue(ANIM_FLY);
                default -> state.setAndContinue(ANIM_HEAD);
            };
        }).triggerableAnim("emerge", ANIM_EMERGE).triggerableAnim("spit", ANIM_SPIT));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
