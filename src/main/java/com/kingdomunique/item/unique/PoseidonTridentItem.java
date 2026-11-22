package com.kingdomunique.item.unique;

import com.kingdomunique.KingdomUniqueMod;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public final class PoseidonTridentItem extends TridentItem implements UniqueWeapon {
    private static final int COOLDOWN = 400;
    private static final Identifier UNDERWATER_ID = Identifier.of(KingdomUniqueMod.MOD_ID, "poseidon_underwater");

    public PoseidonTridentItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public String getUniqueId() {
        return "poseidon_trident";
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (user instanceof ServerPlayerEntity player && player.getItemCooldownManager().isCoolingDown(this)) {
            return;
        }
        boolean throwing = user instanceof ServerPlayerEntity
                && getMaxUseTime(stack, user) - remainingUseTicks >= 10
                && stack.getDamage() < stack.getMaxDamage() - 1
                && EnchantmentHelper.getTridentSpinAttackStrength(stack, user) == 0;
        super.onStoppedUsing(stack, world, user, remainingUseTicks);
        if (throwing && user instanceof ServerPlayerEntity player) {
            player.getItemCooldownManager().set(this, COOLDOWN);
        }
    }

    public void tickHolder(ServerPlayerEntity player) {
        boolean active = !player.isSpectator() && player.isSubmergedInWater()
                && (player.getMainHandStack().isOf(this) || player.getOffHandStack().isOf(this));
        updateModifier(player, EntityAttributes.GENERIC_WATER_MOVEMENT_EFFICIENCY, 0.5,
                EntityAttributeModifier.Operation.ADD_VALUE, active);
        updateModifier(player, EntityAttributes.GENERIC_ATTACK_SPEED, 0.25,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, active);
    }

    private static void updateModifier(ServerPlayerEntity player, RegistryEntry<EntityAttribute> attribute,
                                       double amount, EntityAttributeModifier.Operation operation, boolean active) {
        var instance = player.getAttributeInstance(attribute);
        if (instance == null) {
            return;
        }
        if (active && !instance.hasModifier(UNDERWATER_ID)) {
            instance.addTemporaryModifier(new EntityAttributeModifier(UNDERWATER_ID, amount, operation));
        } else if (!active) {
            instance.removeModifier(UNDERWATER_ID);
        }
    }
}
