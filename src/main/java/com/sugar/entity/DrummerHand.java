package com.sugar.entity;

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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * Mano del Tamborilero. No tiene vida propia: los golpes de los jugadores la van "aturdiendo".
 * Cuando el aturdimiento llega al máximo cae al suelo unos segundos. Si las dos manos están
 * en el suelo a la vez, el torso queda expuesto.
 *
 * El Tamborilero ({@link EchoDrummer}) decide los ataques; la mano los ejecuta.
 */
public class DrummerHand extends Mob implements GeoEntity {
    public static final int HOVER = 0, WAIT = 1, RISE = 2, AIM = 3, SLAM = 4, BEAT = 5, REACH = 6, HOLD = 7, THROW = 8,
            SWEEP_AIM = 9, SWEEP = 10, CLAP_AIM = 11, CLAP = 12, STUN_FALL = 13, STUNNED = 14, RECOVER = 15, DYING = 16;
    public static final float STAGGER_MAX = 24.0F;
    private static final float RELEASE_STAGGER = 6.0F;
    private static final int STUN_TIME = 150, RECOVER_TIME = 26, RISE_TIME = 24;

    private static final EntityDataAccessor<Boolean> LEFT = SynchedEntityData.defineId(DrummerHand.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(DrummerHand.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private UUID ownerId;
    @Nullable private UUID targetId;
    @Nullable private UUID heldId;
    private int timer;
    private int orphanTicks;
    private int stunLength = STUN_TIME;
    private float stagger;
    private float heldStagger;
    private Vec3 aim = Vec3.ZERO;          // punto fijado (golpe, barrido o palmada)
    private Vec3 sweepCenter = Vec3.ZERO;
    private double sweepFrom, sweepTo;
    private int clapSide = 1;

    public DrummerHand(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
        setPersistenceRequired();
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0)
                .add(Attributes.ARMOR, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(LEFT, false);
        entityData.define(STATE, WAIT);
    }

    public void setup(EchoDrummer owner, boolean left) {
        this.ownerId = owner.getUUID();
        entityData.set(LEFT, left);
    }

    public boolean isLeft() {
        return entityData.get(LEFT);
    }

    public int state() {
        return entityData.get(STATE);
    }

    private void setState(int s) {
        entityData.set(STATE, s);
        timer = 0;
    }

    public boolean isFree() {
        return state() == HOVER;
    }

    public boolean isDown() {
        int s = state();
        return s == STUN_FALL || s == STUNNED;
    }

    public float stagger() {
        return stagger;
    }

    // ---------- Órdenes del Tamborilero ----------

    public void orderWait() { setState(WAIT); }

    public void orderRise() { setState(RISE); }

    public void orderSlam(ServerPlayer target) { targetId = target.getUUID(); aim = Vec3.ZERO; setState(AIM); }

    public void orderBeat() { setState(BEAT); }

    public void orderGrab(ServerPlayer target) { targetId = target.getUUID(); setState(REACH); }

    public void orderSweep(Vec3 center, double fromRad, double toRad) {
        sweepCenter = center; sweepFrom = fromRad; sweepTo = toRad; setState(SWEEP_AIM);
    }

    public void orderClap(Vec3 point, int side) { aim = point; clapSide = side; setState(CLAP_AIM); }

    public void orderDie() { release(); setState(DYING); }

    /** Mantiene la mano en el suelo mientras el torso está expuesto. */
    public void holdDown(int ticks) {
        if (state() == STUNNED) stunLength = Math.max(stunLength, timer + ticks);
    }

    public void orderRecover() {
        if (isDown()) { stagger = 0; setState(RECOVER); }
    }

    // ---------- Lógica ----------

    @Nullable
    private EchoDrummer owner() {
        if (ownerId == null || !(level() instanceof ServerLevel sl)) return null;
        Entity e = sl.getEntity(ownerId);
        return e instanceof EchoDrummer d && d.isAlive() ? d : null;
    }

    @Nullable
    private ServerPlayer target() {
        if (targetId == null || !(level() instanceof ServerLevel sl)) return null;
        Entity e = sl.getEntity(targetId);
        return e instanceof ServerPlayer p && p.isAlive() && !p.isSpectator() && !p.isCreative() ? p : null;
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected void customServerAiStep() {
        if (!(level() instanceof ServerLevel level)) return;
        EchoDrummer owner = owner();
        if (owner == null || !owner.ownsHand(this)) {
            if (state() == DYING && owner == null) { timer++; dyingTick(level); return; }
            if (++orphanTicks > 40) discard();     // el Tamborilero ya no está: desaparece
            return;
        }
        orphanTicks = 0;
        timer++;
        Vec3 home = owner.handHome(isLeft());
        float yaw = owner.getYRot();
        double floor = owner.floorY();

        switch (state()) {
            case WAIT -> { glide(owner.handPit(isLeft()), 2.0, 1.0); face(yaw); }
            case RISE -> riseTick(level, owner, home, yaw);
            case HOVER -> {
                double bob = Math.sin((tickCount + (isLeft() ? 20 : 0)) * 0.08) * 0.25;
                glide(home.add(0, bob, 0), 0.35, 0.15);
                face(yaw);
            }
            case AIM -> aimTick(level, owner, floor);
            case SLAM -> slamTick(level, owner, floor, home);
            case BEAT -> beatTick(level, owner, floor, home, yaw);
            case REACH -> reachTick(level, owner);
            case HOLD -> holdTick(level, owner);
            case THROW -> throwTick(level, owner);
            case SWEEP_AIM, SWEEP -> sweepTick(level, owner, floor);
            case CLAP_AIM, CLAP -> clapTick(level, owner, floor);
            case STUN_FALL -> {
                // Cae a plomo con aceleración y rebota (animación "stun_hit")
                double y = getY() - Math.min(1.6, 0.12 * timer * timer * 0.5 + 0.1);
                setPos(getX(), Math.max(floor, y), getZ());
                if (getY() <= floor + 0.01 && timer < 30 && aim.y != -1) {
                    aim = new Vec3(0, -1, 0);       // marca: ya ha tocado el suelo
                    level.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5F, 0.4F);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_TILES.defaultBlockState()),
                            getX(), floor + 0.1, getZ(), 30, 1.2, 0.05, 1.2, 0.1);
                }
                if (timer >= 18) { stunLength = STUN_TIME; setState(STUNNED); }
            }
            case STUNNED -> {
                setPos(getX(), floor, getZ());
                for (int k = 0; k < 2; k++) {
                    double a = tickCount * 0.3 + k * Math.PI;
                    level.sendParticles(ParticleTypes.CRIT, getX() + Math.cos(a) * 1.2, floor + 1.7, getZ() + Math.sin(a) * 1.2, 1, 0, 0, 0, 0);
                }
                if (timer >= stunLength && !owner.isExposed()) { stagger = 0; setState(RECOVER); }
            }
            case RECOVER -> {
                // Se incorpora a partir de la mitad de la animación "recover"
                if (timer == 8) level.playSound(null, blockPosition(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 1.0F, 0.6F);
                if (timer > 12) glide(home, 0.3, 0.12);
                if (timer >= RECOVER_TIME) setState(HOVER);
            }
            case DYING -> dyingTick(level);
            default -> setState(HOVER);
        }
        if (stagger > 0 && !isDown() && tickCount % 20 == 0) stagger = Math.max(0, stagger - 0.6F);   // se recupera poco a poco
    }

    private void riseTick(ServerLevel level, EchoDrummer owner, Vec3 home, float yaw) {
        // Trepa desde el abismo agarrada al borde (animación "rise")
        Vec3 edge = owner.handEdge(isLeft());
        face(yaw);
        if (timer <= 10) glide(edge.add(0, -1.0, 0), 0.5, 0.3);
        else if (timer <= 18) glide(edge.add(0, 0.6, 0), 0.4, 0.3);
        else glide(home, 0.5, 0.25);
        if (timer == 12) {
            level.playSound(null, blockPosition(), SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_TILES.defaultBlockState()),
                    edge.x, edge.y + 0.2, edge.z, 20, 0.8, 0.1, 0.8, 0.1);
        }
        if (timer >= RISE_TIME) setState(HOVER);
    }

