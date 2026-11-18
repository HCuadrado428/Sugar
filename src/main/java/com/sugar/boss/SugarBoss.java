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
import net.minecraft.network.chat.Component;
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

    private final ServerBossEvent bossEvent;
    private final Map<UUID, Float> damageByPlayer = new HashMap<>();
    @Nullable private TempleType temple;
    private float healthScale = 1.0F;
    private float damageScale = 1.0F;
    private int scaledPlayers = 1;
    private boolean scaledOnce;

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

    /** Tabla de botín base: data/sugar/loot_tables/bosses/<id>_participante.json y <id>_mvp.json */
    public ResourceLocation rewardTable() {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(getType());
        return SugarMod.id("bosses/" + key.getPath());
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
        bossEvent.setProgress(getHealth() / getMaxHealth());
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
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }
}
