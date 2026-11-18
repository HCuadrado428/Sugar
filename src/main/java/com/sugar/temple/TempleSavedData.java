package com.sugar.temple;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Guarda todos los templos en data/sugar_temples.dat del overworld. */
public class TempleSavedData extends SavedData {
    private static final String NAME = "sugar_temples";
    private final Map<TempleType, TempleData> temples = new EnumMap<>(TempleType.class);

    public static TempleSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TempleSavedData::load, TempleSavedData::new, NAME);
    }

    public static TempleSavedData load(CompoundTag tag) {
        TempleSavedData data = new TempleSavedData();
        ListTag list = tag.getList("temples", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            TempleData t = TempleData.load(list.getCompound(i));
            if (t != null) data.temples.put(t.type, t);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        temples.values().forEach(t -> list.add(t.save()));
        tag.put("temples", list);
        return tag;
    }

    public Optional<TempleData> get(TempleType type) {
        return Optional.ofNullable(temples.get(type));
    }

    public Collection<TempleData> all() {
        return temples.values();
    }

    public TempleData define(TempleType type, ResourceKey<Level> dim, BoundingBox zone, BlockPos lobby) {
        TempleData data = new TempleData(type, dim, zone, lobby);
        TempleData old = temples.get(type);
        if (old != null) {
            data.active = old.active;
            data.bossId = old.bossId;
        }
        temples.put(type, data);
        setDirty();
        return data;
    }

    public void remove(TempleType type) {
        temples.remove(type);
        setDirty();
    }

    public Optional<TempleData> findAt(ResourceKey<Level> dim, BlockPos pos) {
        for (TempleData t : temples.values()) {
            if (t.contains(dim, pos)) return Optional.of(t);
        }
        return Optional.empty();
    }
}
