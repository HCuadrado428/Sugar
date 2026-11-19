package com.sugar.test;

import com.sugar.SugarMod;
import com.sugar.block.AshStatueBlock;
import com.sugar.block.BombBushBlock;
import com.sugar.block.EchoDrumBlock;
import com.sugar.block.EchoGuideBlock;
import com.sugar.block.LensPedestalBlock;
import com.sugar.block.EchoPlatformBlock;
import com.sugar.entity.DrummerHand;
import com.sugar.entity.EchoDrummer;
import com.sugar.temple.EchoPuzzle;
import com.sugar.temple.gen.EchoTempleGenerator;
import com.sugar.block.EventDoorBlock;
import com.sugar.block.FireGeyserBlock;
import com.sugar.block.EventSwitchBlock;
import com.sugar.block.MagmaVentBlock;
import com.sugar.block.SupplyCrateBlock;
import com.sugar.entity.AshBeast;
import com.sugar.entity.AshBomb;
import com.sugar.entity.MagmaSerpent;
import com.sugar.registry.ModBlocks;
import com.sugar.temple.gen.AshTempleGenerator;
import com.sugar.registry.ModEntities;
import com.sugar.temple.TempleData;
import com.sugar.temple.TempleGenerator;
import com.sugar.temple.TempleSavedData;
import com.sugar.temple.TempleService;
import com.sugar.temple.TempleType;
import com.sugar.temple.gen.VolcanoTempleGenerator;
import net.minecraft.core.BlockPos;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Pruebas automáticas que se ejecutan en GitHub Actions con un servidor real:
 * comprueban que el mod arranca, que el templo se genera completo y que la Sierpe funciona sin errores.
 */
@GameTestHolder(SugarMod.MODID)
@PrefixGameTestTemplate(false)
public class SugarGameTests {

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void serpentRunsWithoutTemple(GameTestHelper helper) {
        MagmaSerpent serpent = helper.spawn(ModEntities.MAGMA_SERPENT.get(), new BlockPos(2, 2, 2));
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(serpent.isAlive(), "La Sierpe ha desaparecido");
            serpent.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 900)
    public static void volcanoTempleFullFlow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos feet = helper.absolutePos(new BlockPos(2, 2, 2)).offset(3000, 40, 3000);

        TempleGenerator.Result result = new VolcanoTempleGenerator().generate(level, feet);
        TempleSavedData saved = TempleSavedData.get(level.getServer());
        TempleData data = saved.define(TempleType.VOLCAN, level.dimension(), result.zone(), result.lobby());
        TempleService.scan(level, data);
        var server = level.getServer();

        helper.assertTrue(data.altar != null, "Falta el altar");
        int vents = TempleService.positionsOf(level, data, MagmaVentBlock.class).size();
        int keyChests = TempleService.positionsOf(level, data, com.sugar.block.KeyChestBlock.class).size();
        int bigChests = TempleService.positionsOf(level, data, com.sugar.block.BigChestBlock.class).size();
        int locks = TempleService.positionsOf(level, data, com.sugar.block.LockedDoorBlock.class).size();
        int hammerSwitches = TempleService.positionsOf(level, data, com.sugar.block.HammerSwitchBlock.class).size();
        int seals = TempleService.positionsOf(level, data, EventSwitchBlock.class).size() - hammerSwitches;
        int plates = TempleService.positionsOf(level, data, com.sugar.block.GroupPlateBlock.class).size();
        int relics = TempleService.positionsOf(level, data, com.sugar.block.RelicBlock.class).size();
        int altars = TempleService.positionsOf(level, data, com.sugar.block.WaveAltarBlock.class).size();
        int treasure = TempleService.positionsOf(level, data, com.sugar.block.TreasureCrateBlock.class).size();
        int cracks = TempleService.positionsOf(level, data, com.sugar.block.CrackedWallBlock.class).size();
        var lake = TempleService.positionsOf(level, data, com.sugar.block.DrainableMagmaBlock.class);
        helper.assertTrue(vents == 8, "Respiraderos: " + vents);
        helper.assertTrue(keyChests == 4, "Cofres con llave: " + keyChests);
        helper.assertTrue(bigChests == 3, "Grandes Cofres: " + bigChests);
        helper.assertTrue(locks == 27 + 12, "Bloques de cerradura (3 puertas + la del jefe): " + locks);
        helper.assertTrue(hammerSwitches == 6, "Interruptores de obsidiana: " + hammerSwitches);
        helper.assertTrue(seals == 3, "Sellos: " + seals);
        helper.assertTrue(plates == 8, "Placas: " + plates);
        helper.assertTrue(relics == 3, "Reliquias: " + relics);
        helper.assertTrue(altars == 1, "Altares de oleadas: " + altars);
        helper.assertTrue(treasure == 3, "Cofres del tesoro: " + treasure);
        helper.assertTrue(cracks > 400, "Muros agrietados: " + cracks);
        helper.assertTrue(lake.size() > 800, "Lago de magma: " + lake.size());

