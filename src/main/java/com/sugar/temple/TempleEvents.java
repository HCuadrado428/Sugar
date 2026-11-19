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
 * El "modo evento" de los templos, cada tick del servidor:
 *  - cuenta atrás de /event start y de la campana del jefe,
 *  - barra de progreso del templo (objetivo actual, n/total y cronómetro),
 *  - placas de grupo (se abren cuando todas las del canal están ocupadas a la vez),
 *  - salas de oleadas ({@link WaveRoom}),
 *  - contador de caídas.
 */
@Mod.EventBusSubscriber(modid = SugarMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TempleEvents {
    private static final Map<TempleType, ServerBossEvent> BARS = new EnumMap<>(TempleType.class);
    private static final Map<TempleType, Integer> LAST_PLATES = new EnumMap<>(TempleType.class);

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        for (TempleData data : TempleSavedData.get(server).all()) {
            ServerLevel level = TempleService.levelOf(server, data);
            if (level == null) continue;
            if (!data.active) { hideBar(data.type); continue; }
            countdowns(server, level, data);
            if (tick % 5 == 0) plates(level, data);
            if (tick % 10 == 0) updateBar(level, data);
        }
        WaveRoom.tick(server);
    }

    // ---------- Cuentas atrás ----------

    private static void countdowns(MinecraftServer server, ServerLevel level, TempleData data) {
        if (data.countdown > 0) {
            data.countdown--;
            if (data.countdown == 0) TempleService.openLobby(server, data);
            else if (data.countdown % 20 == 0 && data.countdown <= 200) {
                int n = data.countdown / 20;
                title(level, data, Component.literal(String.valueOf(n)).withStyle(ChatFormatting.BOLD, n <= 3 ? ChatFormatting.RED : ChatFormatting.WHITE),
                        Component.literal("El evento empieza...").withStyle(ChatFormatting.GRAY));
                for (ServerPlayer p : TempleService.playersInside(level, data))
                    p.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.MASTER, 1.0F, n <= 3 ? 1.6F : 1.0F);
            }
        }
        if (data.summonCountdown > 0) {
            data.summonCountdown--;
            if (data.summonCountdown == 0) {
                TempleService.summonBoss(level, data, e -> SugarMod.LOGGER.warn("Campana del jefe: {}", e));
            } else if (data.summonCountdown % 20 == 0) {
                int n = data.summonCountdown / 20;
                title(level, data, Component.literal(String.valueOf(n)).withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_RED),
                        data.type.bossDisplayName().copy().append(Component.literal(" despierta...").withStyle(ChatFormatting.GRAY)));
                for (ServerPlayer p : TempleService.playersInside(level, data))
                    p.playNotifySound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.MASTER, 1.0F, 0.5F);
            }
        }
    }

    private static void title(ServerLevel level, TempleData data, Component title, Component subtitle) {
        for (ServerPlayer p : TempleService.playersInside(level, data)) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 22, 4));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    // ---------- Barra de progreso ----------

    private static void updateBar(ServerLevel level, TempleData data) {
        ServerBossEvent bar = BARS.computeIfAbsent(data.type, t -> {
            ServerBossEvent b = new ServerBossEvent(t.displayName(), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_6);
            b.setDarkenScreen(false);
            return b;
        });
        int total = TempleObjectives.of(data.type).size();
        int done = TempleObjectives.done(data);
        TempleObjectives.Objective cur = TempleObjectives.current(data);
        Component name = data.type.displayName().copy()
                .append(Component.literal("  " + done + "/" + total + "  ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(data.countdown > 0 ? "Preparados..." : cur != null ? cur.name() : "¡Completado!").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("  ⏱ " + TempleService.elapsed(level, data)).withStyle(ChatFormatting.GRAY));
        bar.setName(name);
        bar.setProgress(total == 0 ? 0 : done / (float) total);
        // Se oculta mientras dura el combate contra el jefe: ya tiene su propia barra
        boolean fighting = TempleService.findBoss(level, data) != null;
        Set<ServerPlayer> inside = fighting ? Set.of() : new HashSet<>(TempleService.playersInside(level, data));
        for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) if (!inside.contains(p)) bar.removePlayer(p);
        for (ServerPlayer p : inside) bar.addPlayer(p);
    }

    private static void hideBar(TempleType type) {
        ServerBossEvent bar = BARS.get(type);
        if (bar != null && !bar.getPlayers().isEmpty()) bar.removeAllPlayers();
    }

    // ---------- Placas de grupo ----------

    private static void plates(ServerLevel level, TempleData data) {
        Map<Integer, List<BlockPos>> byChannel = new HashMap<>();
        for (BlockPos p : TempleService.positionsOf(level, data, GroupPlateBlock.class)) {
            byChannel.computeIfAbsent(level.getBlockState(p).getValue(GroupPlateBlock.CHANNEL), k -> new ArrayList<>()).add(p);
        }
        int litTotal = 0;
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
            if (lit == e.getValue().size()) {
                TempleService.setDoors(level.getServer(), data, channel, true);
                data.markDone(channel);     // por si el canal no es un objetivo
                TempleService.actionbar(level, data, Component.literal("¡Todas las placas a la vez! Se abre el paso").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            } else if (lit > 0 && !Integer.valueOf(lit).equals(LAST_PLATES.get(data.type))) {
                TempleService.actionbar(level, data, Component.literal("Placas ocupadas: " + lit + "/" + e.getValue().size()
                        + "  (hace falta alguien en cada una a la vez)").withStyle(ChatFormatting.GOLD));
            }
        }
        LAST_PLATES.put(data.type, litTotal);
    }

    // ---------- Caídas ----------

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) return;
        for (TempleData data : TempleSavedData.get(player.getServer()).all()) {
            if (!data.active || !data.contains(player.level().dimension(), player.blockPosition())) continue;
            data.deaths++;
            TempleSavedData.get(player.getServer()).setDirty();
            ServerLevel level = TempleService.levelOf(player.getServer(), data);
            if (level != null) TempleService.actionbar(level, data, Component.literal("☠ " + player.getGameProfile().getName()
                    + " ha caído  (caídas del grupo: " + data.deaths + ")").withStyle(ChatFormatting.RED));
            return;
        }
    }

    // ---------- Final ----------

    /** Fuegos artificiales desde el altar al derrotar al jefe (explotan muy por encima de los jugadores). */
    public static void launchFireworks(ServerLevel level, TempleData data) {
        BlockPos c = data.altar != null ? data.altar.above() : data.lobby;
        int[][] palettes = {{0xFF4500, 0xFFD700}, {0xFFFFFF, 0xC0C0C0}, {0x40E0D0, 0x9370DB}};
        int[] colors = palettes[data.type.ordinal() % palettes.length];
        for (int i = 0; i < 6; i++) {
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            CompoundTag fw = new CompoundTag();
            fw.putByte("Flight", (byte) 2);
            ListTag explosions = new ListTag();
            CompoundTag ex = new CompoundTag();
            ex.putByte("Type", (byte) (i % 2 == 0 ? 1 : 4));
            ex.putIntArray("Colors", colors);
            ex.putIntArray("FadeColors", new int[]{0xFFFFFF});
            ex.putBoolean("Trail", true);
            ex.putBoolean("Flicker", true);
            explosions.add(ex);
            fw.put("Explosions", explosions);
            rocket.getOrCreateTag().put("Fireworks", fw);
            double a = Math.PI * 2 * i / 6;
            FireworkRocketEntity e = new FireworkRocketEntity(level, c.getX() + 0.5 + Math.cos(a) * 3, c.getY() + 1.0, c.getZ() + 0.5 + Math.sin(a) * 3, rocket);
            level.addFreshEntity(e);
        }
    }

    @SubscribeEvent
    public static void onStop(ServerStoppedEvent event) {
        BARS.values().forEach(ServerBossEvent::removeAllPlayers);
        BARS.clear();
        LAST_PLATES.clear();
    }

    private TempleEvents() {}
}
