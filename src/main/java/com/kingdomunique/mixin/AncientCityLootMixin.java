package com.kingdomunique.mixin;

import com.kingdomunique.selection.SelectionManager;
import net.minecraft.advancement.criterion.PlayerGeneratesContainerLootCriterion;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 古代都市の宝箱（未開封のルート生成時）を開けたプレイヤーに能力選択を出す。 */
@Mixin(PlayerGeneratesContainerLootCriterion.class)
public abstract class AncientCityLootMixin {
    @Inject(method = "trigger", at = @At("TAIL"))
    private void kingdomUnique$ancientCityChest(ServerPlayerEntity player, RegistryKey<LootTable> lootTable,
                                                CallbackInfo ci) {
        if (LootTables.ANCIENT_CITY_CHEST.equals(lootTable) || LootTables.ANCIENT_CITY_ICE_BOX_CHEST.equals(lootTable)) {
            SelectionManager.onAncientCityChestOpened(player);
        }
    }
}
