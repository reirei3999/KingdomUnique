package com.kingdomunique.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * 英雄召喚の角笛：英雄召喚の祭壇（中央のロードストーン）で使ったときだけ英雄を召喚する。
 * 祭壇での処理は SanctuaryInteractHandler が行い、それ以外の場所では案内を表示するだけ。
 */
public final class HeroSummonHornItem extends Item {

    public HeroSummonHornItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient()) {
            user.sendMessage(Text.literal(
                    "§e[英雄召喚の角笛] 角笛はここでは響かない……。英雄召喚の祭壇の中央にあるロードストーンを、角笛を持って右クリックしてください。"),
                    false);
        }
        return TypedActionResult.fail(user.getStackInHand(hand));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                              List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal(""));
        tooltip.add(Text.literal("【英雄召喚の角笛】").formatted(Formatting.GOLD, Formatting.BOLD));
        tooltip.add(Text.literal("  英雄召喚の祭壇のロードストーンで使うと英雄を1体召喚する")
                .formatted(Formatting.YELLOW));
        tooltip.add(Text.literal("  1王国につき1回まで（軍旗で2回まで拡張）")
                .formatted(Formatting.AQUA));
        tooltip.add(Text.literal("  召喚に成功すると角笛は砕ける")
                .formatted(Formatting.GRAY));
    }
}
