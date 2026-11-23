package com.kingdomunique.mixin;

import com.kingdomunique.util.EnhancementUtil;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {
    @Inject(method = "updateItems", at = @At("HEAD"))
    private void kingdomUnique$migrateArmor(CallbackInfo ci) {
        var inventory = (PlayerInventory) (Object) this;
        if (inventory.player.getWorld().isClient()) {
            return;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            EnhancementUtil.migrateLegacy(inventory.getStack(slot));
        }
    }
}
