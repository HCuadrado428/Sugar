# Sugar

Mod de bosses de evento para SMP (Forge 1.20.1 + GeckoLib). Tres templos con recorrido, puzles y un boss final, pensados para 25–50 jugadores.

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
- **Puerta de Evento**: bloque sólido que desaparece al abrirse. Tiene canal 0–15 (shift + clic derecho con la mano vacía en creativo para cambiarlo). El canal 10 es la entrada de fuera (`/event open`) y el 0 la del vestíbulo (`/event start`).
- **Altar del Jefe**: el boss aparece encima.

Bloques de mecánicas (sirven en cualquier templo):

- **Basalto Inestable**: tiembla al pisarlo, se derrumba a los 0,6 s y vuelve a los 5 s.
- **Fuente de Magma**: hace subir magma por encima (9x9, hasta 20 bloques), espera y baja, en bucle mientras el evento está activo. Ajustable con `/data merge block` (`HalfSize`, `MaxRise`, `RiseInterval`, `HoldTicks`, `DrainInterval`, `IdleTicks`).
- **Magma del Templo**: quema y frena, pero no fluye ni destruye los objetos de quien muere.
- **Grieta del Techo**: en el techo; durante el evento deja caer rocas sobre quien pasa por debajo. La sombra avisa.
- **Sello de Obsidiana**: se enciende 10 s al hacer clic derecho. Si todos los sellos del mismo canal están encendidos a la vez, se abren las puertas de ese canal. Canal con shift + clic derecho en creativo.
- **Cofre de Suministros**: cada jugador recoge un lote por evento (`data/sugar/loot_tables/supplies/<templo>.json`).
- **Respiradero de Magma**: agujero por donde asoma la Sierpe. Quema al pisarlo.

Puzles estilo Zelda (canal con shift + clic derecho en creativo, como las puertas):

- **Bloque Empujable**: clic derecho lo empuja un bloque en la dirección en que miras (solo si hay suelo y hueco). Al reiniciar el templo vuelve a su sitio.
- **Baldosa Destino**: se ilumina con un Bloque Empujable encima. Cuando todas las del canal lo están, se abren sus puertas.
- **Brasero del Templo**: se enciende con antorcha, mechero, carga ígnea, vara de blaze, flecha en llamas o Bomba de Ceniza. Se apaga a los 20 s; si todos los del canal arden a la vez, abre el canal.
- **Ojo de Piedra**: solo se activa con un proyectil (flecha, tridente…). Se cierra a los 10 s; todos los del canal abiertos a la vez abren las puertas.
- **Plataforma Intermitente**: aparece y desaparece cada 3 s. Las de fase 0 y 1 se alternan.
- **Nido de Guardianes** (invisible): al acercarse el primer jugador suelta Keese (de fuego, de ceniza o del eco) o los guardias del templo.

## Evento: Corazón del Volcán

Templo al estilo Ocarina of Time, pensado para 25–50 jugadores. `/eventtemple place volcan` lo construye entero con **tu posición como campamento base**: la montaña queda al norte (unos 180 × 220 bloques y 56 de alto, y la guarida 18 bloques bajo tus pies). Hace falta una zona amplia y despejada; tarda unos segundos en construirse.

**Fuera, la subida.** El campamento base tiene una empalizada con dos puertas: la del sur (canal 10) se abre con `/event open`, la del norte (canal 0) con `/event start`. Desde ahí un sendero de 5 de ancho da dos vueltas a la montaña hasta el cráter: géiseres de fuego, coladas de lava con puentes que se hunden, una cornisa con desprendimientos, un campamento a media subida (checkpoint, cofres y una cueva con la **reliquia 1**) y un mirador arriba. En el fondo del cráter está la fachada del templo.

**Dentro, la Gran Caldera**: un pozo circular con balcones en tres pisos, rampas entre ellos y un lago de magma al fondo, donde ya se ve la Puerta del Jefe. Alrededor:

