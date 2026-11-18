package com.sugar.temple;

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
        data.active = true;
        data.checkpoints.clear();
        data.claimedSupplies.clear();
        TempleSavedData.get(server).setDirty();
        setDoors(server, data, 0, true);

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

    public static void stop(MinecraftServer server, TempleData data) {
        data.active = false;
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
        data.checkpoints.clear();
        data.claimedSupplies.clear();
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
        return changed;
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
