package com.sugar.temple;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Textos de historia de los templos. Se cuentan en el chat, en cursiva, como un narrador:
 * sin títulos, contadores ni avisos de ayuda.
 */
public final class TempleStory {

    /** Narra una línea a todos los que están dentro del templo. */
    public static void narrate(ServerLevel level, TempleData data, String text) {
        Component line = Component.literal(text).withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY);
        for (ServerPlayer p : TempleService.playersInside(level, data)) p.sendSystemMessage(line);
    }

    /** Narra solo a un jugador (por ejemplo, el que encuentra una reliquia). */
    public static void narrate(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.ITALIC, color));
    }

    public static String opening(TempleType t) {
        return switch (t) {
            case VOLCAN -> "La empalizada del campamento se abre. Arriba, el volcán humea. El templo está en el cráter: hay que subir.";
            case CENIZA -> "Las puertas se abren. Huele a pólvora, y la ceniza cae como nieve sucia.";
            case ECO -> "Las puertas se abren sin un ruido. Al otro lado, alguien toca un tambor muy lejos.";
        };
    }

    /** Lo que se cuenta al cumplir cada paso del templo (por canal). Null si no hay nada que contar. */
    public static String step(TempleType t, int channel) {
        return switch (t) {
            case VOLCAN -> switch (channel) {
                case TempleObjectives.V_FORGE -> "El Capataz se derrumba sobre su yunque. Al fondo de la Forja, la reja del Gran Cofre se levanta.";
                case TempleObjectives.V_SEALS -> "Los tres sellos arden a la vez y la jaula del centro de la sala se abre.";
                case TempleObjectives.V_BRIDGE -> "Las placas se hunden bajo vuestro peso. Muy por debajo, el lago de la Caldera empieza a vaciarse...";
                case TempleObjectives.V_SWITCHES -> "Los seis interruptores retumban a la vez. Algo pesado se desliza en la sala.";
                case TempleObjectives.V_BOSS_DOOR -> "El cerrojo gira. Detrás de la puerta, un túnel baja hacia un calor que no es de este mundo.";
                case TempleObjectives.BELL -> "La puerta se cierra a vuestra espalda. En el centro del Corazón del Volcán, la roca empieza a hervir...";
                default -> null;
            };
            case CENIZA -> switch (channel) {
                case 1 -> "El sello cede y la puerta se abre. Al otro lado se oyen chillidos.";
                case 2 -> "El nido queda en silencio. El aire sabe a ceniza.";
                case 3 -> "Las cuatro placas ceden a la vez y los géiseres se calman.";
                case TempleObjectives.BELL -> "La campana resuena en la caverna. La montaña empieza a respirar...";
                default -> null;
            };
            case ECO -> switch (channel) {
                case 1 -> "Los tambores callan. El eco de vuestro ritmo abre la cripta.";
                case 2 -> "Los muertos de la cripta vuelven a dormir.";
                case 3 -> "Las placas suenan como un tambor lejano y el camino se abre.";
                case TempleObjectives.BELL -> "La campana suena... y en algún sitio, alguien empieza a tocar.";
                default -> null;
            };
        };
    }

    public static String ending(TempleType t) {
        return switch (t) {
            case VOLCAN -> "La Sierpe se deshace en roca y lava. Por primera vez en siglos, el volcán descansa.";
            case CENIZA -> "La Gran Bestia se desploma y deja de caer ceniza. La montaña vuelve a estar en silencio.";
            case ECO -> "El último latido se apaga. En el Santuario ya no suena ningún tambor.";
        };
    }

    public static String rumour(TempleType t) {
        return switch (t) {
            case VOLCAN -> "Se cuenta que la Sierpe de Magma ha caído en el Corazón del Volcán.";
            case CENIZA -> "Se cuenta que la Gran Bestia de Ceniza ha caído en sus propias fauces.";
            case ECO -> "Se cuenta que El Tamborilero ha dejado de tocar en el Santuario del Eco.";
        };
    }

    public static String waveStart(TempleType t) {
        return switch (t) {
            case VOLCAN -> "La reja de la Forja cae a vuestra espalda. El altar se enciende y las paredes empiezan a escupir criaturas.";
            case CENIZA -> "El altar se agrieta. Del suelo del nido salen cosas que estaban esperando.";
            case ECO -> "El altar suena como un tambor. La cripta despierta.";
        };
    }

    public static String waveNext(TempleType t) {
        return switch (t) {
            case VOLCAN -> "El fuego del altar vuelve a avivarse...";
            case CENIZA -> "Algo más se arrastra por las grietas...";
            case ECO -> "El tambor vuelve a sonar...";
        };
    }

    /** Fragmentos de la historia del jefe: uno por reliquia, en orden. */
    public static String relic(TempleType t, int index) {
        String[] lore = switch (t) {
            case VOLCAN -> new String[]{
                    "«Los herreros del volcán alimentaban su forja con el aliento de una sierpe dormida...»",
                    "«...hasta que un día le pidieron más fuego del que ella quería dar.»",
                    "«Su capataz se negó a huir. Dicen que sigue en la forja, golpeando el mismo yunque.»"};
            case CENIZA -> new String[]{
                    "«La bestia no nació monstruo: guardaba las minas de pólvora de la montaña.»",
                    "«Los mineros la alimentaban con ceniza para que no tuviera hambre de ellos.»",
                    "«Cuando se acabó la ceniza, se tragó la montaña entera.»"};
            case ECO -> new String[]{
                    "«El Tamborilero tocaba para que los muertos durmieran tranquilos.»",
                    "«Una noche no quiso parar, y tocó hasta perder la cabeza.»",
                    "«Desde entonces su corazón lleva el compás. Si se detiene, despierta.»"};
        };
        return lore[Math.max(0, Math.min(lore.length - 1, index))];
    }

    /** Al abrir un cofre con llave (n = llaves pequeñas encontradas). */
    public static String smallKey(TempleType t, int n) {
        return "Dentro del cofre hay una llave pequeña, caliente todavía. Es la " + (n <= 1 ? "primera" : n == 2 ? "segunda" : n == 3 ? "tercera" : "cuarta") + " que encontráis.";
    }

    public static String unlocked(TempleType t) {
        return "La cerradura cede con un chasquido y la puerta se abre.";
    }

    public static String bossDoor(TempleType t) {
        return "La Llave del Jefe encaja en el cerrojo. Todo el templo parece contener la respiración.";
    }

    /** Primer Gran Cofre abierto (kind: 0 martillo, 1 brújula, 2 Llave del Jefe). */
    public static String bigChest(TempleType t, int kind, String who) {
        return switch (kind) {
            case 0 -> who + " abre el Gran Cofre: dentro hay martillos de obsidiana, uno para cada uno. Pesan como una montaña.";
            case 1 -> who + " abre el Gran Cofre: una brújula antigua. Su aguja no apunta al norte, sino hacia el fondo del templo.";
            default -> who + " abre el Gran Cofre y levanta la Llave del Jefe. Es enorme, y quema.";
        };
    }

    public static String foreman() {
        return "Del fondo de la Forja se levanta el Capataz, con el martillo todavía al rojo.";
    }

    public static String vault(TempleType t) {
        return "Con la última reliquia se oye un mecanismo antiguo, cerca de la antesala.";
    }

    private TempleStory() {}
}
