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

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void volcanoTempleFullFlow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos feet = helper.absolutePos(new BlockPos(2, 2, 2)).offset(3000, 20, 3000);

        TempleGenerator.Result result = new VolcanoTempleGenerator().generate(level, feet);
        TempleSavedData saved = TempleSavedData.get(level.getServer());
        TempleData data = saved.define(TempleType.VOLCAN, level.dimension(), result.zone(), result.lobby());
        TempleService.scan(level, data);

        helper.assertTrue(data.altar != null, "Falta el altar");
        int vents = TempleService.positionsOf(level, data, MagmaVentBlock.class).size();
        int seals = TempleService.positionsOf(level, data, EventSwitchBlock.class).size();
        int doors = TempleService.positionsOf(level, data, EventDoorBlock.class).size();
        int crates = TempleService.positionsOf(level, data, SupplyCrateBlock.class).size();
        helper.assertTrue(vents == 8, "Respiraderos: " + vents);
        helper.assertTrue(seals == 3, "Sellos: " + seals);
        helper.assertTrue(doors == 27, "Puertas: " + doors);
        helper.assertTrue(crates == 4, "Cofres: " + crates);

        TempleService.start(level.getServer(), data);
        helper.assertTrue(TempleService.setDoors(level.getServer(), data, 1, true) == 9, "La puerta del canal 1 no se abre");

        // Sin jugadores cerca el servidor descargaría la zona: la mantenemos cargada durante la prueba
        int acx = data.altar.getX() >> 4, acz = data.altar.getZ() >> 4;
        for (int cx = acx - 2; cx <= acx + 2; cx++)
            for (int cz = acz - 2; cz <= acz + 2; cz++)
                level.setChunkForced(cx, cz, true);

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

        helper.runAfterDelay(400, () -> {
            helper.assertTrue(!boss.inIntro(), "La presentación de la Sierpe no ha terminado (fase " + boss.phase() + ")");
            helper.assertTrue(boss.isAlive(), "La Sierpe ha desaparecido durante el evento (motivo: "
                    + boss.getRemovalReason() + ", fase " + boss.phase() + ", pos " + boss.blockPosition().toShortString() + ")");
            helper.assertTrue(boss.tickCount > 300, "La Sierpe no se actualiza");
            TempleService.reset(level.getServer(), data);
            helper.assertTrue(!boss.isAlive(), "El reset no ha quitado a la Sierpe");
            saved.remove(TempleType.VOLCAN);
            for (int cx = acx - 2; cx <= acx + 2; cx++)
                for (int cz = acz - 2; cz <= acz + 2; cz++)
                    level.setChunkForced(cx, cz, false);
            helper.succeed();
        });
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
        BlockPos feet = helper.absolutePos(new BlockPos(2, 2, 2)).offset(-3000, 20, 6000);
        TempleGenerator.Result result = new AshTempleGenerator().generate(level, feet);
        TempleSavedData saved = TempleSavedData.get(level.getServer());
        TempleData data = saved.define(TempleType.CENIZA, level.dimension(), result.zone(), result.lobby());
        TempleService.scan(level, data);

        helper.assertTrue(data.altar != null, "Falta el altar");
        int statues = TempleService.positionsOf(level, data, AshStatueBlock.class).size();
        int bushes = TempleService.positionsOf(level, data, BombBushBlock.class).size();
        int geysers = TempleService.positionsOf(level, data, FireGeyserBlock.class).size();
        int cracks = TempleService.positionsOf(level, data, MagmaVentBlock.class).size();
        int doors = TempleService.positionsOf(level, data, EventDoorBlock.class).size();
        int crates = TempleService.positionsOf(level, data, SupplyCrateBlock.class).size();
        helper.assertTrue(statues == 264, "Estatuas: " + statues);
        helper.assertTrue(bushes == 14, "Matas: " + bushes);
        helper.assertTrue(geysers == 24, "Géiseres: " + geysers);
        helper.assertTrue(cracks == 6, "Grietas: " + cracks);
        helper.assertTrue(doors == 18, "Puertas: " + doors);
        helper.assertTrue(crates == 4, "Cofres: " + crates);

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
        BlockPos feet = helper.absolutePos(new BlockPos(2, 2, 2)).offset(6000, 20, -3000);
        TempleGenerator.Result result = new EchoTempleGenerator().generate(level, feet);
        TempleSavedData saved = TempleSavedData.get(level.getServer());
        TempleData data = saved.define(TempleType.ECO, level.dimension(), result.zone(), result.lobby());
        TempleService.scan(level, data);

        helper.assertTrue(data.altar != null, "Falta el altar");
        int drums = TempleService.positionsOf(level, data, EchoDrumBlock.class).size();
        int gongs = TempleService.positionsOf(level, data, EchoGuideBlock.class).size();
        int pedestals = TempleService.positionsOf(level, data, LensPedestalBlock.class).size();
        int doors = TempleService.positionsOf(level, data, EventDoorBlock.class).size();
        int crates = TempleService.positionsOf(level, data, SupplyCrateBlock.class).size();
        helper.assertTrue(drums == 8, "Tambores: " + drums);
        helper.assertTrue(gongs == 1, "Gongs: " + gongs);
        helper.assertTrue(pedestals == 8, "Atriles: " + pedestals);
        helper.assertTrue(doors == 27, "Puertas: " + doors);
        helper.assertTrue(crates == 4, "Cofres: " + crates);
        int platforms = 0;
        for (BlockPos p : BlockPos.betweenClosed(feet.offset(-6, -1, -7), feet.offset(60, -1, 10))) {
            if (level.getBlockState(p).getBlock() instanceof EchoPlatformBlock) platforms++;
        }
        helper.assertTrue(platforms > 40, "Puente invisible: " + platforms);

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
            helper.assertTrue(level.getBlockState(door).getValue(EventDoorBlock.OPEN), "La puerta del canal 1 no se ha abierto");
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
}
