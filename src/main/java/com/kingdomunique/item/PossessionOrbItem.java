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

/** 憑依のオーブ：刺客英雄への乗り移りを切り替える。 */
public final class PossessionOrbItem extends Item {

    public PossessionOrbItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient()) {
            if (user instanceof net.minecraft.server.network.ServerPlayerEntity player) {
                KingdomManager.togglePossession(player);
            }
        }
        return TypedActionResult.success(user.getStackInHand(hand));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                              List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal(""));
        tooltip.add(Text.literal("【憑依のオーブ】").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
        tooltip.add(Text.literal("  右クリック: 近くの刺客英雄に意識を移す")
                .formatted(Formatting.YELLOW));
        tooltip.add(Text.literal("  憑依中に右クリック / Gキーで本体へ戻る")
                .formatted(Formatting.AQUA));
    }
}
