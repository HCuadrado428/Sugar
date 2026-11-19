package com.sugar.temple;

import java.util.List;

/**
 * Objetivos de cada templo, en orden. Cada uno se cumple cuando se abren las puertas de su canal
 * (el canal 15 es derrotar al jefe). El canal 8 es la cámara del tesoro (opcional, no cuenta).
 */
public final class TempleObjectives {
    public static final int VAULT = 8, BELL = 9, BOSS = 15;

    public record Objective(int channel, String name) {}

    public static List<Objective> of(TempleType type) {
        return switch (type) {
            case VOLCAN -> List.of(
                    new Objective(1, "Encender los tres sellos"),
                    new Objective(2, "Resistir en la Forja"),
                    new Objective(3, "Ocupar las placas del Puente de Brasas"),
                    new Objective(BELL, "Tocar la campana del jefe"),
                    new Objective(BOSS, "Derrotar a la Sierpe de Magma"));
            case CENIZA -> List.of(
                    new Objective(1, "Abrirse paso por la Galería de estatuas"),
                    new Objective(2, "Resistir en el Nido de crías"),
                    new Objective(3, "Ocupar las placas de la Sala de los Géiseres"),
                    new Objective(BELL, "Tocar la campana del jefe"),
                    new Objective(BOSS, "Derrotar a la Gran Bestia de Ceniza"));
            case ECO -> List.of(
                    new Objective(1, "Repetir el ritmo de los tambores"),
                    new Objective(2, "Resistir en la Cripta del Eco"),
                    new Objective(3, "Ocupar las placas sobre el abismo"),
                    new Objective(BELL, "Tocar la campana del jefe"),
                    new Objective(BOSS, "Derrotar a El Tamborilero"));
        };
    }

    public static Objective find(TempleType type, int channel) {
        for (Objective o : of(type)) if (o.channel() == channel) return o;
        return null;
    }

    public static int done(TempleData data) {
        int n = 0;
        for (Objective o : of(data.type)) if (data.isDone(o.channel())) n++;
        return n;
    }

    /** El primer objetivo sin cumplir, o null si el templo está completo. */
    public static Objective current(TempleData data) {
        for (Objective o : of(data.type)) if (!data.isDone(o.channel())) return o;
        return null;
    }

    private TempleObjectives() {}
}
