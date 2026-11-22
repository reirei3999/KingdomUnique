package com.kingdomunique.mixin;

import com.kingdomunique.selection.AncientCityChest;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.registry.RegistryKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LootableContainerBlockEntity.class)
public abstract class LootChestOriginMixin {
    @Inject(method = "setLootTable", at = @At("HEAD"))
    private void kingdomunique$rememberOrigin(RegistryKey<LootTable> table, CallbackInfo ci) {
        var self = (LootableContainerBlockEntity) (Object) this;
        if (this instanceof AncientCityChest chest
                && (LootTables.ANCIENT_CITY_CHEST.equals(table)
                || LootTables.ANCIENT_CITY_ICE_BOX_CHEST.equals(table)
                || LootTables.ANCIENT_CITY_CHEST.equals(self.getLootTable())
                || LootTables.ANCIENT_CITY_ICE_BOX_CHEST.equals(self.getLootTable()))) {
            chest.kingdomunique$markLootChest();
        }
    }
}
