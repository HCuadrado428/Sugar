package com.sugar.test;

import com.sugar.SugarMod;
import com.sugar.block.EventDoorBlock;
import com.sugar.block.EventSwitchBlock;
import com.sugar.block.MagmaVentBlock;
import com.sugar.block.SupplyCrateBlock;
import com.sugar.entity.MagmaSerpent;
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
        data.bossId = boss.getUUID();

        helper.runAfterDelay(400, () -> {
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
}
