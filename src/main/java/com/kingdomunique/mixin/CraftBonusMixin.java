package com.kingdomunique.mixin;

import com.kingdomunique.component.ModDataComponents;
import com.kingdomunique.registry.UniqueContentRegistry;
import com.kingdomunique.util.EnhancementUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ThreadLocalRandom;

/** 神匠: クラフトしたアイテム種別に応じてボーナスを1種類だけ抽選する。 */
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
        if (!UniqueContentRegistry.has(serverPlayer, UniqueContentRegistry.DIVINE_SMITH)) {
            return;
        }
        if (!EnhancementUtil.isEnhanceable(stack)) {
            return;
        }
        if (stack.get(ModDataComponents.UNIQUE_WEAPON_ID) != null) {
            return;
        }

        Item item = stack.getItem();
        float attack = 0.0F;
        float speed = 0.0F;
        float mining = 0.0F;
        float armor = 0.0F;
        float toughness = 0.0F;

        if (item instanceof ArmorItem) {
            // (C) 防具: 防御力 または 防具強度 のみ。
            if (ThreadLocalRandom.current().nextBoolean()) {
                armor = roll();
            } else {
                toughness = roll();
            }
        } else if (item instanceof PickaxeItem || item instanceof ShovelItem || item instanceof HoeItem) {
            // (B) 採掘ツール: 採掘速度のみ。攻撃力・攻撃速度は一切付かない。
            mining = roll();
        } else if (item instanceof SwordItem || item instanceof AxeItem
                || item instanceof TridentItem || item instanceof MaceItem
                || item instanceof net.minecraft.item.RangedWeaponItem) {
            // (A) 武器: 攻撃力 または 攻撃速度 のみ。
            if (ThreadLocalRandom.current().nextBoolean()) {
                attack = roll();
            } else {
                speed = roll();
            }
        } else {
            return;
        }

        if (attack <= 0.0F && speed <= 0.0F && mining <= 0.0F
                && armor <= 0.0F && toughness <= 0.0F) {
            return;
        }
        EnhancementUtil.applyDivineBonuses(stack, attack, speed, mining, armor, toughness);
    }

    private static float roll() {
        if (ThreadLocalRandom.current().nextDouble() > CHANCE) {
            return 0.0F;
        }
        return (float) (MIN_BONUS + ThreadLocalRandom.current().nextDouble(MAX_BONUS - MIN_BONUS));
    }
}