        // Empieza: se abren el campamento y la reja de la Forja
        TempleService.start(server, data);
        helper.assertTrue(doorsOpen(level, data, 0), "No se abre la salida del campamento");
        helper.assertTrue(doorsOpen(level, data, com.sugar.temple.TempleObjectives.V_FORGE_GATE), "No se abre la reja de la Forja");

        // Llaves: la primera abre los sellos, el puente pide la 2 y la 3
        BlockPos sealLock = lockOf(level, data, com.sugar.temple.TempleObjectives.V_LOCK_SEALS);
        BlockPos bridgeLock = lockOf(level, data, com.sugar.temple.TempleObjectives.V_LOCK_BRIDGE);
        helper.assertTrue(!com.sugar.block.LockedDoorBlock.tryUnlock(level, sealLock, level.getBlockState(sealLock), data), "Se abre sin llave");
        openKeyChest(level, data, 1);
        helper.assertTrue(com.sugar.block.LockedDoorBlock.tryUnlock(level, sealLock, level.getBlockState(sealLock), data), "La Llave 1 no abre los sellos");
        openKeyChest(level, data, 2);
        helper.assertTrue(!com.sugar.block.LockedDoorBlock.tryUnlock(level, bridgeLock, level.getBlockState(bridgeLock), data), "La cerradura doble se abre con una llave");
        openKeyChest(level, data, 3);
        helper.assertTrue(com.sugar.block.LockedDoorBlock.tryUnlock(level, bridgeLock, level.getBlockState(bridgeLock), data), "Las llaves 2 y 3 no abren el puente");

        // El Puente vacía el lago
        TempleService.setDoors(server, data, com.sugar.temple.TempleObjectives.V_BRIDGE, true);
        helper.assertTrue(level.getBlockState(lake.get(0)).getValue(EventDoorBlock.OPEN), "El lago no se vacía");

        // Martillo: muros agrietados e interruptores
        BlockPos crack = TempleService.positionsOf(level, data, com.sugar.block.CrackedWallBlock.class).get(0);
        helper.assertTrue(com.sugar.block.CrackedWallBlock.smash(level, crack) > 0, "El muro agrietado no se rompe");
        for (BlockPos p : TempleService.positionsOf(level, data, com.sugar.block.HammerSwitchBlock.class)) {
            var st = level.getBlockState(p);
            ((com.sugar.block.HammerSwitchBlock) st.getBlock()).activate(level, p, st, data);
        }
        helper.assertTrue(data.isDone(com.sugar.temple.TempleObjectives.V_SWITCHES), "Los interruptores no abren la jaula");

        // Llave del Jefe y puerta del jefe
        BlockPos bossLock = lockOf(level, data, com.sugar.temple.TempleObjectives.V_BOSS_DOOR);
        helper.assertTrue(!com.sugar.block.LockedDoorBlock.tryUnlock(level, bossLock, level.getBlockState(bossLock), data), "La puerta del jefe se abre sin su llave");
        for (BlockPos p : TempleService.positionsOf(level, data, com.sugar.block.BigChestBlock.class)) {
            var st = level.getBlockState(p);
            if (st.getValue(com.sugar.block.BigChestBlock.KIND) == com.sugar.block.BigChestBlock.BOSS_KEY)
                com.sugar.block.BigChestBlock.open(level, p, st, data, null);
        }
        helper.assertTrue(com.sugar.block.LockedDoorBlock.tryUnlock(level, bossLock, level.getBlockState(bossLock), data), "La Llave del Jefe no abre la puerta");

