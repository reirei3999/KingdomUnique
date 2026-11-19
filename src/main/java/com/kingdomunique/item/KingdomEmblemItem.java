package com.kingdomunique.item;

import com.kingdomunique.kingdom.KingdomManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/** 王国の証：村人に使用すると国民化し、役割を割り当てる。 */
public final class KingdomEmblemItem extends Item {

    public KingdomEmblemItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user,
                                    net.minecraft.entity.LivingEntity entity, Hand hand) {
        if (user.getWorld().isClient()) {
            return ActionResult.SUCCESS;
        }
        if (user instanceof net.minecraft.server.network.ServerPlayerEntity player) {
            KingdomManager.setKingdomCenter(player, entity.getBlockPos(), false);
            KingdomManager.enlistVillager(player, entity, null);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return TypedActionResult.success(user.getStackInHand(hand));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                              List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal(""));
        tooltip.add(Text.literal("【王国の証】").formatted(Formatting.GOLD, Formatting.BOLD));
        tooltip.add(Text.literal("  村人を右クリック: 国民化して役割を割り当てる")
                .formatted(Formatting.YELLOW));
        tooltip.add(Text.literal("  国民化した地点が王国の中心になる")
                .formatted(Formatting.AQUA));
        tooltip.add(Text.literal("  所持コスト: エメラルド1個 または パン2個")
                .formatted(Formatting.GRAY));
    }
}
