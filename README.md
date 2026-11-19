# Sugar

Mod de bosses de evento para SMP (Forge 1.20.1 + GeckoLib). Tres templos con recorrido, puzles y un boss final, pensados para 20–50 jugadores.

| Templo (id) | Boss | Estado |
|---|---|---|
| Corazón del Volcán (`volcan`) | Sierpe de Magma | ✅ Listo para probar |
| Las Fauces de Ceniza (`ceniza`) | Gran Bestia de Ceniza | ✅ Listo para probar |
| Santuario del Eco (`eco`) | El Tamborilero | ✅ Listo para probar |

## Descargar el .jar

Cada cambio se compila y se prueba en un servidor automático en **Actions** → último build → artefacto `sugar-jar`. Usa el `.jar` que **no** termina en `-sources`. Necesita [GeckoLib 4](https://modrinth.com/mod/geckolib) (Forge 1.20.1) en servidor y clientes.

## Bloques (pestaña creativa "Sugar")

Irrompibles en supervivencia; los admins los colocan y quitan en creativo.

- **Piedra de Control**: checkpoint. Al pisarla o hacer clic derecho durante el evento, el jugador reaparece ahí si muere.
- **Puerta de Evento**: bloque sólido que desaparece al abrirse. Tiene canal 0–15 (shift + clic derecho con la mano vacía en creativo para cambiarlo). El canal 0 es la entrada y se abre con `/event start`.
- **Altar del Jefe**: el boss aparece encima.

Bloques de mecánicas (sirven en cualquier templo):

- **Basalto Inestable**: tiembla al pisarlo, se derrumba a los 0,6 s y vuelve a los 5 s.
- **Fuente de Magma**: hace subir magma por encima (9x9, hasta 20 bloques), espera y baja, en bucle mientras el evento está activo. Ajustable con `/data merge block` (`HalfSize`, `MaxRise`, `RiseInterval`, `HoldTicks`, `DrainInterval`, `IdleTicks`).
- **Magma del Templo**: quema y frena, pero no fluye ni destruye los objetos de quien muere.
- **Grieta del Techo**: en el techo; durante el evento deja caer rocas sobre quien pasa por debajo. La sombra avisa.
- **Sello de Obsidiana**: se enciende 10 s al hacer clic derecho. Si todos los sellos del mismo canal están encendidos a la vez, se abren las puertas de ese canal. Canal con shift + clic derecho en creativo.
- **Cofre de Suministros**: cada jugador recoge un lote por evento (`data/sugar/loot_tables/supplies/<templo>.json`).
- **Respiradero de Magma**: agujero por donde asoma la Sierpe. Quema al pisarlo.

## Evento: Corazón del Volcán

Templo por defecto: `/eventtemple place volcan` construye todo hacia el este (+X) desde tu posición (~130 × 60 bloques). Mejor en una zona llana y despejada.

1. **Vestíbulo** → puerta canal 0 (se abre con `/event start volcan`).
2. **Pasarela de lava**: dos carriles de basalto inestable sobre magma, con rocas cayendo del techo.
3. **Sala de sellos**: 3 sellos (suelo, media altura y arriba, subiendo por plataformas) → abren la puerta canal 1.
4. **Pozo de ascenso**: espiral de plataformas mientras el magma sube y baja.
5. **Antesala**: checkpoint y 4 cofres (martillo, comida, manzana dorada, flechas). Puerta canal 2: la abre el admin cuando todos estén listos.
6. **Cráter**: `/eventboss summon volcan`.

**Sierpe de Magma**: asoma por un respiradero y solo la aturde el **Martillo de Obsidiana** (hacen falta más golpes cuantos más jugadores haya). Aturdida recibe daño durante 8 s; luego vuela en círculos lanzando magma (vulnerable a flechas) y vuelve a hundirse. Con 10+ jugadores salen cabezas señuelo: tienen los ojos apagados y explotan si las golpeas. Por debajo del 40% de vida, la mitad de los respiraderos entran en erupción y caen más rocas.

Orden típico de un evento:
```
/event start volcan
/event door volcan 2 open      (cuando el grupo esté en la antesala)
/eventboss summon volcan
/event reset volcan            (al terminar)
```

## Evento: Las Fauces de Ceniza

Templo por defecto: `/eventtemple place ceniza` (~125 × 50 bloques hacia el este).

1. **Vestíbulo** → puerta canal 0 (`/event start ceniza`).
2. **Paso de los géiseres**: pasarela sobre magma con filas de géiseres que se alternan y un tramo de basalto inestable.
3. **Galería de estatuas**: tres muros de Estatuas Agrietadas; solo las rompen las Bombas de Ceniza (las dan las Matas de Pólvora, que rebrotan a los 10 s). Hay géiseres entre muro y muro.
4. **Antesala**: checkpoint y 4 cofres (4 bombas, comida, manzana dorada, flechas). Puerta canal 2 para el admin.
5. **Caverna**: cúpula con foso de lava junto a la pared, 6 matas de pólvora y 6 grietas. `/eventboss summon ceniza`.

**Gran Bestia de Ceniza**: acecha a los jugadores y da pisotones (saltar los esquiva). Su coraza solo deja pasar el 20% del daño. Cada pocos segundos:
- **Inhala** (3 s, avisa en pantalla): aspira bombas y ceniza. Si se traga suficientes Bombas de Ceniza (1 + 1 por cada 12 jugadores) queda **aturdida 8 s** y recibe todo el daño. Si no, escupe un abanico de fuego.
- **Rueda** hecha una bola: embiste rebotando en las paredes y lanza a la gente hacia el foso. Es invulnerable mientras rueda.
- **Ruge** e invoca Crías de Ceniza desde las grietas (máximo 12).

Por debajo del 40% de vida, las grietas entran en erupción y rueda más a menudo.

Bloques nuevos: **Mata de Pólvora**, **Estatua Agrietada**, **Géiser de Fuego** (fase 0-3 con shift + clic derecho en creativo; cada fase retrasa la erupción 1 s).

## Evento: Santuario del Eco

Templo por defecto: `/eventtemple place eco` (~140 × 52 bloques hacia el este).

1. **Vestíbulo**: 6 **Atriles de la Lente**. Cada atril da UNA **Lente del Eco** por evento; llevarla en una mano deja ver lo oculto. Puerta canal 0 (`/event start eco`).
2. **Puente invisible**: un abismo cruzado por **Plataformas Ocultas** que solo ve quien lleva la lente; esa persona guía al resto. Si caes, hay agua abajo y una escalera para volver al principio. Descanso con checkpoint a mitad de camino.
3. **Sala de los Tambores**: tocad el **Gong** y los tambores tocarán un ritmo; repetidlo golpeando los **Tambores del Eco** en el mismo orden (cada nota está en dos tambores, uno a cada lado). Tres rondas (3, 4 y 5 golpes). Un fallo reinicia el ritmo. Al terminar se abre la puerta canal 1.
4. **Antesala**: checkpoint, 4 cofres y 2 atriles más. Puerta canal 2 para el admin.
5. **Gran Tambor**: arena circular sobre el abismo (si caes, agua y escalera hasta el puente). `/eventboss summon eco`.

**El Tamborilero**: un torso fantasma sin cabeza (costillas de hueso, hombreras de deepslate y fuego de alma saliendo del cuello) que flota invisible sobre el tambor (solo se ve con la lente), y dos enormes manos de piedra.
- **Presentación**: todo se oscurece y suenan tambores cada vez más rápido; las manos trepan desde el abismo y golpean el tambor, el torso sube encorvado, se yergue de golpe, prende el fuego del cuello, se ríe sacudiendo los hombros y se desvanece.
- **Golpe**: una mano sube sobre alguien; su sombra marca dónde caerá. Después se queda unos segundos en el suelo.
- **Redoble**: las manos golpean el tambor por turnos y todo el que esté apoyado sale botando. Saltad justo antes del golpe.
- **Agarre**: atrapa a un jugador, lo aprieta y lo lanza. Si los demás golpean la mano, lo suelta.
- **Barrido**: arrastra la mano a ras de suelo en arco; saltad por encima.
- **Palmada** (por debajo del 40% de vida): las dos manos se cierran de golpe sobre alguien.

Golpear una mano la va aturdiendo (la barra sale en pantalla); al llenarse cae al suelo unos 7 s. **Si las dos manos están en el suelo a la vez, el torso se desploma, las costillas se abren como puertas y el corazón queda a la vista y recibe todo el daño** durante unos 8 s; luego se sacude, se ríe y vuelve a esconderse. Oculta solo recibe el 25% del daño (flechas de quien lleve la lente).

Bloques nuevos: **Tambor del Eco** (nota 0-3 con shift + clic derecho en creativo), **Gong del Eco**, **Atril de la Lente**, **Plataforma Oculta** (en creativo se ve siempre).

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
- Vida efectiva +50% de la base y daño +2% por cada jugador dentro del templo (se recalcula cada 10 s).
- Reciben recompensa quienes hagan al menos el 1% del daño total y, opcionalmente, todos los que estén vivos en el templo.
- Los 3 que más daño hacen reciben además la recompensa MVP.
- Tablas de botín: `data/sugar/loot_tables/bosses/<boss>_participante.json` y `<boss>_mvp.json` (se pueden cambiar con un datapack).

## Estilo

Texturas de 16×16 al estilo vanilla.