    private void aimTick(ServerLevel level, EchoDrummer owner, double floor) {
        ServerPlayer t = target();
        if (t != null && timer < 24) aim = new Vec3(t.getX(), floor, t.getZ());
        if (aim == Vec3.ZERO || aim.y == -1) { setState(HOVER); return; }
        glide(aim.add(0, 5.5, 0), 0.8, 0.25);
        faceTo(aim);
        // Sombra en el suelo: avisa de dónde va a caer
        if (timer % 2 == 0) shadow(level, aim, 2.6, timer >= 24);
        if (timer == 1) level.playSound(null, blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 2.0F, 0.5F);
        if (timer >= 30) setState(SLAM);
    }

    private void slamTick(ServerLevel level, EchoDrummer owner, double floor, Vec3 home) {
        // Animación "slam": sube (0-10), cae (10-13), golpe en 13, se queda en el suelo y vuelve
        if (timer < 10) glide(aim.add(0, 6.2, 0), 0.4, 0.3);
        else if (timer <= 13) setPos(aim.x, Mth.lerp((timer - 10) / 3.0, aim.y + 6.2, floor), aim.z);
        if (timer == 13) {
            impact(level, owner, aim, 2.8, owner.scaledDamage(9.0F), 0.9, true);
            com.sugar.fx.Fx.shake(level, aim, 24, 1.5F, 10);
            owner.drumBoom(level, aim, 12, 0.35);
        }
        if (timer > 13 && timer < 34) setPos(aim.x, floor, aim.z);        // ventana para atacarla
        if (timer >= 34) glide(home, 0.5, 0.15);
        if (timer >= 48) setState(HOVER);
    }