| Sala | Piso | Para entrar | Qué hay | Da |
|---|---|---|---|---|
| Pasarela de lava | 3 · oeste | — | dos carriles de basalto inestable, rocas del techo | Llave 1 |
| Galería de las Cadenas | 3 · este | — | plataformas colgadas sobre magma, géiseres | Brújula (una por jugador), reliquia 2 |
| La Forja | 2 · sur | — | se cierra al tocar el altar: 3 oleadas y el **Capataz de la Forja** | Martillo de Obsidiana (uno por jugador) |
| Sala de los sellos | 2 · este | 🔒 Llave 1 | 3 sellos a distintas alturas, a la vez | Llave 2 |
| Pozo de ascenso | 1→3 · noroeste | muro agrietado (martillo) | espiral mientras sube el magma | Llave 3 |
| Puente de Brasas | 2 · norte | 🔒 Llaves 2 y 3 | 8 placas: vacían el lago de la Caldera | Llave 4 |
| Sala del Martillo | 1 · este | 🔒 Llave 4 | muros agrietados, géiseres, 6 interruptores de obsidiana a la vez | Llave del Jefe |
| Cámara oculta | 1 · oeste | muro agrietado | reliquia 3 y la puerta del tesoro (canal 8) | tesoro |
| Corazón del Volcán | fondo | Puerta del Jefe (Llave del Jefe y lago vacío) | túnel con checkpoint y la arena (radio 24) | la Sierpe |

Las primeras tres salas se hacen a la vez, así que el grupo puede repartirse. El Puente de Brasas obliga a juntarse: pide una llave de cada rama y mucha gente en las placas. Las llaves son **del grupo** (no van al inventario): cualquiera abre la cerradura y pasan todos. **La Sierpe despierta cuando ha bajado el 60 % del grupo a la guarida**: la Puerta del Jefe se cierra a su espalda y se abre de nuevo al vencerla.

Bloques nuevos: **Cofre con Llave** (llave 1-4), **Gran Cofre** (martillo, brújula o Llave del Jefe), **Puerta con Cerradura** y **Puerta del Jefe** (se abren con las llaves del grupo o con `/event door`), **Muro Agrietado** (un martillazo, golpe o clic derecho, derrumba todo el muro), **Interruptor de Obsidiana** (solo con el martillo, dura 5 s) y **Lago de Magma** (desaparece al abrir su canal). En creativo, shift + clic derecho con la mano vacía cambia la llave, el contenido o el canal. Las **Llaves Pequeña y del Jefe** de la pestaña creativa abren cualquier cerradura con clic derecho (solo admins en creativo).

**Escalado con mucha gente**: monstruos por oleada = 4 + 0,6 × jugadores (+2 por oleada, máx. 36); placas necesarias = 4 hasta 10 jugadores, una más cada 7 (las 8 con 38 o más); vida del Capataz = 200 + 18 × jugadores.

**Capataz de la Forja**: gigante de basalto con un martillo de fragua. Martillazo con onda de fuego (se esquiva saltando), giro con el martillo si le rodeáis y, después del giro, se queda sin aliento unos segundos (recibe más daño). Sin barra de jefe.

**Sierpe de Magma**: asoma por un respiradero y solo la aturde el **Martillo de Obsidiana** (hacen falta más golpes cuantos más jugadores haya). Aturdida recibe daño durante 8 s; luego vuela en círculos lanzando magma (vulnerable a flechas) y vuelve a hundirse. Con 10+ jugadores salen cabezas señuelo: tienen los ojos apagados y explotan si las golpeas.
- **Fase II (66%)**: sale disparada, sube al centro rugiendo y todos los respiraderos escupen fuego. Desde ahora hace **pasadas de fuego**: marca una línea de humo en el suelo, baja en rasante y deja un reguero de llamas (al pasar baja es vulnerable). Además, antes de hundirse da un **golpe de lava**: una onda de fuego que hay que saltar.
- **Fase III (33%)**: se estrella contra el techo, que se derrumba en una lluvia de rocas, y la mitad de los respiraderos entran en erupción.

