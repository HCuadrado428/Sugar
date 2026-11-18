package com.sugar.client;

import com.sugar.SugarMod;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/** Modelo compartido por la Sierpe y sus señuelos: geo/entity/magma_serpent.geo.json */
public class MagmaSerpentModel<T extends GeoAnimatable> extends DefaultedEntityGeoModel<T> {
    public MagmaSerpentModel() {
        super(SugarMod.id("magma_serpent"));
    }
}
