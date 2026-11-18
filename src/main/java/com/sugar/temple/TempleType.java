package com.sugar.temple;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/** Los tres templos de evento. El id es el que se escribe en los comandos. */
public enum TempleType {
    VOLCAN("volcan", "Corazón del Volcán", "Sierpe de Magma", ChatFormatting.GOLD),
    CENIZA("ceniza", "Las Fauces de Ceniza", "Gran Bestia de Ceniza", ChatFormatting.RED),
    ECO("eco", "Santuario del Eco", "Tamborilero Sin Cuerpo", ChatFormatting.DARK_PURPLE);

    private final String id;
    private final String templeName;
    private final String bossName;
    private final ChatFormatting color;

    TempleType(String id, String templeName, String bossName, ChatFormatting color) {
        this.id = id;
        this.templeName = templeName;
        this.bossName = bossName;
        this.color = color;
    }

    public String id() { return id; }

    public ChatFormatting color() { return color; }

    public Component displayName() {
        return Component.literal(templeName).withStyle(color);
    }

    public Component bossDisplayName() {
        return Component.literal(bossName).withStyle(color);
    }

    @Nullable
    public static TempleType byId(String id) {
        for (TempleType t : values()) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return null;
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(TempleType::id).toList();
    }
}
