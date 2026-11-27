package com.sugar.temple;

import com.sugar.net.SugarNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lado del servidor del control de shaders. Avisa al que entra a prepararse, apaga los shaders
 * de los que están dentro al empezar (y de los que lleguen tarde) y los devuelve al terminar.
 * El cliente decide qué hacer (si no tiene Oculus o lo ha desactivado en su config, nada).
 */
public final class ShaderControl {
    private static final Map<TempleType, Set<UUID>> WARNED = new EnumMap<>(TempleType.class);
    private static final Map<TempleType, Set<UUID>> OFF = new EnumMap<>(TempleType.class);

    private ShaderControl() {}

    private static Set<UUID> set(Map<TempleType, Set<UUID>> map, TempleData data) {
        return map.computeIfAbsent(data.type, t -> new HashSet<>());
    }

    public static void warn(ServerPlayer p, TempleData data) {
        if (set(WARNED, data).add(p.getUUID())) SugarNetwork.shaders(p, 0);
    }

    public static void off(ServerPlayer p, TempleData data) {
        if (set(OFF, data).add(p.getUUID())) SugarNetwork.shaders(p, 1);
    }

    /** Cada 2 s: avisa a los que entran mientras está abierto y apaga a los que llegan con el evento empezado. */
    public static void tick(ServerLevel level, TempleData data) {
        if (data.isDone(TempleObjectives.BOSS)) return;
        for (ServerPlayer p : TempleService.playersInside(level, data)) {
            if (data.active) off(p, data); else if (data.open) warn(p, data);
        }
    }

    public static void start(ServerLevel level, TempleData data) {
        for (ServerPlayer p : TempleService.playersInside(level, data)) off(p, data);
    }

    /** Fin del evento (jefe vencido, /event stop o /event reset): enciende los shaders que apagamos. */
    public static void restore(net.minecraft.server.MinecraftServer server, TempleData data) {
        for (UUID id : set(OFF, data)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) SugarNetwork.shaders(p, 2);
        }
        OFF.remove(data.type);
        WARNED.remove(data.type);
    }
}
