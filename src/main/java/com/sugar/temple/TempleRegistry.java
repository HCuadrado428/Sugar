package com.sugar.temple;

import com.sugar.boss.SugarBoss;
import net.minecraft.world.entity.EntityType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Asocia cada templo con su boss y su generador. Se rellena a medida que se añaden los bosses. */
public final class TempleRegistry {
    private static final Map<TempleType, Supplier<? extends EntityType<? extends SugarBoss>>> BOSSES = new EnumMap<>(TempleType.class);
    private static final Map<TempleType, TempleGenerator> GENERATORS = new EnumMap<>(TempleType.class);

    public static void registerBoss(TempleType type, Supplier<? extends EntityType<? extends SugarBoss>> boss) {
        BOSSES.put(type, boss);
    }

    public static void registerGenerator(TempleType type, TempleGenerator generator) {
        GENERATORS.put(type, generator);
    }

    public static Optional<EntityType<? extends SugarBoss>> boss(TempleType type) {
        Supplier<? extends EntityType<? extends SugarBoss>> s = BOSSES.get(type);
        return s == null ? Optional.empty() : Optional.of(s.get());
    }

    public static Optional<TempleGenerator> generator(TempleType type) {
        return Optional.ofNullable(GENERATORS.get(type));
    }

    private TempleRegistry() {}
}