    private void beatTick(ServerLevel level, EchoDrummer owner, double floor, Vec3 home, float yaw) {
        // Animación "beat" (0.5 s): golpe seco contra la piel del tambor en el tick 5
        face(yaw);
        Vec3 drum = new Vec3(home.x, floor, home.z);
        if (timer < 4) glide(drum.add(0, 2.2, 0), 0.6, 0.5);
        else if (timer == 4) setPos(drum.x, floor + 1.0, drum.z);
        else if (timer == 5) {
            setPos(drum.x, floor, drum.z);
            impact(level, owner, drum, 2.2, owner.scaledDamage(4.0F), 0.6, false);
            owner.drumBoom(level, drum, 40, 0.62);
        } else glide(drum.add(0, 2.2, 0), 0.4, 0.3);
        if (timer >= 10) setState(HOVER);
    }

    private void reachTick(ServerLevel level, EchoDrummer owner) {
        ServerPlayer t = target();
        if (t == null || timer > 60) { setState(HOVER); return; }
        Vec3 to = t.position().add(0, 0.2, 0);
        faceTo(to);
        glide(to, timer < 10 ? 0.2 : 0.55, 0.3);
        if (timer % 4 == 0) level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 0.5, getZ(), 1, 0.5, 0.2, 0.5, 0.01);
        if (position().distanceTo(to) < 1.8) {
            heldId = t.getUUID();
            heldStagger = 0;
            level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 2.0F, 0.5F);
            t.displayClientMessage(Component.literal("¡Te ha atrapado! Que los demás golpeen la mano.").withStyle(ChatFormatting.RED), true);
            setState(HOLD);
        }
    }

    @Nullable
    private ServerPlayer held() {
        if (heldId == null || !(level() instanceof ServerLevel sl)) return null;
        Entity e = sl.getEntity(heldId);
        return e instanceof ServerPlayer p && p.isAlive() && !p.isSpectator() ? p : null;
    }

    private void holdTick(ServerLevel level, EchoDrummer owner) {
        ServerPlayer p = held();
        if (p == null) { release(); setState(HOVER); return; }
        Vec3 up = new Vec3(getX(), owner.floorY() + 4.5, getZ());
        glide(up, 0.15, 0.1);
        faceTo(owner.position());
        p.teleportTo(getX(), getY() + 0.1, getZ());
        p.fallDistance = 0;
        if (timer % 10 == 0) p.hurt(damageSources().mobAttack(this), owner.scaledDamage(1.5F));
        if (heldStagger >= RELEASE_STAGGER) {
            release();
            level.playSound(null, blockPosition(), SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 1.5F, 0.7F);
            setState(HOVER);
            return;
        }
        if (timer >= 50) setState(THROW);
    }

    private void throwTick(ServerLevel level, EchoDrummer owner) {
        // Animación "throw": echa el brazo atrás y suelta en el tick 6
        ServerPlayer p = held();
        if (timer < 6 && p != null) p.teleportTo(getX(), getY() + 0.1, getZ());
        if (timer == 6 && p != null) {
            Vec3 dir = owner.position().subtract(position()).multiply(1, 0, 1);
            dir = dir.lengthSqr() < 1 ? new Vec3(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5) : dir;
            dir = dir.normalize();
            release();
            p.hurt(damageSources().mobAttack(this), owner.scaledDamage(6.0F));
            p.setDeltaMovement(dir.x * 1.1, 0.75, dir.z * 1.1);
            p.hurtMarked = true;
            level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 2.0F, 0.6F);
        }
        if (timer >= 14) setState(HOVER);
    }

    private void release() {
        heldId = null;
        heldStagger = 0;
    }

    private void sweepTick(ServerLevel level, EchoDrummer owner, double floor) {
        double r = 9.0;
        if (state() == SWEEP_AIM) {
            Vec3 start = sweepCenter.add(Math.cos(sweepFrom) * r, 0, Math.sin(sweepFrom) * r);
            glide(new Vec3(start.x, floor + 0.05, start.z), 0.7, 0.25);
            faceTangent(sweepFrom);
            if (timer % 3 == 0) shadow(level, new Vec3(start.x, floor, start.z), 1.6, false);
            if (timer == 1) level.playSound(null, blockPosition(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 1.5F, 0.6F);
            if (timer >= 26) setState(SWEEP);
            return;
        }
        int length = 46;
        double u = Math.min(1, timer / (double) length);
        double s = u * u * (3 - 2 * u);
        double a = sweepFrom + (sweepTo - sweepFrom) * s;
        Vec3 p = sweepCenter.add(Math.cos(a) * r, 0, Math.sin(a) * r);
        setPos(p.x, floor + 0.05, p.z);
        faceTangent(a);
        if (timer % 2 == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_TILES.defaultBlockState()),
                    p.x, floor + 0.1, p.z, 4, 0.8, 0.05, 0.8, 0.1);
            level.playSound(null, blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.HOSTILE, 0.6F, 0.5F);
        }
        // Barre a ras de suelo: saltar a tiempo lo esquiva
        double dir = Math.signum(sweepTo - sweepFrom);
        Vec3 tangent = new Vec3(-Math.sin(a) * dir, 0, Math.cos(a) * dir);
        for (ServerPlayer pl : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(1.0, 0.2, 1.0), q -> !q.isSpectator() && !q.isCreative())) {
            if (pl.getY() > floor + 1.1 || pl.hurtTime > 0) continue;
            pl.hurt(damageSources().mobAttack(this), owner.scaledDamage(7.0F));
            pl.push(tangent.x * 1.2, 0.5, tangent.z * 1.2);
            pl.hurtMarked = true;
        }
        if (timer >= length + 8) setState(HOVER);
    }

    private void clapTick(ServerLevel level, EchoDrummer owner, double floor) {
        // Las dos manos se colocan a los lados del objetivo, de canto, y se cierran de golpe
        float yaw = owner.getYRot();
        double ry = Math.toRadians(yaw);
        Vec3 fwd = new Vec3(-Math.sin(ry), 0, Math.cos(ry));
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        face(yaw);
        Vec3 c = new Vec3(aim.x, floor + 1.0, aim.z);
        if (state() == CLAP_AIM) {
            glide(c.add(right.scale(clapSide * 6.0)), 0.8, 0.25);
            if (timer % 3 == 0 && clapSide > 0) shadow(level, new Vec3(aim.x, floor, aim.z), 2.0, timer > 14);
            if (timer >= 20) setState(CLAP);
            return;
        }
        // Animación "clap": de canto (0-6), se separan (6-11), se cierran (11-13)
        if (timer < 11) glide(c.add(right.scale(clapSide * 7.0)), 0.4, 0.3);
        else if (timer <= 13) setPos(Mth.lerp((timer - 11) / 2.0, getX(), c.x + right.x * clapSide * 1.3), c.y,
                Mth.lerp((timer - 11) / 2.0, getZ(), c.z + right.z * clapSide * 1.3));
        if (timer == 13 && clapSide > 0) {
            level.playSound(null, BlockPos.containing(c), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0F, 1.4F);
            level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
            com.sugar.fx.Fx.shake(level, c, 22, 2.2F, 12);
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(c, c).inflate(2.6, 1.8, 2.6),
                    q -> !q.isSpectator() && !q.isCreative())) {
                p.hurt(damageSources().mobAttack(this), owner.scaledDamage(14.0F));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0));
            }
        }
        if (timer >= 24) setState(HOVER);
    }

    private void dyingTick(ServerLevel level) {
        if (timer % 4 == 0) level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 0.6, getZ(), 3, 0.8, 0.3, 0.8, 0.02);
        if (timer == 30) level.playSound(null, blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 1.5F, 0.6F);
        if (timer >= 40) {
            level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.6, getZ(), 30, 1.0, 0.4, 1.0, 0.05);
            discard();
        }
    }

    // ---------- Golpes ----------

    private void impact(ServerLevel level, EchoDrummer owner, Vec3 at, double radius, float damage, double lift, boolean big) {
        level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, big ? 2.0F : 1.2F, big ? 0.5F : 0.8F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_TILES.defaultBlockState()),
                at.x, at.y + 0.1, at.z, big ? 50 : 20, radius * 0.5, 0.05, radius * 0.5, 0.15);
        if (big) level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 2, 0.6, 0.1, 0.6, 0);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(at, at).inflate(radius, 2.5, radius),
                q -> !q.isSpectator() && !q.isCreative())) {
            p.hurt(damageSources().mobAttack(this), damage);
            Vec3 away = p.position().subtract(at).multiply(1, 0, 1);
            away = away.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : away.normalize();
            p.push(away.x * 0.9, lift, away.z * 0.9);
            p.hurtMarked = true;
        }
    }

    private void shadow(ServerLevel level, Vec3 at, double radius, boolean urgent) {
        int n = 14;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + tickCount * 0.05;
            level.sendParticles(urgent ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SCULK_SOUL,
                    at.x + Math.cos(a) * radius, at.y + 0.1, at.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.1, at.z, 3, radius * 0.4, 0, radius * 0.4, 0);
    }

    // ---------- Movimiento ----------

    private void glide(Vec3 to, double maxSpeed, double ease) {
        Vec3 d = to.subtract(position());
        Vec3 step = d.scale(ease);
        if (step.length() > maxSpeed) step = step.normalize().scale(maxSpeed);
        if (d.length() < 0.02) step = d;
        setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
    }

    private void face(float yaw) {
        float y = Mth.approachDegrees(getYRot(), yaw, 10);
        setYRot(y); yBodyRot = y; yHeadRot = y;
    }

    private void faceTo(Vec3 p) {
        double dx = p.x - getX(), dz = p.z - getZ();
        if (dx * dx + dz * dz < 0.04) return;
        face((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
    }

    private void faceTangent(double angle) {
        double dir = Math.signum(sweepTo - sweepFrom);
        face((float) Math.toDegrees(Math.atan2(Math.cos(angle) * dir, -Math.sin(angle) * dir)) - 90.0F);
    }

    // ---------- Daño ----------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        EchoDrummer owner = owner();
        if (owner == null || state() == DYING || state() == WAIT || owner.inIntro()) return false;
        if (!(source.getEntity() instanceof ServerPlayer player)) return false;
        if (isDown()) {
            if (player.tickCount % 2 == 0) player.displayClientMessage(Component.literal("¡Está en el suelo! Tirad la otra mano...").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        float hit = amount / owner.healthScale();
        stagger += hit;
        if (state() == HOLD) heldStagger += hit;
        boolean result = super.hurt(source, 0.01F);
        setHealth(getMaxHealth());
        level().playSound(null, blockPosition(), SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 1.2F, 0.5F);
        if (stagger >= STAGGER_MAX) {
            release();
            aim = Vec3.ZERO;
            setState(STUN_FALL);
            owner.onHandDown(this);
        } else {
            player.displayClientMessage(bar(stagger / STAGGER_MAX), true);
        }
        return result;
    }

    private Component bar(float f) {
        int full = Math.round(f * 20);
        return Component.literal((isLeft() ? "Mano izquierda " : "Mano derecha "))
                .append(Component.literal("|".repeat(full)).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("|".repeat(20 - full)).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Solo para las pruebas automáticas. */
    public void debugStun() {
        stagger = STAGGER_MAX;
        aim = Vec3.ZERO;
        setState(STUN_FALL);
        EchoDrummer o = owner();
        if (o != null) o.onHandDown(this);
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    public boolean isPickable() { return state() != WAIT; }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public boolean canChangeDimensions() { return false; }

    @Override
    public boolean fireImmune() { return true; }

    @Override
    public boolean shouldShowName() { return false; }

    // ---------- Guardado ----------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putBoolean("Left", isLeft());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        entityData.set(LEFT, tag.getBoolean("Left"));
        setState(HOVER);
    }

    // ---------- Animaciones ----------

    private static RawAnimation[] anims(String side) {
        String p = "animation.drummer_hand_" + side + ".";
        return new RawAnimation[]{
                RawAnimation.begin().thenLoop(p + "idle"),                                  // 0
                RawAnimation.begin().thenPlayAndHold(p + "rise"),                           // 1
                RawAnimation.begin().thenPlayAndHold(p + "slam"),                           // 2
                RawAnimation.begin().thenPlayAndHold(p + "beat"),                           // 3
                RawAnimation.begin().thenPlay(p + "grab").thenLoop(p + "hold"),             // 4
                RawAnimation.begin().thenPlayAndHold(p + "throw"),                          // 5
                RawAnimation.begin().thenLoop(p + "sweep"),                                 // 6
                RawAnimation.begin().thenPlayAndHold(p + "clap"),                           // 7
                RawAnimation.begin().thenPlay(p + "stun_hit").thenLoop(p + "stunned"),      // 8
                RawAnimation.begin().thenPlayAndHold(p + "recover"),                        // 9
                RawAnimation.begin().thenPlayAndHold(p + "death")};                         // 10
    }
    private static final RawAnimation[] RIGHT = anims("right"), LEFT_A = anims("left");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            RawAnimation[] a = isLeft() ? LEFT_A : RIGHT;
            return state.setAndContinue(switch (state()) {
                case RISE -> a[1];
                case SLAM -> a[2];
                case BEAT -> a[3];
                case HOLD -> a[4];
                case THROW -> a[5];
                case SWEEP -> a[6];
                case CLAP -> a[7];
                case STUN_FALL, STUNNED -> a[8];
                case RECOVER -> a[9];
                case DYING -> a[10];
                default -> a[0];
            });
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
