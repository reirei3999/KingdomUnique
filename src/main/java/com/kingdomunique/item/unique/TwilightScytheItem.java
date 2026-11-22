package com.kingdomunique.item.unique;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.server.network.ServerPlayerEntity;

public final class TwilightScytheItem extends SwordItem implements UniqueWeapon {
    public TwilightScytheItem(ToolMaterial material, Settings settings) {
        super(material, settings);
    }

    @Override
    public String getUniqueId() {
        return "twilight_scythe";
    }

    public boolean hit(ServerPlayerEntity player, LivingEntity target, DamageSource source, float amount) {
        float healthBefore = target.getHealth();
        float absorptionBefore = target.getAbsorptionAmount();
        boolean damaged = target.damage(source, amount + target.getMaxHealth() * 0.05F);
        if (damaged && UniqueWeaponTriggers.isOverworldNight(player.getWorld())) {
            float dealt = healthBefore - target.getHealth() + absorptionBefore - target.getAbsorptionAmount();
            if (dealt > 0) {
                player.heal(dealt * 0.10F);
            }
        }
        return damaged;
    }
}
