package com.kingdomunique.item.unique;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.TridentItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public final class TrishulaItem extends TridentItem implements UniqueWeapon {
    public TrishulaItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public String getUniqueId() {
        return "trishula";
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }
        if (user instanceof ServerPlayerEntity player) {
            TrishulaEntity projectile = new TrishulaEntity(world, player, stack.copy());
            projectile.setVelocity(player.getRotationVec(1.0F).multiply(3.0D));
            world.spawnEntity(projectile);
            player.getItemCooldownManager().set(this, 400);
        }
        return TypedActionResult.consume(stack);
    }
}
