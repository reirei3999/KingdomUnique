package com.kingdomunique.item;

import com.kingdomunique.kingdom.KingdomManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

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
            KingdomManager.enlistVillager(player, entity, null);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return TypedActionResult.success(user.getStackInHand(hand));
    }
}
