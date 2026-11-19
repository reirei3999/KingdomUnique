package com.kingdomunique.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 憑依操作でプレイヤー入力を読むための accessor。 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    @Accessor("forwardSpeed")
    float ku$forwardSpeed();

    @Accessor("sidewaysSpeed")
    float ku$sidewaysSpeed();

    @Accessor("jumping")
    boolean ku$jumping();
}
