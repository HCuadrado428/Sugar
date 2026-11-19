package com.sugar.boss;

import com.sugar.SugarConfig;
import com.sugar.SugarMod;
import com.sugar.temple.TempleData;
import com.sugar.temple.TempleSavedData;
import com.sugar.temple.TempleService;
import com.sugar.temple.TempleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Base de todos los bosses de evento:
 * - Barra de jefe para los jugadores cercanos.
 * - Escalado según los jugadores presentes. La vida se escala reduciendo el daño recibido
 *   (vida efectiva), porque Minecraft limita la vida máxima a 1024.
 * - Registro del daño de cada jugador para repartir recompensas al morir.
 */
public abstract class SugarBoss extends Monster {
    private static final UUID DAMAGE_SCALING_ID = UUID.fromString("5b0f6c2e-7d4a-4c8e-9a41-3f1d2e6b8c01");
    private static final EntityDataAccessor<Boolean> INTRO = SynchedEntityData.defineId(SugarBoss.class, EntityDataSerializers.BOOLEAN);
    /** Progreso de la presentación (0-1): rellena la barra de jefe mientras dura. */
    protected float introProgress = 1.0F;

    private final ServerBossEvent bossEvent;
    private final Map<UUID, Float> damageByPlayer = new HashMap<>();
    @Nullable private TempleType temple;
    private float healthScale = 1.0F;
    private float damageScale = 1.0F;
    private int scaledPlayers = 1;
    private boolean scaledOnce;
    /** Fase del combate: 1 (100-66 %), 2 (66-33 %), 3 (33-0 %). */
    private int stage = 1;
    /** Ticks que quedan de invulnerabilidad por el cambio de fase (la escena del jefe). */
    protected int stageLock;

