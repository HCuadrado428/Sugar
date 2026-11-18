package com.sugar.client;

import com.sugar.SugarMod;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/** Modelo de la Gran Bestia y de sus crías: geo/entity/ash_beast.geo.json */
public class AshBeastModel<T extends GeoAnimatable> extends DefaultedEntityGeoModel<T> {
    public AshBeastModel() {
        super(SugarMod.id("ash_beast"));
    }
}
