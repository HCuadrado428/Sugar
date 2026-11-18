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
    @Nullable public UUID bossId;
    public final Map<UUID, BlockPos> checkpoints = new HashMap<>();
    public final Set<BlockPos> blocks = new HashSet<>();

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
        if (tag.hasUUID("boss")) data.bossId = tag.getUUID("boss");

        ListTag cps = tag.getList("checkpoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < cps.size(); i++) {
            CompoundTag c = cps.getCompound(i);
            data.checkpoints.put(c.getUUID("player"), NbtUtils.readBlockPos(c.getCompound("pos")));
        }
        for (long l : tag.getLongArray("blocks")) {
            data.blocks.add(BlockPos.of(l));
        }
        return data;
    }
}