    protected SugarBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.bossEvent = new ServerBossEvent(getDisplayName(), barColor(), BossEvent.BossBarOverlay.NOTCHED_10);
        this.bossEvent.setDarkenScreen(false);
        this.xpReward = 500;
        setPersistenceRequired();
    }

    /** Atributos base comunes. Cada boss los ajusta con sus propios valores. */
    public static AttributeSupplier.Builder createBossAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 500.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    protected abstract BossEvent.BossBarColor barColor();

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(INTRO, false);
    }

    // ---------- Presentación ----------

    /** Arranca la presentación del jefe (la llama /eventboss summon). Cada jefe la redefine. */
    public void beginIntro() {}

    public boolean inIntro() {
        return entityData.get(INTRO);
    }

    protected void setIntro(boolean intro) {
        entityData.set(INTRO, intro);
        introProgress = intro ? 0.0F : 1.0F;
    }

    /** Título grande en pantalla para todos los jugadores del templo (o cercanos). */
    protected void announce(Component title, Component subtitle) {
        if (!(level() instanceof ServerLevel level)) return;
        List<ServerPlayer> players = templeData().map(d -> TempleService.playersInside(level, d))
                .orElseGet(() -> level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(64)));
        for (ServerPlayer p : players) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    protected void announceBoss() {
        TempleType t = temple;
        Component title = (t != null ? t.bossDisplayName() : getDisplayName()).copy().withStyle(ChatFormatting.BOLD);
        Component subtitle = t != null ? Component.literal("— ").append(t.displayName()).append(" —") : Component.empty();
        announce(title, subtitle);
    }

    /** Tabla de botín base: data/sugar/loot_tables/bosses/<id>_participante.json y <id>_mvp.json */
    public ResourceLocation rewardTable() {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(getType());
        return SugarMod.id("bosses/" + key.getPath());
    }

    // ---------- Fases ----------

    public int stage() {
        return stage;
    }

    /** Se llama una vez al entrar en la fase 2 y otra al entrar en la 3. Cada jefe monta aquí su escena. */
    protected void onStageChange(ServerLevel level, int newStage) {}

    /** Para las pruebas: fuerza el paso a la siguiente fase. */
    public void debugNextStage() {
        if (stage < 3 && level() instanceof ServerLevel sl) advanceStage(sl);
    }

    private void advanceStage(ServerLevel level) {
        stage++;
        updateBarName();
        onStageChange(level, stage);
    }

    private void updateBarName() {
        Component name = getDisplayName();
        bossEvent.setName(stage <= 1 ? name : name.copy().append(Component.literal(stage == 2 ? "  · II" : "  · III").withStyle(ChatFormatting.GOLD)));
    }

    /** Título de cambio de fase para todo el templo. */
    protected void announceStage(Component subtitle) {
        TempleType t = temple;
        Component name = (t != null ? t.bossDisplayName() : getDisplayName()).copy();
        announce(name.copy().append(stage == 2 ? " · II" : " · III").withStyle(ChatFormatting.BOLD), subtitle);
    }

    /** Sacudida de pantalla alrededor del jefe. */
    protected void shake(float intensity, int ticks) {
        if (level() instanceof ServerLevel sl) com.sugar.fx.Fx.shake(sl, position(), 72.0, intensity, ticks);
    }

    // ---------- Templo ----------

    public void bindTemple(TempleType type) {
        this.temple = type;
    }

    @Nullable
    public TempleType temple() {
        return temple;
    }

    public Optional<TempleData> templeData() {
        if (temple == null || getServer() == null) return Optional.empty();
        return TempleSavedData.get(getServer()).get(temple);
    }

    // ---------- Escalado ----------

    public int countPlayers() {
        if (!(level() instanceof ServerLevel serverLevel)) return 1;
        Optional<TempleData> data = templeData();
        int count;
        if (data.isPresent()) {
            count = TempleService.playersInside(serverLevel, data.get()).size();
        } else {
            double r = SugarConfig.SCALING_RADIUS.get();
            count = serverLevel.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(r),
                    p -> p.isAlive() && !p.isSpectator()).size();
        }
        return Math.max(1, Math.min(count, SugarConfig.MAX_SCALING_PLAYERS.get()));
    }

    public void rescale() {
        int players = countPlayers();
        this.scaledPlayers = players;
        this.healthScale = (float) (1.0 + SugarConfig.HEALTH_PER_PLAYER.get() * (players - 1));
        this.damageScale = (float) (1.0 + SugarConfig.DAMAGE_PER_PLAYER.get() * (players - 1));

        AttributeInstance attack = getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.removeModifier(DAMAGE_SCALING_ID);
            attack.addTransientModifier(new AttributeModifier(DAMAGE_SCALING_ID, "Sugar escalado de daño",
                    damageScale - 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        this.scaledOnce = true;
    }

    /** Usar en los ataques especiales para que también escalen con los jugadores. */
    public float scaledDamage(float base) {
        return base * damageScale;
    }

    public int scaledPlayers() {
        return scaledPlayers;
    }

    public float healthScale() {
        return healthScale;
    }

    // ---------- Daño y muerte ----------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return super.hurt(source, amount);
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING)) return false;
        if (inIntro() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;   // invulnerable durante la presentación
        if (stageLock > 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false; // y durante el cambio de fase

        float before = getHealth();
        boolean result = super.hurt(source, amount / healthScale);
        float dealt = before - getHealth();
        if (dealt > 0 && source.getEntity() instanceof ServerPlayer player) {
            damageByPlayer.merge(player.getUUID(), dealt, Float::sum);
        }
        return result;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && !this.dead && !isRemoved() && level() instanceof ServerLevel serverLevel) {
            BossRewards.distribute(serverLevel, this);
            templeData().ifPresent(data -> {
                data.bossId = null;
                TempleSavedData.get(serverLevel.getServer()).setDirty();
            });
        }
        super.die(source);
    }

    public Map<UUID, Float> damageByPlayer() {
        return Collections.unmodifiableMap(damageByPlayer);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // ---------- Barra de jefe ----------

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!scaledOnce || tickCount % 200 == 0) rescale();
        if (stageLock > 0) stageLock--;
        if (!inIntro() && stage < 3 && isAlive() && level() instanceof ServerLevel sl
                && getHealth() / getMaxHealth() <= (stage == 1 ? 0.66F : 0.33F)) advanceStage(sl);
        bossEvent.setProgress(inIntro() ? Math.max(0.0F, Math.min(1.0F, introProgress)) : getHealth() / getMaxHealth());
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    // ---------- Guardado ----------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (temple != null) tag.putString("SugarTemple", temple.id());
        tag.putInt("SugarStage", stage);
        ListTag dmg = new ListTag();
        damageByPlayer.forEach((uuid, value) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("Player", uuid);
            c.putFloat("Damage", value);
            dmg.add(c);
        });
        tag.put("SugarDamage", dmg);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SugarTemple")) temple = TempleType.byId(tag.getString("SugarTemple"));
        ListTag dmg = tag.getList("SugarDamage", Tag.TAG_COMPOUND);
        for (int i = 0; i < dmg.size(); i++) {
            CompoundTag c = dmg.getCompound(i);
            damageByPlayer.put(c.getUUID("Player"), c.getFloat("Damage"));
        }
        stage = Math.max(1, Math.min(3, tag.getInt("SugarStage")));
        updateBarName();
        setIntro(false);
    }
}
