package com.sugar.temple;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.MutableComponent;
import com.sugar.block.EventDoorBlock;
import com.sugar.block.TempleBlock;
import com.sugar.boss.SugarBoss;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Lógica común de los templos: registro de bloques, reset, puertas, checkpoints y jugadores. */
public final class TempleService {

    // ---------- Bloques especiales ----------

    public static void registerBlock(ServerLevel level, BlockPos pos, BlockState state) {
        TempleSavedData saved = TempleSavedData.get(level.getServer());
        saved.findAt(level.dimension(), pos).ifPresent(data -> {
            data.blocks.add(pos.immutable());
            if (state.getBlock() instanceof TempleBlock tb) tb.onRegistered(data, pos.immutable());
            saved.setDirty();
        });
    }

    public static void unregisterBlock(ServerLevel level, BlockPos pos) {
        TempleSavedData saved = TempleSavedData.get(level.getServer());
        saved.findAt(level.dimension(), pos).ifPresent(data -> {
            data.blocks.remove(pos);
            if (pos.equals(data.altar)) data.altar = null;
            saved.setDirty();
        });
    }

    /** Busca todos los bloques del mod dentro de la zona (para templos construidos antes de definirlos o con WorldEdit). */
    public static int scan(ServerLevel level, TempleData data) {
        data.blocks.clear();
        data.altar = null;
        BoundingBox box = data.zone;
        int found = 0;
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof TempleBlock tb) {
                BlockPos pos = p.immutable();
                data.blocks.add(pos);
                tb.onRegistered(data, pos);
                found++;
            }
        }
        TempleSavedData.get(level.getServer()).setDirty();
        return found;
    }

    // ---------- Estado del evento ----------

    @Nullable
    public static ServerLevel levelOf(MinecraftServer server, TempleData data) {
        return server.getLevel(data.dimension);
    }

    public static void start(MinecraftServer server, TempleData data) {
        start(server, data, 0);
    }

    /** Empieza el evento. Con cuenta atrás, la puerta del vestíbulo se abre al terminar (la lleva {@link TempleEvents}). */
    public static void start(MinecraftServer server, TempleData data, int countdownTicks) {
        data.active = true;
        data.clearProgress();
        TempleSavedData.get(server).setDirty();
        if (countdownTicks > 0) data.countdown = countdownTicks;
        else openLobby(server, data);

        Component title = Component.literal("¡Evento!").withStyle(ChatFormatting.BOLD, data.type.color());
        Component subtitle = data.type.displayName();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.MASTER, 1.0F, 1.0F);
        }
        server.getPlayerList().broadcastSystemMessage(Component.literal("⚔ Ha comenzado el evento ")
                .append(data.type.displayName())
                .append(Component.literal(". Usa ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("/event join " + data.type.id()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" para unirte.").withStyle(ChatFormatting.GRAY)), false);
    }

    /** Fin de la cuenta atrás: se abre la puerta del vestíbulo y arranca el cronómetro. */
    public static void openLobby(MinecraftServer server, TempleData data) {
        data.countdown = 0;
        setDoors(server, data, 0, true);
        ServerLevel level = levelOf(server, data);
        if (level == null) return;
        data.startTime = level.getGameTime();
        TempleSavedData.get(server).setDirty();
        for (ServerPlayer p : playersInside(level, data)) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 30, 10));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("¡ADELANTE!").withStyle(ChatFormatting.BOLD, data.type.color())));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
            p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.MASTER, 1.0F, 1.2F);
        }
    }

    public static void stop(MinecraftServer server, TempleData data) {
        data.active = false;
        data.countdown = 0;
        data.summonCountdown = 0;
        data.checkpoints.clear();
        TempleSavedData.get(server).setDirty();
        server.getPlayerList().broadcastSystemMessage(Component.literal("El evento ")
                .append(data.type.displayName())
                .append(Component.literal(" ha terminado.")), false);
    }

    /** Deja el templo como nuevo: puertas cerradas, bloques restaurados, sin boss ni checkpoints. */
    public static void reset(MinecraftServer server, TempleData data) {
        ServerLevel level = levelOf(server, data);
        if (level != null) {
            List<BlockPos> stale = new ArrayList<>();
            for (BlockPos pos : List.copyOf(data.blocks)) {
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof TempleBlock tb) {
                    tb.resetBlock(level, pos, state);
                } else {
                    stale.add(pos);
                }
            }
            stale.forEach(data.blocks::remove);
            removeBoss(level, data);
        }
        data.active = false;
        data.clearProgress();
        TempleSavedData.get(server).setDirty();
    }

    /** Posiciones registradas cuyo bloque actual es del tipo indicado. */
    public static List<BlockPos> positionsOf(ServerLevel level, TempleData data, Class<?> blockClass) {
        List<BlockPos> list = new ArrayList<>();
        for (BlockPos pos : data.blocks) {
            if (blockClass.isInstance(level.getBlockState(pos).getBlock())) list.add(pos);
        }
        return list;
    }

    /** Mensaje en la barra de acción para todos los jugadores dentro del templo. */
    public static void actionbar(ServerLevel level, TempleData data, Component message) {
        for (ServerPlayer p : playersInside(level, data)) p.displayClientMessage(message, true);
    }

    public static int setDoors(MinecraftServer server, TempleData data, int channel, boolean open) {
        ServerLevel level = levelOf(server, data);
        if (level == null) return 0;
        int changed = 0;
        for (BlockPos pos : List.copyOf(data.blocks)) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof EventDoorBlock && state.getValue(EventDoorBlock.CHANNEL) == channel) {
                if (EventDoorBlock.setOpen(level, pos, state, open)) changed++;
            }
        }
        if (open) markObjective(level, data, channel);
        return changed;
    }

    // ---------- Objetivos y celebraciones ----------

    /** Cumple el objetivo de ese canal (si lo hay y no estaba cumplido) y lo celebra. */
    public static void markObjective(ServerLevel level, TempleData data, int channel) {
        TempleObjectives.Objective o = TempleObjectives.find(data.type, channel);
        if (o == null || !data.markDone(channel)) return;
        TempleSavedData.get(level.getServer()).setDirty();
        int done = TempleObjectives.done(data), total = TempleObjectives.of(data.type).size();
        if (channel == TempleObjectives.BOSS) return;      // el final lo celebra complete()
        celebrate(level, data, Component.literal("✔ " + o.name()).withStyle(ChatFormatting.GREEN),
                Component.literal("Progreso " + done + "/" + total).withStyle(ChatFormatting.GRAY), channel);
    }

    /** Título, sonido y chispas de colores en las puertas del canal. */
    public static void celebrate(ServerLevel level, TempleData data, Component title, Component subtitle, int channel) {
        for (ServerPlayer p : playersInside(level, data)) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
            p.connection.send(new ClientboundSetSubtitleTextPacket(title.copy().append(Component.literal("  ")).append(subtitle)));
            p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.8F, 1.0F);
        }
        for (BlockPos pos : data.blocks) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof EventDoorBlock) || state.getValue(EventDoorBlock.CHANNEL) != channel) continue;
            com.sugar.fx.Fx.particles(level, ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.4, 0.6, 0.4, 0.4);
            com.sugar.fx.Fx.particles(level, ParticleTypes.FIREWORK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.5, 0.3, 0.15);
            level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.BLOCKS, 0.6F, 1.0F);
        }
    }

    /** Tiempo desde la apertura del vestíbulo, en mm:ss. */
    public static String elapsed(ServerLevel level, TempleData data) {
        if (data.startTime < 0) return "--:--";
        long secs = Math.max(0, (level.getGameTime() - data.startTime) / 20);
        return String.format("%02d:%02d", secs / 60, secs % 60);
    }

    /** El jefe ha caído: fuegos artificiales y resumen del evento para todo el servidor. */
    public static void complete(ServerLevel level, TempleData data, SugarBoss boss) {
        data.markDone(TempleObjectives.BOSS);
        TempleSavedData.get(level.getServer()).setDirty();
        String time = elapsed(level, data);
        List<java.util.Map.Entry<java.util.UUID, Float>> ranking = new ArrayList<>(boss.damageByPlayer().entrySet());
        ranking.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
        List<String> mvps = new ArrayList<>();
        for (int i = 0; i < Math.min(3, ranking.size()); i++) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(ranking.get(i).getKey());
            if (p != null) mvps.add(p.getGameProfile().getName());
        }
        for (ServerPlayer p : playersInside(level, data)) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 30));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("¡Templo completado!").withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(data.type.displayName().copy().append(Component.literal("  ⏱ " + time).withStyle(ChatFormatting.WHITE))));
            p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0F, 0.8F);
        }
        MutableComponent msg = Component.literal("🏆 ").withStyle(ChatFormatting.GOLD)
                .append(data.type.displayName().copy().withStyle(ChatFormatting.BOLD))
                .append(Component.literal(" completado").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n   ⏱ Tiempo: ").withStyle(ChatFormatting.GRAY)).append(Component.literal(time).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("   ☠ Caídas: ").withStyle(ChatFormatting.GRAY)).append(Component.literal(String.valueOf(data.deaths)).withStyle(ChatFormatting.WHITE));
        if (!mvps.isEmpty()) msg = msg.append(Component.literal("\n   ★ MVP: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.join(", ", mvps)).withStyle(ChatFormatting.YELLOW));
        level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
        TempleEvents.launchFireworks(level, data);
    }

    /** Invoca al jefe del templo en el altar con su presentación. Devuelve null y explica el motivo si no puede. */
    @Nullable
    public static SugarBoss summonBoss(ServerLevel level, TempleData data, java.util.function.Consumer<String> error) {
        if (findBoss(level, data) != null) { error.accept("Ya hay un boss activo en ese templo."); return null; }
        java.util.Optional<net.minecraft.world.entity.EntityType<? extends SugarBoss>> bossType = TempleRegistry.boss(data.type);
        if (bossType.isEmpty()) { error.accept("El boss de '" + data.type.id() + "' no está disponible."); return null; }
        BlockPos spawn = data.altar != null ? data.altar.above() : data.lobby;
        SugarBoss boss = bossType.get().create(level);
        if (boss == null) { error.accept("No se pudo crear el boss."); return null; }
        boss.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), net.minecraft.world.entity.MobSpawnType.EVENT, null, null);
        boss.bindTemple(data.type);
        boss.rescale();
        level.addFreshEntity(boss);
        boss.beginIntro();
        data.bossId = boss.getUUID();
        TempleSavedData.get(level.getServer()).setDirty();
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("⚠ ")
                .withStyle(ChatFormatting.DARK_RED)
                .append(data.type.bossDisplayName())
                .append(Component.literal(" ha despertado en ").withStyle(ChatFormatting.DARK_RED))
                .append(data.type.displayName()), false);
        return boss;
    }

    // ---------- Boss ----------

    @Nullable
    public static SugarBoss findBoss(ServerLevel level, TempleData data) {
        if (data.bossId == null) return null;
        Entity e = level.getEntity(data.bossId);
        return e instanceof SugarBoss boss && boss.isAlive() ? boss : null;
    }

    public static boolean removeBoss(ServerLevel level, TempleData data) {
        SugarBoss boss = findBoss(level, data);
        data.bossId = null;
        if (boss != null) {
            boss.discard();
            return true;
        }
        return false;
    }

    // ---------- Jugadores ----------

    public static List<ServerPlayer> playersInside(ServerLevel level, TempleData data) {
        List<ServerPlayer> list = new ArrayList<>();
        if (!level.dimension().equals(data.dimension)) return list;
        for (ServerPlayer p : level.players()) {
            if (p.isAlive() && !p.isSpectator() && data.zone.isInside(p.blockPosition())) list.add(p);
        }
        return list;
    }

    public static void trySetCheckpoint(ServerPlayer player, BlockPos stonePos) {
        TempleSavedData saved = TempleSavedData.get(player.getServer());
        saved.findAt(player.level().dimension(), stonePos).ifPresent(data -> {
            if (!data.active) return;
            BlockPos cp = stonePos.above();
            BlockPos previous = data.checkpoints.put(player.getUUID(), cp);
            if (!cp.equals(previous)) {
                saved.setDirty();
                player.displayClientMessage(Component.literal("✔ Checkpoint guardado").withStyle(ChatFormatting.GREEN), true);
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.6F);
            }
        });
    }

    /** Teletransporta al jugador a su checkpoint o, si no tiene, al vestíbulo. */
    public static boolean sendToTemple(ServerPlayer player, TempleData data) {
        ServerLevel level = levelOf(player.getServer(), data);
        if (level == null) return false;
        BlockPos target = data.checkpoints.getOrDefault(player.getUUID(), data.lobby);
        player.teleportTo(level, target.getX() + 0.5, target.getY(), target.getZ() + 0.5, player.getYRot(), player.getXRot());
        return true;
    }

    private TempleService() {}
}
