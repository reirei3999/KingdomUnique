package com.kingdomunique.item;

import com.kingdomunique.kingdom.KingdomManager;
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

/** 英雄召喚の角笛：1王国1回（軍旗で2回）だけ英雄を召喚する。 */
public final class HeroSummonHornItem extends Item {

    public HeroSummonHornItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient() && user instanceof net.minecraft.server.network.ServerPlayerEntity player
                && KingdomManager.summonHero(player) && !player.isCreative()) {
            // 召喚に成功したら角笛は砕ける（召喚回数の上限とは別に、1本につき1回）。
            stack.decrement(1);
        }
        return TypedActionResult.success(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                              List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal(""));
        tooltip.add(Text.literal("【英雄召喚の角笛】").formatted(Formatting.GOLD, Formatting.BOLD));
        tooltip.add(Text.literal("  右クリック: 王国に英雄を1体召喚する")
                .formatted(Formatting.YELLOW));
        tooltip.add(Text.literal("  1王国につき1回まで（軍旗で2回まで拡張）")
                .formatted(Formatting.AQUA));
        tooltip.add(Text.literal("  召喚に成功すると角笛は砕ける")
                .formatted(Formatting.GRAY));
    }
}