        // Sin jugadores cerca el servidor descargaría la zona: la mantenemos cargada durante la prueba
        BlockPos forgeAltar = TempleService.positionsOf(level, data, com.sugar.block.WaveAltarBlock.class).get(0);
        List<long[]> forced = new java.util.ArrayList<>();
        for (BlockPos c : new BlockPos[]{data.altar, forgeAltar})
            for (int cx = (c.getX() >> 4) - 2; cx <= (c.getX() >> 4) + 2; cx++)
                for (int cz = (c.getZ() >> 4) - 2; cz <= (c.getZ() >> 4) + 2; cz++) {
                    level.setChunkForced(cx, cz, true);
                    forced.add(new long[]{cx, cz});
                }

        // El Capataz
        com.sugar.entity.ForgeForeman foreman = com.sugar.temple.WaveRoom.spawnForeman(level, data, forgeAltar);
        helper.assertTrue(foreman != null, "No sale el Capataz");

        BlockPos spawn = data.altar.above();
        MagmaSerpent boss = ModEntities.MAGMA_SERPENT.get().create(level);
        helper.assertTrue(boss != null, "No se pudo crear la Sierpe");
        boss.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), MobSpawnType.EVENT, null, null);
        boss.bindTemple(TempleType.VOLCAN);
        boss.rescale();
        level.addFreshEntity(boss);
        boss.beginIntro();
        data.bossId = boss.getUUID();

        helper.runAfterDelay(120, () -> {
            helper.assertTrue(foreman.isAlive(), "El Capataz ha desaparecido");
            helper.assertTrue(foreman.phase() != com.sugar.entity.ForgeForeman.INTRO, "El Capataz no termina su presentación");
            foreman.kill();
        });
        helper.runAfterDelay(180, () ->
            helper.assertTrue(data.isDone(com.sugar.temple.TempleObjectives.V_FORGE) && doorsOpen(level, data, com.sugar.temple.TempleObjectives.V_FORGE),
                    "Al morir el Capataz no se abre la Forja"));

        helper.runAfterDelay(400, () -> {
            helper.assertTrue(!boss.inIntro(), "La presentación de la Sierpe no ha terminado (fase " + boss.phase() + ")");
            helper.assertTrue(boss.isAlive(), "La Sierpe ha desaparecido durante el evento (motivo: "
                    + boss.getRemovalReason() + ", fase " + boss.phase() + ", pos " + boss.blockPosition().toShortString() + ")");
            TempleService.reset(server, data);
            helper.assertTrue(!boss.isAlive(), "El reset no ha quitado a la Sierpe");
            helper.assertTrue(data.keys == 0, "El reset no quita las llaves");
            helper.assertTrue(!level.getBlockState(lake.get(0)).getValue(EventDoorBlock.OPEN), "El reset no vuelve a llenar el lago");
            helper.assertTrue(!level.getBlockState(crack).getValue(com.sugar.block.CrackedWallBlock.BROKEN), "El reset no levanta el muro");
            saved.remove(TempleType.VOLCAN);
            for (long[] c : forced) level.setChunkForced((int) c[0], (int) c[1], false);
            helper.succeed();
        });
    }

    private static boolean doorsOpen(ServerLevel level, TempleData data, int channel) {
        boolean any = false;
        for (BlockPos p : TempleService.positionsOf(level, data, EventDoorBlock.class)) {
            var st = level.getBlockState(p);
            if (st.getValue(EventDoorBlock.CHANNEL) != channel) continue;
            if (!st.getValue(EventDoorBlock.OPEN)) return false;
            any = true;
        }
        return any;
    }

    private static BlockPos lockOf(ServerLevel level, TempleData data, int channel) {
        return TempleService.positionsOf(level, data, com.sugar.block.LockedDoorBlock.class).stream()
                .filter(p -> level.getBlockState(p).getValue(EventDoorBlock.CHANNEL) == channel).findFirst().orElseThrow();
    }

    private static void openKeyChest(ServerLevel level, TempleData data, int key) {
        for (BlockPos p : TempleService.positionsOf(level, data, com.sugar.block.KeyChestBlock.class)) {
            var st = level.getBlockState(p);
            if (st.getValue(com.sugar.block.KeyChestBlock.KEY) == key) com.sugar.block.KeyChestBlock.open(level, p, st, data);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void bombBreaksStatue(GameTestHelper helper) {
        BlockPos statue = new BlockPos(2, 1, 2);
        helper.setBlock(statue, ModBlocks.ASH_STATUE.get());
        AshBomb bomb = new AshBomb(ModEntities.ASH_BOMB.get(), helper.getLevel());
        BlockPos at = helper.absolutePos(statue.above());
        bomb.moveTo(at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5, 0, 0);
        helper.getLevel().addFreshEntity(bomb);
        helper.runAfterDelay(AshBomb.FUSE + 15, () -> {
            helper.assertTrue(helper.getBlockState(statue).getValue(AshStatueBlock.BROKEN), "La bomba no ha roto la estatua");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void beastSwallowsBombAndStuns(GameTestHelper helper) {
        AshBeast beast = helper.spawn(ModEntities.ASH_BEAST.get(), new BlockPos(2, 1, 2));
        helper.runAfterDelay(5, () -> {
            beast.debugSetPhase(AshBeast.INHALE);
            AshBomb bomb = new AshBomb(ModEntities.ASH_BOMB.get(), helper.getLevel());
            var m = beast.mouthPos();
            bomb.moveTo(m.x, m.y, m.z, 0, 0);
            helper.getLevel().addFreshEntity(bomb);
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(beast.phase() == AshBeast.STUNNED, "La Bestia no se ha aturdido (fase " + beast.phase() + ")");
            beast.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void ashTempleFullFlow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos feet = helper.absolutePos(new BlockPos(2, 2, 2)).offset(-3000, 45, 6000);
        TempleGenerator.Result result = new AshTempleGenerator().generate(level, feet);
        TempleSavedData saved = TempleSavedData.get(level.getServer());
        TempleData data = saved.define(TempleType.CENIZA, level.dimension(), result.zone(), result.lobby());
        TempleService.scan(level, data);

        helper.assertTrue(data.altar != null, "Falta el altar");
        int statues = TempleService.positionsOf(level, data, AshStatueBlock.class).size();
        helper.assertTrue(statues > 300, "Estatuas: " + statues);
        checkOot(helper, level, data, 3, 6);

        int acx = data.altar.getX() >> 4, acz = data.altar.getZ() >> 4;
        for (int cx = acx - 2; cx <= acx + 2; cx++)
            for (int cz = acz - 2; cz <= acz + 2; cz++)
                level.setChunkForced(cx, cz, true);

        TempleService.start(level.getServer(), data);
        BlockPos spawn = data.altar.above();
        AshBeast boss = ModEntities.ASH_BEAST.get().create(level);
        helper.assertTrue(boss != null, "No se pudo crear la Bestia");
        boss.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), MobSpawnType.EVENT, null, null);
        boss.bindTemple(TempleType.CENIZA);
        boss.rescale();
        level.addFreshEntity(boss);
        boss.beginIntro();
        data.bossId = boss.getUUID();

        helper.runAfterDelay(400, () -> {
            helper.assertTrue(!boss.inIntro(), "La presentación de la Bestia no ha terminado (fase " + boss.phase() + ")");
            helper.assertTrue(boss.isAlive(), "La Bestia ha desaparecido (" + boss.getRemovalReason() + ")");
            TempleService.reset(level.getServer(), data);
            helper.assertTrue(!boss.isAlive(), "El reset no ha quitado a la Bestia");
            saved.remove(TempleType.CENIZA);
            for (int cx = acx - 2; cx <= acx + 2; cx++)
                for (int cz = acz - 2; cz <= acz + 2; cz++)
                    level.setChunkForced(cx, cz, false);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void drummerRunsWithoutTemple(GameTestHelper helper) {
        EchoDrummer drummer = helper.spawn(ModEntities.ECHO_DRUMMER.get(), new BlockPos(2, 2, 2));
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(drummer.isAlive(), "El Tamborilero ha desaparecido");
            helper.assertTrue(drummer.hand(true) != null && drummer.hand(false) != null, "No tiene las dos manos");
            drummer.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 900)
    public static void echoTempleFullFlow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos feet = helper.absolutePos(new BlockPos(2, 2, 2)).offset(6000, 45, -3000);
        TempleGenerator.Result result = new EchoTempleGenerator().generate(level, feet);
        TempleSavedData saved = TempleSavedData.get(level.getServer());
        TempleData data = saved.define(TempleType.ECO, level.dimension(), result.zone(), result.lobby());
        TempleService.scan(level, data);

        helper.assertTrue(data.altar != null, "Falta el altar");
        int drums = TempleService.positionsOf(level, data, EchoDrumBlock.class).size();
        int gongs = TempleService.positionsOf(level, data, EchoGuideBlock.class).size();
        helper.assertTrue(drums == 8, "Tambores: " + drums);
        helper.assertTrue(gongs == 1, "Gongs: " + gongs);
        checkOot(helper, level, data, 6, 0);
        int platforms = 0;
        BlockPos o = EchoTempleGenerator.origin(feet);
        for (BlockPos p : BlockPos.betweenClosed(o.offset(10, -26, -10), o.offset(72, 1, 25))) {
            if (level.getBlockState(p).getBlock() instanceof EchoPlatformBlock) platforms++;
        }
        helper.assertTrue(platforms > 150, "Caminos ocultos: " + platforms);

        BlockPos gong = TempleService.positionsOf(level, data, EchoGuideBlock.class).get(0);
        java.util.List<int[]> forced = new java.util.ArrayList<>();
        for (BlockPos c : new BlockPos[]{gong, data.altar}) {
            for (int cx = (c.getX() >> 4) - 2; cx <= (c.getX() >> 4) + 2; cx++)
                for (int cz = (c.getZ() >> 4) - 2; cz <= (c.getZ() >> 4) + 2; cz++) {
                    level.setChunkForced(cx, cz, true);
                    forced.add(new int[]{cx, cz});
                }
        }
        TempleService.start(level.getServer(), data);
        EchoPuzzle.start(level, gong, (EchoGuideBlock) level.getBlockState(gong).getBlock());

        // Simón dice: el grupo repite cada ritmo en cuanto termina de sonar
        Runnable play = () -> {
            helper.assertTrue(EchoPuzzle.isWaitingInput(gong), "El puzle no espera a los jugadores (" + EchoPuzzle.describe(gong) + ")");
            String before = EchoPuzzle.describe(gong);
            for (int note : EchoPuzzle.currentSequence(gong)) {
                BlockPos drum = TempleService.positionsOf(level, data, EchoDrumBlock.class).stream()
                        .filter(p -> level.getBlockState(p).getValue(EchoDrumBlock.NOTE) == note).findFirst().orElseThrow();
                EchoPuzzle.drumHit(level, drum, note, null);
            }
            SugarMod.LOGGER.info("Puzle del eco: {} -> {}", before, EchoPuzzle.describe(gong));
        };
        helper.runAfterDelay(90, () -> play.run());
        helper.runAfterDelay(200, () -> play.run());
        helper.runAfterDelay(330, () -> play.run());
        helper.runAfterDelay(340, () -> {
            helper.assertTrue(EchoPuzzle.isSolved(gong), "El puzle de los tambores no se ha resuelto (" + EchoPuzzle.describe(gong) + ")");
            BlockPos door = TempleService.positionsOf(level, data, EventDoorBlock.class).stream()
                    .filter(p -> level.getBlockState(p).getValue(EventDoorBlock.CHANNEL) == EchoPuzzle.CHANNEL).findFirst().orElseThrow();
            helper.assertTrue(level.getBlockState(door).getValue(EventDoorBlock.OPEN), "La jaula de la Llave 1 no se ha abierto");
        });

        EchoDrummer[] boss = new EchoDrummer[1];
        helper.runAfterDelay(345, () -> {
            BlockPos spawn = data.altar.above();
            EchoDrummer b = ModEntities.ECHO_DRUMMER.get().create(level);
            helper.assertTrue(b != null, "No se pudo crear el Tamborilero");
            b.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
            b.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), MobSpawnType.EVENT, null, null);
            b.bindTemple(TempleType.ECO);
            b.rescale();
            level.addFreshEntity(b);
            b.beginIntro();
            data.bossId = b.getUUID();
            boss[0] = b;
        });
        helper.runAfterDelay(560, () -> {
            EchoDrummer b = boss[0];
            helper.assertTrue(!b.inIntro(), "La presentación del Tamborilero no ha terminado (fase " + b.phase() + ")");
            helper.assertTrue(b.isAlive(), "El Tamborilero ha desaparecido (" + b.getRemovalReason() + ")");
            DrummerHand l = b.hand(true), r = b.hand(false);
            helper.assertTrue(l != null && r != null, "Faltan manos");
            l.debugStun();
            r.debugStun();
            helper.assertTrue(b.isExposed(), "Con las dos manos en el suelo el torso no cae (fase " + b.phase() + ")");
        });
        helper.runAfterDelay(590, () -> {
            EchoDrummer b = boss[0];
            float before = b.getHealth();
            b.hurt(level.damageSources().generic(), 20.0F);
            helper.assertTrue(b.getHealth() < before, "El torso expuesto no recibe daño");
            TempleService.reset(level.getServer(), data);
            helper.assertTrue(!b.isAlive(), "El reset no ha quitado al Tamborilero");
            helper.assertTrue(!EchoPuzzle.isSolved(gong), "El reset no reinicia el puzle");
        });
        helper.runAfterDelay(660, () -> {
            int hands = level.getEntitiesOfClass(DrummerHand.class, new net.minecraft.world.phys.AABB(data.altar).inflate(40)).size();
            helper.assertTrue(hands == 0, "Quedan manos sueltas tras el reset: " + hands);
            saved.remove(TempleType.ECO);
            for (int[] c : forced) level.setChunkForced(c[0], c[1], false);
            helper.succeed();
        });
    }

    /** Fuerza las fases 2 y 3 de un jefe y comprueba que termina la escena y sigue peleando. */
    private static void runStages(GameTestHelper helper, com.sugar.boss.SugarBoss boss, java.util.function.IntSupplier phase, int ragePhase) {
        helper.runAfterDelay(20, () -> {
            boss.debugNextStage();
            helper.assertTrue(boss.stage() == 2, "No pasa a la fase 2");
            helper.assertTrue(phase.getAsInt() == ragePhase, "No arranca la escena de la fase 2 (fase " + phase.getAsInt() + ")");
        });
        helper.runAfterDelay(110, () -> {
            helper.assertTrue(boss.isAlive(), "Ha desaparecido en la fase 2");
            helper.assertTrue(phase.getAsInt() != ragePhase, "La escena de la fase 2 no termina");
            boss.debugNextStage();
            helper.assertTrue(boss.stage() == 3, "No pasa a la fase 3");
        });
        helper.runAfterDelay(220, () -> {
            helper.assertTrue(boss.isAlive(), "Ha desaparecido en la fase 3");
            helper.assertTrue(phase.getAsInt() != ragePhase, "La escena de la fase 3 no termina");
            boss.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void serpentStages(GameTestHelper helper) {
        MagmaSerpent b = helper.spawn(ModEntities.MAGMA_SERPENT.get(), new BlockPos(2, 2, 2));
        runStages(helper, b, b::phase, MagmaSerpent.RAGE);
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void beastStages(GameTestHelper helper) {
        AshBeast b = helper.spawn(ModEntities.ASH_BEAST.get(), new BlockPos(2, 2, 2));
        runStages(helper, b, b::phase, AshBeast.RAGE);
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void drummerStages(GameTestHelper helper) {
        EchoDrummer b = helper.spawn(ModEntities.ECHO_DRUMMER.get(), new BlockPos(2, 2, 2));
        runStages(helper, b, b::phase, EchoDrummer.RAGE);
    }

    /** Cada templo tiene una sala de oleadas, cuatro placas, tres reliquias, la campana y el tesoro. */
    /** Piezas comunes de los templos al estilo Ocarina: llaves, cofres, cerraduras, placas, sellos y reliquias. */
    private static void checkOot(GameTestHelper helper, ServerLevel level, TempleData data, int seals, int hammerSwitches) {
        helper.assertTrue(data.altar != null, "Falta el altar");
        int keyChests = TempleService.positionsOf(level, data, com.sugar.block.KeyChestBlock.class).size();
        int bigChests = TempleService.positionsOf(level, data, com.sugar.block.BigChestBlock.class).size();
        int locks = TempleService.positionsOf(level, data, com.sugar.block.LockedDoorBlock.class).size();
        int hs = TempleService.positionsOf(level, data, com.sugar.block.HammerSwitchBlock.class).size();
        int sw = TempleService.positionsOf(level, data, EventSwitchBlock.class).size() - hs;
        int plates = TempleService.positionsOf(level, data, com.sugar.block.GroupPlateBlock.class).size();
        int relics = TempleService.positionsOf(level, data, com.sugar.block.RelicBlock.class).size();
        int altars = TempleService.positionsOf(level, data, com.sugar.block.WaveAltarBlock.class).size();
        int treasure = TempleService.positionsOf(level, data, com.sugar.block.TreasureCrateBlock.class).size();
        helper.assertTrue(keyChests == 4, "Cofres con llave: " + keyChests);
        helper.assertTrue(bigChests == 3, "Grandes Cofres: " + bigChests);
        helper.assertTrue(locks == 27 + 12, "Bloques de cerradura: " + locks);
        helper.assertTrue(hs == hammerSwitches, "Interruptores de obsidiana: " + hs);
        helper.assertTrue(sw == seals, "Sellos: " + sw);
        helper.assertTrue(plates >= 8, "Placas: " + plates);
        helper.assertTrue(relics == 3, "Reliquias: " + relics);
        helper.assertTrue(altars == 1, "Altares de oleadas: " + altars);
        helper.assertTrue(treasure == 3, "Cofres del tesoro: " + treasure);
    }

    private static void checkEventRooms(GameTestHelper helper, ServerLevel level, TempleData data) {
        int altars = TempleService.positionsOf(level, data, com.sugar.block.WaveAltarBlock.class).size();
        int plates = TempleService.positionsOf(level, data, com.sugar.block.GroupPlateBlock.class).size();
        int relics = TempleService.positionsOf(level, data, com.sugar.block.RelicBlock.class).size();
        int bells = TempleService.positionsOf(level, data, com.sugar.block.BossBellBlock.class).size();
        int treasure = TempleService.positionsOf(level, data, com.sugar.block.TreasureCrateBlock.class).size();
        helper.assertTrue(altars == 1, "Altares de oleadas: " + altars);
        helper.assertTrue(plates >= 4, "Placas de grupo: " + plates);
        helper.assertTrue(relics == 3, "Reliquias: " + relics);
        helper.assertTrue(bells == 1, "Campanas: " + bells);
        helper.assertTrue(treasure == 3, "Cofres del tesoro: " + treasure);
    }

    /** Recorrido del templo de ceniza: oleadas, bombas e interruptores, llaves, lago, reliquias y jefe. */
    @GameTest(template = "empty", timeoutTicks = 1200, batch = "evento")
    public static void eventModeFlow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos feet = helper.absolutePos(new BlockPos(2, 2, 2)).offset(-3000, 45, -6000);
        TempleGenerator.Result result = new AshTempleGenerator().generate(level, feet);
        TempleSavedData saved = TempleSavedData.get(level.getServer());
        TempleData data = saved.define(TempleType.CENIZA, level.dimension(), result.zone(), result.lobby());
        TempleService.scan(level, data);
        var server = level.getServer();
        java.util.List<int[]> forced = new java.util.ArrayList<>();
        BlockPos altarWave = TempleService.positionsOf(level, data, com.sugar.block.WaveAltarBlock.class).get(0);
        BlockPos sw = TempleService.positionsOf(level, data, com.sugar.block.HammerSwitchBlock.class).get(0);
        for (BlockPos c : new BlockPos[]{altarWave, sw, data.altar}) {
            for (int cx = (c.getX() >> 4) - 2; cx <= (c.getX() >> 4) + 2; cx++)
                for (int cz = (c.getZ() >> 4) - 2; cz <= (c.getZ() >> 4) + 2; cz++) {
                    level.setChunkForced(cx, cz, true);
                    forced.add(new int[]{cx, cz});
                }
        }
        java.util.function.IntPredicate doorOpen = ch -> TempleService.positionsOf(level, data, EventDoorBlock.class).stream()
                .filter(p -> level.getBlockState(p).getValue(EventDoorBlock.CHANNEL) == ch)
                .allMatch(p -> level.getBlockState(p).getValue(EventDoorBlock.OPEN));
        int gate = com.sugar.temple.TempleObjectives.V_FORGE_GATE, cage = com.sugar.temple.TempleObjectives.V_FORGE;

        // Abrir para reunirse: entrada abierta, el camino todavía cerrado
        TempleService.open(server, data);
        helper.assertTrue(data.open && !data.active, "/event open no deja el templo abierto sin empezar");
        helper.assertTrue(doorOpen.test(com.sugar.temple.TempleObjectives.ENTRANCE), "No se abre la entrada");
        helper.assertTrue(!doorOpen.test(0), "El camino se abre antes de /event start");
        TempleService.start(server, data);
        helper.assertTrue(doorOpen.test(0), "No se abre el camino");
        helper.assertTrue(doorOpen.test(gate), "No se abre la reja del nido");
        helper.runAfterDelay(50, () -> {
            com.sugar.block.WaveAltarBlock.activate(level, altarWave, level.getBlockState(altarWave), data);
            helper.assertTrue(!doorOpen.test(gate), "La reja del nido no se cierra con las oleadas");
            // Una bomba junto a un interruptor de obsidiana lo enciende
            AshBomb bomb = new AshBomb(ModEntities.ASH_BOMB.get(), level);
            bomb.moveTo(sw.getX() + 1.5, sw.getY() + 0.1, sw.getZ() + 0.5, 0, 0);
            level.addFreshEntity(bomb);
        });
        helper.runAfterDelay(50 + AshBomb.FUSE + 15, () ->
            helper.assertTrue(level.getBlockState(sw).getValue(EventSwitchBlock.ACTIVE), "La bomba no enciende el interruptor"));
        for (int w = 0; w < 3; w++) {
            final int wave = w;
            helper.runAfterDelay(50 + 80 * (w + 1), () -> {
                java.util.List<net.minecraft.world.entity.Mob> mobs = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                        new net.minecraft.world.phys.AABB(altarWave).inflate(16), m -> m.getTags().contains(com.sugar.temple.WaveRoom.TAG));
                helper.assertTrue(!mobs.isEmpty(), "No ha salido la oleada " + (wave + 1));
                mobs.forEach(net.minecraft.world.entity.Entity::discard);
            });
        }
        helper.runAfterDelay(50 + 80 * 3 + 20, () -> {
            helper.assertTrue(doorOpen.test(cage) && data.isDone(cage), "Superar las oleadas no abre la jaula del Gran Cofre");
            helper.assertTrue(doorOpen.test(gate), "La reja del nido no se vuelve a abrir");
            // Reliquias: al encontrar las tres se abre el tesoro
            for (BlockPos r : TempleService.positionsOf(level, data, com.sugar.block.RelicBlock.class))
                com.sugar.block.RelicBlock.collect(level, r, level.getBlockState(r), data, "Prueba");
            helper.assertTrue(doorOpen.test(com.sugar.temple.TempleObjectives.VAULT), "Las reliquias no abren la cámara del tesoro");
            // Llaves y cerraduras
            for (int key = 1; key <= 4; key++) openKeyChest(level, data, key);
            for (int ch : new int[]{1, 2, 3}) {
                BlockPos lock = lockOf(level, data, ch);
                helper.assertTrue(com.sugar.block.LockedDoorBlock.tryUnlock(level, lock, level.getBlockState(lock), data), "No se abre la cerradura " + ch);
            }
            TempleService.setDoors(server, data, com.sugar.temple.TempleObjectives.V_BRIDGE, true);
            for (BlockPos p : TempleService.positionsOf(level, data, com.sugar.block.BigChestBlock.class)) {
                var st = level.getBlockState(p);
                if (st.getValue(com.sugar.block.BigChestBlock.KIND) == com.sugar.block.BigChestBlock.BOSS_KEY)
                    com.sugar.block.BigChestBlock.open(level, p, st, data, null);
            }
            BlockPos bossLock = lockOf(level, data, com.sugar.temple.TempleObjectives.V_BOSS_DOOR);
            helper.assertTrue(com.sugar.block.LockedDoorBlock.tryUnlock(level, bossLock, level.getBlockState(bossLock), data), "No se abre la puerta del jefe");
            helper.assertTrue(TempleService.summonBoss(level, data, e -> {}) != null, "No despierta el jefe");
        });
        helper.runAfterDelay(50 + 80 * 3 + 20 + 120, () -> {
            helper.assertTrue(TempleService.findBoss(level, data) != null, "El jefe ha desaparecido");
            TempleService.reset(server, data);
            helper.assertTrue(data.progressMask == 0 && !data.active && !data.open && data.keys == 0, "El reset no borra el progreso");
            helper.assertTrue(!doorOpen.test(com.sugar.temple.TempleObjectives.ENTRANCE), "El reset no cierra la entrada");
            helper.assertTrue(!doorOpen.test(cage), "El reset no cierra las jaulas");
            saved.remove(TempleType.CENIZA);
            for (int[] c : forced) level.setChunkForced(c[0], c[1], false);
            helper.succeed();
        });
    }
}