Orden típico:
```
/event open volcan             (el grupo se reúne en el campamento base)
/event start volcan            (se abre el camino a la montaña)
/event reset volcan            (al terminar)
```

## Evento: Las Fauces de Ceniza

Templo por defecto: `/eventtemple place ceniza`. **Tu posición queda como campamento base** y todo se construye hacia el este (unos 290 × 80 bloques): el templo empieza 100 bloques más allá, dentro de un monte.

**Fuera, el Desfiladero de Ceniza.** El campamento minero tiene dos puertas: la del oeste (canal 10) se abre con `/event open` y la del este (canal 0) con `/event start`. El camino cruza una llanura de ceniza con filas de géiseres y matas de pólvora, entra en un desfiladero cerrado por un muro de estatuas (hay que volarlo con bombas), salta una grieta de lava por un puente que se hunde y llega a **las Fauces**: la boca de piedra de la montaña, que da al vestíbulo. Hay checkpoints en el campamento, antes del desfiladero y después de la grieta.

**Dentro, la Gran Garganta**: un pozo circular con balcones en dos pisos, rampas entre ellos y un lago de lava al fondo, donde está la Puerta del Jefe.

| Sala | Piso | Para entrar | Qué hay | Da |
|---|---|---|---|---|
| Galería de las estatuas | 2 · norte | — | dos muros de estatuas que hay que volar con bombas, matas de pólvora, géiseres | Llave 1, reliquia 1 |
| Nido de crías | 2 · sur | — | se cierra al tocar el altar: 3 oleadas | Bombas (8 por jugador) |
| Sala de los sellos | 2 · este | 🔒 Llave 1 | 3 sellos a distintas alturas, a la vez | Llave 2 |
| Mina abandonada | 1 · norte | muro de estatuas (bombas) | foso de lava con puente que se hunde, desprendimientos | Llave 3, brújula, reliquia 2 |
| Sala de los Géiseres | 1 · este | 🔒 Llaves 2 y 3 | 8 placas entre géiseres: vacían el lago | Llave 4 |
| Polvorín | 1 · sur | 🔒 Llave 4 | 6 interruptores de obsidiana que se encienden con bombas, a la vez | Llave del Jefe |
| Cámara oculta | 1 · oeste | muro de estatuas (bombas) | reliquia 3 y la puerta del tesoro (canal 8) | tesoro |
| Caverna de la Bestia | fondo | Puerta del Jefe (Llave del Jefe y lago vacío) | túnel con checkpoint y la cúpula | la Bestia |

Como en el Volcán, las llaves son del grupo y la Bestia despierta cuando el 60 % del grupo ha bajado a la caverna. Las Matas de Pólvora rebrotan a los 10 s, así que nunca faltan bombas.

**Gran Bestia de Ceniza**: acecha a los jugadores y da pisotones (saltar los esquiva). Su coraza solo deja pasar el 20% del daño. Cada pocos segundos:
- **Inhala** (3 s, avisa en pantalla): aspira bombas y ceniza. Si se traga suficientes Bombas de Ceniza (1 + 1 por cada 12 jugadores) queda **aturdida 8 s** y recibe todo el daño. Si no, escupe un abanico de fuego.
- **Rueda** hecha una bola: embiste rebotando en las paredes y lanza a la gente hacia el foso. Es invulnerable mientras rueda.
- **Ruge** e invoca Crías de Ceniza desde las grietas (máximo 12).

- **Fase II (66%)**: ruge con una onda de ceniza. Sus pisotones abren **grietas de lava**: líneas que se resquebrajan hacia los jugadores y revientan en fuego al poco (apartaos de la línea). Al chocar rodando contra una pared caen rocas del techo.
- **Fase III (33%)**: **tormenta de ceniza**. Al inhalar crea un remolino que arrastra de verdad (y es el mejor momento para meterle una bomba), sus pisotones lanzan ondas que hay que saltar y a veces rueda dos veces seguidas.

