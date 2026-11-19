package com.kingdomunique.item.unique;

import com.kingdomunique.item.unique.UniqueWeaponManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** 雷神銃ミョルニル。 */
public final class MjolnirItem extends SwordItem implements UniqueWeapon {

    /** 25秒 = 500ティック。 */
    public static final int COOLDOWN_TICKS = 500;

    public MjolnirItem(ToolMaterial material, Settings settings) {
        super(material, settings);
    }

    @Override
    public String getUniqueId() {
        return "mjolnir";
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient()) {
            if (user.getItemCooldownManager().isCoolingDown(this)) {
                return TypedActionResult.fail(stack);
            }
            if (user instanceof net.minecraft.server.network.ServerPlayerEntity player) {
                UniqueWeaponManager.fireLightning(player);
            }
            user.getItemCooldownManager().set(this, COOLDOWN_TICKS);
        }
        return TypedActionResult.consume(stack);
    }
}
