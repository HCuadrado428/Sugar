package com.sugar.temple;

import com.sugar.block.WaveAltarBlock;
import com.sugar.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sala de oleadas: al tocar el Altar de Oleadas salen 3 oleadas de monstruos del tema del templo
 * (más cuantos más jugadores haya). Al limpiar la última se abren las puertas del canal del altar.
 * Si alguno se queda atascado mucho rato, vuelve junto al altar.
 */
public final class WaveRoom {
    public static final int WAVES = 3;
    public static final String TAG = "sugar_wave";
    private static final int PAUSE = 60, STUCK_AFTER = 1200;

    static final class State {
        final TempleType temple;
        final BlockPos altar;
        final int channel;
        final List<UUID> mobs = new ArrayList<>();
        int wave, delay = PAUSE, spawned, waveTicks;
        State(TempleType temple, BlockPos altar, int channel) {
            this.temple = temple; this.altar = altar; this.channel = channel;
        }
    }

    private static final Map<BlockPos, State> STATES = new HashMap<>();

    public static boolean isRunning(BlockPos altar) {
        return STATES.containsKey(altar);
    }

    /** Lo llama el altar al usarlo. */
    public static void start(ServerLevel level, BlockPos altar, TempleData data, int channel) {
        State s = new State(data.type, altar.immutable(), channel);
        STATES.put(s.altar, s);
        level.playSound(null, altar, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4.0F, 0.7F);
        if (channel == TempleObjectives.V_FORGE) TempleService.setDoors(level.getServer(), data, TempleObjectives.V_FORGE_GATE, false);
        TempleStory.narrate(level, data, TempleStory.waveStart(data.type));
    }

