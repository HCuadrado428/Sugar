package com.sugar.temple;

import com.sugar.SugarMod;
import com.sugar.block.GroupPlateBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lo que pasa en los templos cada tick del servidor, sin nada en pantalla:
 *  - la espera tras tocar la campana del jefe (el suelo late hasta que despierta),
 *  - placas de grupo (se abren cuando todas las del canal están ocupadas a la vez),
 *  - salas de oleadas ({@link WaveRoom}),
 *  - caídas (solo se cuentan para /event info).
 */
@Mod.EventBusSubscriber(modid = SugarMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TempleEvents {
    private static final Map<TempleType, Integer> LAST_PLATES = new EnumMap<>(TempleType.class);

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        for (TempleData data : TempleSavedData.get(server).all()) {
            ServerLevel level = TempleService.levelOf(server, data);
            if (level == null) continue;
            if (!data.active) continue;
            summonDelay(level, data);
            if (tick % 5 == 0) plates(level, data);
            if (tick % 20 == 0) lairQuorum(level, data);
        }
        WaveRoom.tick(server);
    }

    // ---------- La campana ----------

    /** Tras tocar la campana el suelo tiembla unos segundos y el jefe despierta (sin cuenta atrás en pantalla). */
    private static void summonDelay(ServerLevel level, TempleData data) {
        if (data.summonCountdown <= 0) return;
        data.summonCountdown--;
        if (data.summonCountdown % 25 == 0 && data.altar != null)
            level.playSound(null, data.altar, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 4.0F, 0.5F);
        if (data.summonCountdown == 0) TempleService.summonBoss(level, data, e -> SugarMod.LOGGER.warn("Campana del jefe: {}", e));
    }

    // ---------- Placas de grupo ----------

    private static void plates(ServerLevel level, TempleData data) {
        Map<Integer, List<BlockPos>> byChannel = new HashMap<>();
        for (BlockPos p : TempleService.positionsOf(level, data, GroupPlateBlock.class)) {
            byChannel.computeIfAbsent(level.getBlockState(p).getValue(GroupPlateBlock.CHANNEL), k -> new ArrayList<>()).add(p);
        }
        int litTotal = 0;
        // Con mucha gente hacen falta más placas: 4 hasta 10 jugadores, luego una más cada 7 (8 con 38 o más)
        int inside = TempleService.playersInside(level, data).size();
        int needed = 4 + Math.max(0, inside - 10) / 7;
        for (Map.Entry<Integer, List<BlockPos>> e : byChannel.entrySet()) {
            int channel = e.getKey();
            boolean solved = data.isDone(channel);
            int lit = 0;
            for (BlockPos p : e.getValue()) {
                BlockState s = level.getBlockState(p);
                boolean on = solved || !level.getEntitiesOfClass(ServerPlayer.class, new AABB(p.above()).deflate(0.05, 0.0, 0.05).expandTowards(0, 0.5, 0),
                        q -> !q.isSpectator() && q.isAlive()).isEmpty();
                if (on) lit++;
                if (s.getValue(GroupPlateBlock.LIT) != on) {
                    level.setBlock(p, s.setValue(GroupPlateBlock.LIT, on), Block.UPDATE_CLIENTS);
                    level.playSound(null, p, on ? SoundEvents.STONE_PRESSURE_PLATE_CLICK_ON : SoundEvents.STONE_PRESSURE_PLATE_CLICK_OFF,
                            SoundSource.BLOCKS, 1.0F, on ? 0.8F : 0.6F);
                }
            }
            if (solved) continue;
            litTotal += lit;
            if (lit >= Math.min(e.getValue().size(), needed)) {
                TempleService.setDoors(level.getServer(), data, channel, true);
                data.markDone(channel);     // por si el canal no es un paso de la historia
            }
        }
        LAST_PLATES.put(data.type, litTotal);
    }

    // ---------- Guarida del Volcán ----------

    /**
     * Templo del Volcán: con la puerta del jefe abierta, cuando el 60 % de los que están dentro han bajado a la
     * guarida, la puerta se cierra a su espalda y la Sierpe despierta.
     */
    private static void lairQuorum(ServerLevel level, TempleData data) {
        if (data.type != TempleType.VOLCAN || data.altar == null || !data.isDone(TempleObjectives.V_BOSS_DOOR)
                || data.isDone(TempleObjectives.BELL) || data.summonCountdown > 0 || TempleService.findBoss(level, data) != null) return;
        List<ServerPlayer> inside = TempleService.playersInside(level, data);
        if (inside.isEmpty()) return;
        long inLair = inside.stream().filter(p -> inLair(data, p.blockPosition())).count();
        if (inLair < Math.max(1, Math.ceil(inside.size() * 0.6))) return;
        TempleService.setDoors(level.getServer(), data, TempleObjectives.V_BOSS_DOOR, false);
        TempleService.markObjective(level, data, TempleObjectives.BELL);
        data.summonCountdown = 100;
    }

    public static boolean inLair(TempleData data, BlockPos p) {
        BlockPos a = data.altar;
        int dx = p.getX() - a.getX(), dz = p.getZ() - a.getZ();
        return dx * dx + dz * dz <= 25 * 25 && p.getY() >= a.getY() - 2 && p.getY() <= a.getY() + 16;
    }

    // ---------- Caídas ----------

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) return;
        for (TempleData data : TempleSavedData.get(player.getServer()).all()) {
            if (!data.active || !data.contains(player.level().dimension(), player.blockPosition())) continue;
            data.deaths++;             // solo para /event info
            TempleSavedData.get(player.getServer()).setDirty();
            return;
        }
    }

    @SubscribeEvent
    public static void onStop(ServerStoppedEvent event) {
        LAST_PLATES.clear();
    }

    private TempleEvents() {}
}
