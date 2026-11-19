package com.kingdomunique.item.unique;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** 魔槍ゲイボルグ。手元の本体は残したまま、投擲エンティティのみを生成する。 */
public final class GaeBolgItem extends TridentItem implements UniqueWeapon {

    public static final int FULL_COOLDOWN = 720;   // 36秒
    public static final int KILL_COOLDOWN = 360;   // 18秒

    public GaeBolgItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public String getUniqueId() {
        return "gae_bolg";
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient()) {
            if (user.getItemCooldownManager().isCoolingDown(this)) {
                return TypedActionResult.fail(stack);
            }
            if (user instanceof net.minecraft.server.network.ServerPlayerEntity player) {
                UniqueWeaponManager.throwGaeBolg(player);
            }
            user.getItemCooldownManager().set(this, FULL_COOLDOWN);
        }
        return TypedActionResult.consume(stack);
    }
}
