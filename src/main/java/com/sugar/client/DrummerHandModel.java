package com.sugar.client;

import com.sugar.SugarMod;
import com.sugar.entity.DrummerHand;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Mano derecha o izquierda según la entidad; las dos comparten textura. */
public class DrummerHandModel extends GeoModel<DrummerHand> {
    private static final ResourceLocation TEXTURE = SugarMod.id("textures/entity/drummer_hand.png");
    private static final ResourceLocation GEO_L = SugarMod.id("geo/entity/drummer_hand_left.geo.json");
    private static final ResourceLocation GEO_R = SugarMod.id("geo/entity/drummer_hand_right.geo.json");
    private static final ResourceLocation ANIM_L = SugarMod.id("animations/entity/drummer_hand_left.animation.json");
    private static final ResourceLocation ANIM_R = SugarMod.id("animations/entity/drummer_hand_right.animation.json");

    @Override
    public ResourceLocation getModelResource(DrummerHand hand) {
        return hand.isLeft() ? GEO_L : GEO_R;
    }

    @Override
    public ResourceLocation getTextureResource(DrummerHand hand) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(DrummerHand hand) {
        return hand.isLeft() ? ANIM_L : ANIM_R;
    }
}
