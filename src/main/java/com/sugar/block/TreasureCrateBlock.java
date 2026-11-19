package com.sugar.block;

/** Cofre del Tesoro: como el de suministros, pero con el botín extra de la cámara del tesoro. */
public class TreasureCrateBlock extends SupplyCrateBlock {
    public TreasureCrateBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean treasure() {
        return true;
    }
}
