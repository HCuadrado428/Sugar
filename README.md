# Sugar

Mod de bosses de evento para SMP (Forge 1.20.1 + GeckoLib). Tres templos con recorrido, puzles y un boss final, pensados para 20–50 jugadores.

| Templo (id) | Boss | Estado |
|---|---|---|
| Corazón del Volcán (`volcan`) | Sierpe de Magma | Fase 2 |
| Las Fauces de Ceniza (`ceniza`) | Gran Bestia de Ceniza | Fase 3 |
| Santuario del Eco (`eco`) | Tamborilero Sin Cuerpo | Fase 4 |

## Descargar el .jar

Cada cambio se compila solo en **Actions** → último build → artefacto `sugar-jar`. Usa el `.jar` que **no** termina en `-sources`. Necesita [GeckoLib 4](https://modrinth.com/mod/geckolib) (Forge 1.20.1) en servidor y clientes.

## Bloques (pestaña creativa "Sugar")

Irrompibles en supervivencia; los admins los colocan y quitan en creativo.

- **Piedra de Control**: checkpoint. Al pisarla o hacer clic derecho durante el evento, el jugador reaparece ahí si muere.
- **Puerta de Evento**: bloque sólido que desaparece al abrirse. Tiene canal 0–15 (shift + clic derecho con la mano vacía en creativo para cambiarlo). El canal 0 es la entrada y se abre con `/event start`.
- **Altar del Jefe**: el boss aparece encima.

## Montar vuestro propio templo

1. Construid el templo y colocad los bloques especiales.
2. Poneos en el punto de reunión y ejecutad `/eventtemple define <templo> <x1 y1 z1> <x2 y2 z2>` con dos esquinas opuestas que cubran todo el templo. Vuestra posición queda como vestíbulo.
3. Si añadís bloques con WorldEdit u otras herramientas, ejecutad `/eventtemple scan <templo>`.

## Comandos

Jugadores:
- `/event join <templo>`: ir al evento activo (al checkpoint si ya tienes uno).

Admins (nivel OP 2):
- `/event start|stop|reset <templo>`
- `/event door <templo> <canal> open|close`
- `/eventtemple define|place|scan|setlobby|info|remove <templo>`
- `/eventboss summon|kill|info <templo>`

`/eventboss kill` quita el boss sin dar recompensas. `/event reset` cierra puertas, restaura bloques, quita el boss y borra los checkpoints.

## Escalado y recompensas

Configurable en `config/sugar-common.toml`:
- Vida efectiva +8% y daño +2% por jugador dentro del templo (se recalcula cada 10 s).
- Reciben recompensa quienes hagan al menos el 1% del daño total y, opcionalmente, todos los que estén vivos en el templo.
- Los 3 que más daño hacen reciben además la recompensa MVP.
- Tablas de botín: `data/sugar/loot_tables/bosses/<boss>_participante.json` y `<boss>_mvp.json` (se pueden cambiar con un datapack).

## Estilo

Texturas de 16×16 al estilo vanilla.
