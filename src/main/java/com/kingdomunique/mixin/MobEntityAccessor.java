package com.kingdomunique.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** MobEntity の protected GoalSelector を mod 内部から操作するための accessor。 */
@Mixin(MobEntity.class)
public interface MobEntityAccessor {

    @Accessor("goalSelector")
    GoalSelector kingdomUnique$goalSelector();

    @Accessor("targetSelector")
    GoalSelector kingdomUnique$targetSelector();
}
