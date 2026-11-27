package com.sugar.client;

import com.sugar.SugarClientConfig;
import com.sugar.SugarMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;

/**
 * Habla con Oculus (la API de Iris) sin depender de él: si no está instalado no hace nada.
 * - Aviso: al unirse a un templo para prepararse, si lleva shaders se le avisa de que se apagarán.
 * - Apagar: al empezar el evento se apagan, recordando que los apagamos nosotros.
 * - Restaurar: al terminar el evento (o al salir del servidor) se vuelven a encender, solo si los apagamos nosotros.
 */
@Mod.EventBusSubscriber(modid = SugarMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShaderCompat {
    public static final int WARN = 0, OFF = 1, RESTORE = 2;
    private static boolean turnedOff;

    private ShaderCompat() {}

    public static void handle(int mode) {
        Boolean on = shadersOn();
        boolean auto = SugarClientConfig.DISABLE_SHADERS.get();
        switch (mode) {
            case WARN -> {
                if (on == null || !on) return;
                say(auto ? "⚠ Los templos se ven mejor sin shaders: se apagarán solos al empezar el evento y volverán al terminar."
                         : "⚠ Los templos se ven mejor sin shaders. Puedes quitarlos con la tecla K.", ChatFormatting.GOLD);
            }
            case OFF -> {
                if (on == null || !on || !auto) return;
                if (setShaders(false)) {
                    turnedOff = true;
                    say("Shaders desactivados durante el evento. Se volverán a activar al terminar.", ChatFormatting.GRAY);
                }
            }
            case RESTORE -> restore();
            default -> {}
        }
    }

    private static void restore() {
        if (!turnedOff) return;
        turnedOff = false;
        if (setShaders(true)) say("Shaders activados de nuevo.", ChatFormatting.GRAY);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        restore();
    }

    private static void say(String text, ChatFormatting color) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal(text).withStyle(color), false);
    }

    // ---------- Oculus / Iris API v0, por reflexión ----------

    private static Object config() throws ReflectiveOperationException {
        Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
        Object instance = api.getMethod("getInstance").invoke(null);
        return api.getMethod("getConfig").invoke(instance);
    }

    /** null si no hay Oculus o su API no responde. */
    private static Boolean shadersOn() {
        try {
            Object cfg = config();
            Method m = Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig").getMethod("areShadersEnabled");
            return (Boolean) m.invoke(cfg);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean setShaders(boolean enabled) {
        try {
            Object cfg = config();
            Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig").getMethod("setShadersEnabledAndApply", boolean.class).invoke(cfg, enabled);
            return true;
        } catch (Throwable t) {
            SugarMod.LOGGER.warn("No se pudieron cambiar los shaders de Oculus", t);
            return false;
        }
    }
}
