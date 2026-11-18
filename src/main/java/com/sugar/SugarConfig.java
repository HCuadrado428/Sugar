package com.sugar;

import net.minecraftforge.common.ForgeConfigSpec;

/** Configuración en config/sugar-common.toml */
public final class SugarConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue HEALTH_PER_PLAYER;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_PER_PLAYER;
    public static final ForgeConfigSpec.IntValue MAX_SCALING_PLAYERS;
    public static final ForgeConfigSpec.IntValue SCALING_RADIUS;
    public static final ForgeConfigSpec.DoubleValue MIN_DAMAGE_SHARE;
    public static final ForgeConfigSpec.BooleanValue PRESENCE_COUNTS;
    public static final ForgeConfigSpec.IntValue MVP_COUNT;
    public static final ForgeConfigSpec.BooleanValue RESPAWN_AT_CHECKPOINT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Escalado de los bosses según los jugadores presentes").push("escalado");
        HEALTH_PER_PLAYER = b
                .comment("Vida efectiva extra por cada jugador después del primero (0.5 = +50% de la vida base por jugador)")
                .defineInRange("vidaPorJugador", 0.5, 0.0, 5.0);
        DAMAGE_PER_PLAYER = b
                .comment("Daño extra del boss por cada jugador después del primero (0.02 = +2%)")
                .defineInRange("danoPorJugador", 0.02, 0.0, 1.0);
        MAX_SCALING_PLAYERS = b
                .comment("A partir de este número de jugadores el boss deja de escalar")
                .defineInRange("maxJugadoresEscalado", 60, 1, 500);
        SCALING_RADIUS = b
                .comment("Radio para contar jugadores si el boss no está ligado a un templo")
                .defineInRange("radioEscalado", 64, 8, 256);
        b.pop();

        b.comment("Reparto de recompensas").push("recompensas");
        MIN_DAMAGE_SHARE = b
                .comment("Porcentaje mínimo del daño total para contar como participante (0.01 = 1%)")
                .defineInRange("minimoDano", 0.01, 0.0, 1.0);
        PRESENCE_COUNTS = b
                .comment("Si es true, todos los jugadores vivos dentro del templo al morir el boss reciben recompensa")
                .define("presenciaCuenta", true);
        MVP_COUNT = b
                .comment("Cuántos jugadores con más daño reciben la recompensa MVP")
                .defineInRange("numeroMvp", 3, 0, 20);
        b.pop();

        b.comment("Eventos").push("eventos");
        RESPAWN_AT_CHECKPOINT = b
                .comment("Reaparecer en el último checkpoint mientras el evento está activo")
                .define("reaparecerEnCheckpoint", true);
        b.pop();

        SPEC = b.build();
    }

    private SugarConfig() {}
}
