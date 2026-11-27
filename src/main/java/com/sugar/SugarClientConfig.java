package com.sugar;

import net.minecraftforge.common.ForgeConfigSpec;

/** Configuración de cada jugador en config/sugar-client.toml */
public final class SugarClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue DISABLE_SHADERS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Shaders (Oculus)").push("shaders");
        DISABLE_SHADERS = b
                .comment("Si es true, los shaders se apagan solos cuando empieza un evento de templo y se vuelven a encender al terminar")
                .define("apagarShadersEnEventos", true);
        b.pop();
        SPEC = b.build();
    }

    private SugarClientConfig() {}
}