Bloques nuevos: **Mata de Pólvora**, **Estatua Agrietada**, **Géiser de Fuego** (fase 0-3 con shift + clic derecho en creativo; cada fase retrasa la erupción 1 s).

## Evento: Santuario del Eco

Templo por defecto: `/eventtemple place eco`. **Tu posición queda como campamento**, arriba, y todo se construye hacia el este (unos 300 × 80 bloques). El santuario está 100 bloques al este y **20 bloques más abajo**, enterrado bajo un monte.

**Fuera, el Barranco de los Ecos.** El campamento tiene dos puertas: oeste (canal 10, `/event open`) y este (canal 0, `/event start`). Un barranco baja 20 bloques haciendo eses: una sima con un puente que se hunde, un cementerio con checkpoint y cofres, la **Puerta de los Ecos** (una reja, canal 13, que se abre con cuatro placas a la vez), arcos de roca que sueltan piedras y, al fondo, la fachada del Santuario.

**Dentro, el Pozo de los Ecos**: una sala central de dos pisos sobre un abismo con agua al fondo (si caes, una escalera sube a la repisa del fondo). Desde la repisa, un camino oculto cruza el abismo hasta la reja y la Puerta del Jefe.

| Sala | Piso | Para entrar | Qué hay | Da |
|---|---|---|---|---|
| Sala de los Tambores | 2 · norte | — | tocad el **Gong** y repetid el ritmo en los **Tambores del Eco** (3 rondas de 3, 4 y 5 golpes; cada nota está en dos tambores) | Llave 1, reliquia 1 |
| Cripta | 2 · sur | — | se cierra al tocar el altar: 3 oleadas | **Lente del Eco** (una por jugador) |
| Puente invisible | 2 · este | 🔒 Llave 1 | abismo con un camino oculto en zigzag y un ramal escondido | Llave 2, reliquia 2 |
| Osario | 1 · norte | — | foso de fuego de alma con un puente oculto, desprendimientos | Llave 3, brújula |
| Placas del abismo | 1 · este | 🔒 Llaves 2 y 3 | 8 placas en islotes unidos por piedras ocultas: abren la reja del fondo | Llave 4 |
| Sala de los Ecos | 1 · sur | 🔒 Llave 4 | laberinto oscuro con 6 sellos, a la vez | Llave del Jefe |
| Cámara oculta | 1 · oeste | un tramo oculto del balcón (con la lente) | reliquia 3 y la puerta del tesoro (canal 8) | tesoro |
| Gran Tambor | fondo | reja (canal 5) y Puerta del Jefe | túnel con checkpoint y la arena sobre el agua | el Tamborilero |

La lente ya no se reparte en atriles: sale del Gran Cofre de la Cripta, una para cada uno. Quien la lleva en la mano ve las **Plataformas Ocultas** y puede guiar al resto. El Tamborilero despierta cuando el 60 % del grupo ha bajado a la arena.

**El Tamborilero**: un torso fantasma sin cabeza (costillas de hueso, hombreras de deepslate y fuego de alma saliendo del cuello) que flota invisible sobre el tambor (solo se ve con la lente), y dos enormes manos de piedra.
- **Presentación**: todo se oscurece y suenan tambores cada vez más rápido; las manos trepan desde el abismo y golpean el tambor, el torso sube encorvado, se yergue de golpe, prende el fuego del cuello, se ríe sacudiendo los hombros y se desvanece.
- **Golpe**: una mano sube sobre alguien; su sombra marca dónde caerá. Después se queda unos segundos en el suelo.
- **Redoble**: las manos golpean el tambor por turnos y todo el que esté apoyado sale botando. Saltad justo antes del golpe.
- **Agarre**: atrapa a un jugador, lo aprieta y lo lanza. Si los demás golpean la mano, lo suelta.
- **Barrido**: arrastra la mano a ras de suelo en arco; saltad por encima.
- **Palmada** (fase III): las dos manos se cierran de golpe sobre alguien.
- **Fase II (66%)**: se hace visible, ruge con las costillas en abanico y desde ahora su corazón **late**: cada latido lanza una onda desde el centro del tambor que hay que saltar.
- **Fase III (33%)**: el ritmo se acelera y aprende el **Grito del Eco**: se hace visible, apunta a alguien con un hilo de almas (que se vuelve fuego de alma al fijar el objetivo) y dispara un rayo sónico. Apartaos de la línea.

