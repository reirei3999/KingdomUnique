package com.kingdomunique.mixin;

import com.kingdomunique.component.ModDataComponents;
import com.kingdomunique.registry.UniqueContentRegistry;
import com.kingdomunique.util.EnhancementUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ThreadLocalRandom;

/** 神匠: クラフト時に攻撃力・攻撃速度・採掘速度をそれぞれ独立に抽選する。 */
@Mixin(Item.class)
public abstract class CraftBonusMixin {

    private static final double CHANCE = 0.30D;
    private static final double MIN_BONUS = 0.05D;
    private static final double MAX_BONUS = 0.30D;

    @Inject(method = "onCraftByPlayer", at = @At("HEAD"))
    private void kingdomUnique$divineSmithCraftBonus(ItemStack stack, net.minecraft.world.World world,
                                                      PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer)) {
            return;
        }
        if (!UniqueContentRegistry.DIVINE_SMITH.equals(UniqueContentRegistry.getAbility(serverPlayer))) {
            return;
        }
        if (!EnhancementUtil.isEnhanceable(stack)) {
            return;
        }
        if (stack.get(ModDataComponents.UNIQUE_WEAPON_ID) != null) {
            return;
        }

        float attack = roll();
        float speed = roll();
        float mining = roll();
        if (attack <= 0.0F && speed <= 0.0F && mining <= 0.0F) {
            return;
        }
        EnhancementUtil.applyDivineBonuses(stack, attack, speed, mining);
    }

    private static float roll() {
        if (ThreadLocalRandom.current().nextDouble() > CHANCE) {
            return 0.0F;
        }
        return (float) (MIN_BONUS + ThreadLocalRandom.current().nextDouble(MAX_BONUS - MIN_BONUS));
    }
}
