package com.kingdomunique.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.ForgingScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 継承フィールドへアクセスするための accessor。 */
@Mixin(ForgingScreenHandler.class)
public interface ForgingScreenHandlerAccessor {

    @Accessor("input")
    Inventory ku$input();

    @Accessor("output")
    CraftingResultInventory ku$output();

    @Accessor("player")
    PlayerEntity ku$player();
}
