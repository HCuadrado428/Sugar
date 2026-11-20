package com.sugar.temple;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Estado guardado de un templo: zona, puntos clave, checkpoints y bloques especiales. */
public class TempleData {
    public final TempleType type;
    public ResourceKey<Level> dimension;
    public BoundingBox zone;
    public BlockPos lobby;
    @Nullable public BlockPos altar;
    public boolean active;
    /** Abierto para reunirse: se puede entrar y usar /event join, aunque la historia no ha empezado. */
    public boolean open;
    @Nullable public UUID bossId;
    public final Map<UUID, BlockPos> checkpoints = new HashMap<>();
    public final Set<BlockPos> blocks = new HashSet<>();
    public final Set<UUID> claimedSupplies = new HashSet<>();
    public final Set<UUID> claimedTreasure = new HashSet<>();
    /** Canales cuyo objetivo ya se ha cumplido (bit por canal). */
    public int progressMask;
    /** Tick de juego en que se abrió la puerta del vestíbulo (-1 si no ha empezado). */
    public long startTime = -1;
    public int deaths;
    /** Llaves del grupo (bits): 1-4 llaves pequeñas, {@link #BOSS_KEY} la Llave del Jefe. */
    public int keys;
    /** Quién ha sacado ya su objeto de cada Gran Cofre (posición → jugadores). */
    public final Map<Long, Set<UUID>> chestClaims = new HashMap<>();
    public static final int BOSS_KEY = 1 << 4;
    /** Bloques empujables que se han movido: posición actual → posición original (para el reset). */
    public final Map<Long, Long> pushHome = new HashMap<>();
    // Sin guardar: cuentas atrás en ticks
    public transient int countdown;
    public transient int summonCountdown;

    public boolean isDone(int channel) {
        return (progressMask & (1 << channel)) != 0;
    }

    /** Marca el canal como cumplido; devuelve false si ya lo estaba. */
    public boolean markDone(int channel) {
        if (isDone(channel)) return false;
        progressMask |= 1 << channel;
        return true;
    }

    /** Vuelve a dejar el progreso del evento a cero. */
    public void clearProgress() {
        checkpoints.clear();
        claimedSupplies.clear();
        claimedTreasure.clear();
        progressMask = 0;
        startTime = -1;
        deaths = 0;
        keys = 0;
        chestClaims.clear();
        countdown = 0;
        summonCountdown = 0;
    }

    public TempleData(TempleType type, ResourceKey<Level> dimension, BoundingBox zone, BlockPos lobby) {
        this.type = type;
        this.dimension = dimension;
        this.zone = zone;
        this.lobby = lobby;
    }

    public boolean contains(ResourceKey<Level> dim, BlockPos pos) {
        return dimension.equals(dim) && zone.isInside(pos);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.id());
        tag.putString("dim", dimension.location().toString());
        tag.putIntArray("zone", new int[]{zone.minX(), zone.minY(), zone.minZ(), zone.maxX(), zone.maxY(), zone.maxZ()});
        tag.put("lobby", NbtUtils.writeBlockPos(lobby));
        if (altar != null) tag.put("altar", NbtUtils.writeBlockPos(altar));
        tag.putBoolean("active", active);
        tag.putBoolean("open", open);
        if (bossId != null) tag.putUUID("boss", bossId);

        ListTag cps = new ListTag();
        checkpoints.forEach((uuid, pos) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("player", uuid);
            c.put("pos", NbtUtils.writeBlockPos(pos));
            cps.add(c);
        });
        tag.put("checkpoints", cps);

        tag.putLongArray("blocks", blocks.stream().mapToLong(BlockPos::asLong).toArray());

        ListTag claimed = new ListTag();
        claimedSupplies.forEach(uuid -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("player", uuid);
            claimed.add(c);
        });
        tag.put("claimed", claimed);
        ListTag treasure = new ListTag();
        claimedTreasure.forEach(uuid -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("player", uuid);
            treasure.add(c);
        });
        tag.put("treasure", treasure);
        tag.putInt("progress", progressMask);
        tag.putLong("start", startTime);
        tag.putInt("deaths", deaths);
        tag.putInt("keys", keys);
        ListTag chests = new ListTag();
        chestClaims.forEach((pos, players) -> players.forEach(uuid -> {
            CompoundTag c = new CompoundTag();
            c.putLong("pos", pos);
            c.putUUID("player", uuid);
            chests.add(c);
        }));
        tag.put("chests", chests);
        ListTag pushes = new ListTag();
        pushHome.forEach((cur, home) -> { CompoundTag c = new CompoundTag(); c.putLong("cur", cur); c.putLong("home", home); pushes.add(c); });
        tag.put("pushes", pushes);
        return tag;
    }

    @Nullable
    public static TempleData load(CompoundTag tag) {
        TempleType type = TempleType.byId(tag.getString("type"));
        if (type == null) return null;
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("dim")));
        int[] z = tag.getIntArray("zone");
        if (z.length != 6) return null;
        BoundingBox zone = new BoundingBox(z[0], z[1], z[2], z[3], z[4], z[5]);
        TempleData data = new TempleData(type, dim, zone, NbtUtils.readBlockPos(tag.getCompound("lobby")));
        if (tag.contains("altar")) data.altar = NbtUtils.readBlockPos(tag.getCompound("altar"));
        data.active = tag.getBoolean("active");
        data.open = tag.getBoolean("open") || data.active;
        if (tag.hasUUID("boss")) data.bossId = tag.getUUID("boss");

        ListTag cps = tag.getList("checkpoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < cps.size(); i++) {
            CompoundTag c = cps.getCompound(i);
            data.checkpoints.put(c.getUUID("player"), NbtUtils.readBlockPos(c.getCompound("pos")));
        }
        for (long l : tag.getLongArray("blocks")) {
            data.blocks.add(BlockPos.of(l));
        }
        ListTag claimed = tag.getList("claimed", Tag.TAG_COMPOUND);
        for (int i = 0; i < claimed.size(); i++) {
            data.claimedSupplies.add(claimed.getCompound(i).getUUID("player"));
        }
        ListTag treasure = tag.getList("treasure", Tag.TAG_COMPOUND);
        for (int i = 0; i < treasure.size(); i++) data.claimedTreasure.add(treasure.getCompound(i).getUUID("player"));
        data.progressMask = tag.getInt("progress");
        data.startTime = tag.contains("start") ? tag.getLong("start") : -1;
        data.deaths = tag.getInt("deaths");
        data.keys = tag.getInt("keys");
        ListTag chests = tag.getList("chests", Tag.TAG_COMPOUND);
        for (int i = 0; i < chests.size(); i++) {
            CompoundTag c = chests.getCompound(i);
            data.chestClaims.computeIfAbsent(c.getLong("pos"), k -> new HashSet<>()).add(c.getUUID("player"));
        }
        ListTag pushes = tag.getList("pushes", Tag.TAG_COMPOUND);
        for (int i = 0; i < pushes.size(); i++) data.pushHome.put(pushes.getCompound(i).getLong("cur"), pushes.getCompound(i).getLong("home"));
        return data;
    }
}