Golpear una mano la va aturdiendo (la barra sale en pantalla); al llenarse cae al suelo unos 7 s. **Si las dos manos están en el suelo a la vez, el torso se desploma, las costillas se abren como puertas y el corazón queda a la vista y recibe todo el daño** durante unos 8 s; luego se sacude, se ríe y vuelve a esconderse. Oculta solo recibe el 25% del daño (flechas de quien lleve la lente).

Bloques nuevos: **Tambor del Eco** (nota 0-3 con shift + clic derecho en creativo), **Gong del Eco**, **Plataforma Oculta** (en creativo se ve siempre). El **Atril de la Lente** sigue en la pestaña creativa por si montáis vuestro propio templo.

## Los templos como una historia

Los templos se recorren como una aventura, sin marcadores en pantalla: un narrador cuenta en el chat (en cursiva) lo que pasa al abrir el templo, al superar cada sala, al despertar al jefe y al derrotarlo. Al final corre por el servidor el rumor de que el jefe ha caído.

Salas comunes a los tres templos:
- **Sala de oleadas** (Forja / Nido de crías / Cripta del Eco): al tocar el **Altar de Oleadas** se cierra la reja de la sala (canal 12) y salen 3 oleadas de monstruos del templo, más cuantos más jugadores haya. Al limpiarlas se abre la jaula del Gran Cofre (canal 4) y la reja (en el Volcán, antes sale el Capataz). Si un monstruo se queda atascado mucho rato, vuelve junto al altar.
- **Sala de las placas**: cuatro **Placas de Grupo** en islotes sobre un foso. Hace falta alguien encima de cada una a la vez. En el Santuario del Eco las piedras de salto son plataformas ocultas: solo se ven con la lente.
- **Reliquias**: tres por templo, escondidas en rincones de parkour. Cada una cuenta a quien la encuentra un trozo de la historia del jefe. Con las tres se abre la **cámara del tesoro** junto a la antesala (canal 8), con un **Cofre del Tesoro** por jugador.
- **Campana del Jefe**: ya no la usa ningún templo por defecto (el jefe despierta cuando baja el 60 % del grupo), pero sigue disponible para templos propios: solo suena si la mayoría del grupo está allí. Abre la arena (canal 9), el suelo late unos segundos y el jefe despierta con su presentación. El admin puede seguir usando `/eventboss summon`.

`/eventtemple info <templo>` muestra a los admins los pasos cumplidos, el tiempo y las caídas del grupo.

Canales de puertas de los tres templos por defecto: 10 entrada del campamento (`/event open`), 0 camino y vestíbulo (`/event start`), 1-3 cerraduras, 4 jaula del Gran Cofre de la sala de oleadas, 5 placas (lago o reja del fondo), 6 interruptores / sellos de la Llave del Jefe, 7 jaula de la Llave 2 (Volcán, Ceniza) o de la Llave 1 (Eco, tambores), 8 tesoro, 11 Puerta del Jefe, 12 reja de la sala de oleadas. En el barranco del Eco, la Puerta de los Ecos es el canal 13.

## Fases, ondas y sacudida de pantalla

Los tres jefes tienen **tres fases** (100 %, 66 % y 33 % de vida). Al cambiar de fase hay una escena corta en la que el jefe es invulnerable, sale un título y aparecen ataques nuevos; la barra de jefe lo indica con "· II" y "· III".

