package com.sugar.block;

import com.sugar.temple.TempleData;
import net.minecraft.core.BlockPos;

/** Altar del Jefe: marca dónde aparece el boss del templo (encima del bloque). */
public class BossAltarBlock extends TempleBlock {

    public BossAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onRegistered(TempleData data, BlockPos pos) {
        data.altar = pos;
    }
}