    public static void tick(MinecraftServer server) {
        if (STATES.isEmpty()) return;
        Iterator<State> it = STATES.values().iterator();
        while (it.hasNext()) {
            State s = it.next();
            TempleData data = TempleSavedData.get(server).get(s.temple).orElse(null);
            ServerLevel level = data != null ? TempleService.levelOf(server, data) : null;
            if (data == null || level == null || !data.active) { it.remove(); continue; }
            if (s.delay > 0) {
                s.delay--;
                if (s.delay % 20 == 0 && s.delay > 0) level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, s.altar.getX() + 0.5, s.altar.getY() + 1.2, s.altar.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.05);
                if (s.delay == 0) spawnWave(level, data, s);
                continue;
            }
            s.waveTicks++;
            int alive = 0;
            for (UUID id : s.mobs) {
                Entity e = level.getEntity(id);
                if (e instanceof Mob m && m.isAlive()) {
                    alive++;
                    // Atascado o perdido: vuelve junto al altar
                    if (s.waveTicks > STUCK_AFTER && s.waveTicks % 200 == 0) m.teleportTo(s.altar.getX() + 0.5, s.altar.getY() + 1.0, s.altar.getZ() + 2.5);
                }
            }
            if (alive > 0) continue;
            s.wave++;
            s.mobs.clear();
            if (s.wave >= WAVES) {
                it.remove();
                level.setBlock(s.altar, level.getBlockState(s.altar).setValue(WaveAltarBlock.STATE, 2), 3);
                if (isForge(data, s.channel)) {
                    spawnForeman(level, data, s.altar);
                } else {
                    level.playSound(null, s.altar, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0F, 1.0F);
                    TempleService.setDoors(server, data, s.channel, true);
                    data.markDone(s.channel);
                    if (s.channel == TempleObjectives.V_FORGE) TempleService.setDoors(server, data, TempleObjectives.V_FORGE_GATE, true);
                }
            } else {
                s.delay = PAUSE;
                level.playSound(null, s.altar, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3.0F, 0.8F);
                TempleStory.narrate(level, data, TempleStory.waveNext(data.type));
            }
        }
    }

    /** La Forja del Templo del Volcán: tras las oleadas sale el Capataz. */
    private static boolean isForge(TempleData data, int channel) {
        return data.type == TempleType.VOLCAN && channel == TempleObjectives.V_FORGE;
    }

    public static com.sugar.entity.ForgeForeman spawnForeman(ServerLevel level, TempleData data, BlockPos altar) {
        com.sugar.entity.ForgeForeman f = ModEntities.FORGE_FOREMAN.get().create(level);
        if (f == null) return null;
        f.moveTo(altar.getX() + 0.5, altar.getY() + 0.1, altar.getZ() + 4.5, 180, 0);
        f.scaleFor(TempleService.playersInside(level, data).size());
        f.addTag(TAG);
        level.addFreshEntity(f);
        level.sendParticles(ParticleTypes.LAVA, f.getX(), f.getY() + 1, f.getZ(), 30, 0.8, 0.8, 0.8, 0.2);
        level.playSound(null, altar, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.5F, 0.6F);
        TempleStory.narrate(level, data, TempleStory.foreman());
        return f;
    }

    private static void spawnWave(ServerLevel level, TempleData data, State s) {
        int players = Math.max(1, TempleService.playersInside(level, data).size());
        int count = Math.min(36, 4 + (int) (players * 0.6) + s.wave * 2);     // 25 jugadores: 19-23; 50: 34-36
        List<EntityType<? extends Mob>> types = roster(data.type, s.wave);
        s.spawned = 0;
        s.waveTicks = 0;
        for (int i = 0; i < count; i++) {
            BlockPos at = spot(level, s.altar);
            if (at == null) continue;
            EntityType<? extends Mob> type = types.get(i % types.size());
            Mob mob = type.create(level);
            if (mob == null) continue;
            mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360, 0);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
            mob.setPersistenceRequired();
            mob.addTag(TAG);
            level.addFreshEntity(mob);
            s.mobs.add(mob.getUUID());
            s.spawned++;
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 8, 0.3, 0.4, 0.3, 0.02);
            level.sendParticles(ParticleTypes.SOUL, at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, 4, 0.3, 0.1, 0.3, 0.02);
        }
        level.playSound(null, s.altar, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 3.0F, 0.7F);
    }

    /** Monstruos de cada templo y oleada. */
    private static List<EntityType<? extends Mob>> roster(TempleType type, int wave) {
        return switch (type) {
            case VOLCAN -> switch (wave) {
                case 0 -> List.of(EntityType.MAGMA_CUBE);
                case 1 -> List.of(EntityType.MAGMA_CUBE, EntityType.BLAZE, EntityType.MAGMA_CUBE);
                default -> List.of(EntityType.WITHER_SKELETON, EntityType.BLAZE, EntityType.MAGMA_CUBE);
            };
            case CENIZA -> switch (wave) {
                case 0 -> List.of(ModEntities.ASH_HATCHLING.get());
                case 1 -> List.of(EntityType.HUSK, ModEntities.ASH_HATCHLING.get());
                default -> List.of(EntityType.HUSK, EntityType.SKELETON, ModEntities.ASH_HATCHLING.get());
            };
            case ECO -> switch (wave) {
                case 0 -> List.of(EntityType.ZOMBIE);
                case 1 -> List.of(EntityType.ZOMBIE, EntityType.SKELETON);
                default -> List.of(EntityType.STRAY, EntityType.CAVE_SPIDER, EntityType.ZOMBIE);
            };
        };
    }

    /** Un hueco libre en el suelo alrededor del altar (entre 4 y 9 bloques). */
    @Nullable
    private static BlockPos spot(ServerLevel level, BlockPos altar) {
        for (int tries = 0; tries < 30; tries++) {
            double a = level.random.nextDouble() * Math.PI * 2, d = 4 + level.random.nextDouble() * 5;
            BlockPos base = BlockPos.containing(altar.getX() + 0.5 + Math.cos(a) * d, altar.getY(), altar.getZ() + 0.5 + Math.sin(a) * d);
            for (int dy = -1; dy <= 2; dy++) {
                BlockPos p = base.above(dy);
                if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                        && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)
                        && sameRoom(level, altar, p)) return p;
            }
        }
        return null;
    }

    /** Sin paredes entre el altar y el hueco (para no sacar monstruos fuera de la sala). */
    private static boolean sameRoom(ServerLevel level, BlockPos altar, BlockPos p) {
        net.minecraft.world.phys.Vec3 from = new net.minecraft.world.phys.Vec3(altar.getX() + 0.5, altar.getY() + 1.5, altar.getZ() + 0.5);
        net.minecraft.world.phys.Vec3 to = new net.minecraft.world.phys.Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
        return level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, null)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    /** /event reset: quita los monstruos. */
    public static void reset(ServerLevel level, BlockPos altar) {
        State s = STATES.remove(altar);
        for (Mob m : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(altar).inflate(48), e -> e.getTags().contains(TAG))) m.discard();
    }

    private WaveRoom() {}
}