Las **ondas expansivas** son anillos que recorren el suelo: golpean a quien esté apoyado cuando pasan, así que hay que saltar justo a tiempo.

Los golpes fuertes, rugidos y derrumbes **sacuden la pantalla**. Se ajusta o se desactiva con `sacudidaPantalla` en `config/sugar-common.toml` (0 = desactivada, 1 = normal, 2 = el doble).

## Montar vuestro propio templo

1. Construid el templo y colocad los bloques especiales.
2. Poneos en el punto de reunión y ejecutad `/eventtemple define <templo> <x1 y1 z1> <x2 y2 z2>` con dos esquinas opuestas que cubran todo el templo. Vuestra posición queda como vestíbulo.
3. Si añadís bloques con WorldEdit u otras herramientas, ejecutad `/eventtemple scan <templo>`.

## Comandos

Jugadores:
- `/event join <templo>`: ir a un templo abierto o en marcha (al vestíbulo, o a tu checkpoint si ya tienes uno).

Admins (nivel OP 2):
- `/event open <templo>`: abre la entrada de fuera y deja usar `/event join` para que el grupo se reúna en el vestíbulo. La puerta hacia la primera sala sigue cerrada.
- `/event start <templo>`: abre el vestíbulo y empieza la historia (si no estaba abierto, lo abre también).
- `/event stop|reset <templo>`
- `/event door <templo> <canal> open|close`
- `/eventtemple define|place|scan|setlobby|info|remove <templo>`
- `/eventboss summon|kill|info <templo>`

`/eventboss kill` quita el boss sin dar recompensas. `/event reset` cierra todas las puertas (también la entrada), restaura bloques, quita el boss y borra los checkpoints.

Cada templo va por su cuenta: podéis tener los tres construidos y hacer solo uno. Los cerrados no dejan entrar ni con `/event join`, y nada de dentro responde. Orden típico:
```
/event open volcan     (el grupo va entrando con /event join volcan)
/event start volcan    (cuando estéis todos)
/event reset volcan    (al terminar)
```

## Escalado y recompensas

Configurable en `config/sugar-common.toml`:
- Vida efectiva +50% de la base y daño +2% por cada jugador dentro del templo (se recalcula cada 10 s).
- Reciben recompensa quienes hagan al menos el 1% del daño total y, opcionalmente, todos los que estén vivos en el templo.
- Los 3 que más daño hacen reciben además la recompensa MVP.
- Tablas de botín: `data/sugar/loot_tables/bosses/<boss>_participante.json` y `<boss>_mvp.json` (se pueden cambiar con un datapack).
- Cuando cae un jefe, cada jugador en **creativo** conectado al servidor (los admins) recibe la gema de ese jefe: **Gema de la Sierpe**, **Gema de la Bestia** o **Gema del Tamborilero**.
- Cada jugador dentro del templo cuando cae el jefe recibe un **Contenedor de Corazón**: +1 corazón de vida máxima para siempre (máximo 3, uno por templo). Se mantiene al morir.
- Tras las oleadas de la sala del Gran Cofre aparece un **minijefe**: el Capataz de Obsidiana (Volcán), la Matriarca del Nido (Ceniza; aturdidla con una bomba) y el Caballero del Eco (Eco; solo recibe daño por la espalda o al atacar, y a media vida pierde la armadura y queda el esqueleto).

## Estilo

Texturas de 16×16 al estilo vanilla.

## Shaders (Oculus)

Los templos se ven mejor sin shaders. Si un jugador tiene Oculus con shaders puestos:

- Al unirse a un templo abierto (`/event join` o entrando en la zona) recibe un aviso en el chat.
- Al empezar el evento (`/event start`), los shaders se le apagan solos. Los que lleguen tarde también.
- Al caer el jefe, con `/event stop` o `/event reset`, o si sale del servidor, se le vuelven a encender. Solo si se los apagó el mod.

Cada jugador puede desactivarlo en `config/sugar-client.toml` (`apagarShadersEnEventos = false`). Sin Oculus no pasa nada.
