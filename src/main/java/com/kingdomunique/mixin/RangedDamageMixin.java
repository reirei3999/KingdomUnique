package com.kingdomunique.mixin;

import com.kingdomunique.component.ModDataComponents;
import com.kingdomunique.registry.UniqueContentRegistry;
import com.kingdomunique.util.EnhancementUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 弓・クロスボウなどの遠距離攻撃へ無限強化倍率を反映する。 */
@Mixin(LivingEntity.class)
public abstract class RangedDamageMixin {

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float kingdomUnique$rangedDamage(float amount, DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;

        // アポロンの光の矢はアンデッド系にのみ2倍ダメージ。
        if (source.getSource() instanceof net.minecraft.entity.projectile.ArrowEntity arrow
                && arrow.getCommandTags().contains("ku_light_arrow")
                && self.getType().isIn(net.minecraft.registry.tag.EntityTypeTags.UNDEAD)) {
            amount *= 2.0F;
        }

        if (!(source.getSource() instanceof PersistentProjectileEntity)
                || !(source.getAttacker() instanceof ServerPlayerEntity player)) {
            return amount;
        }

        ItemStack held = player.getMainHandStack();
        if (held.isEmpty()) {
            return amount;
        }

        boolean ranged = held.getItem() instanceof RangedWeaponItem
                || held.isOf(net.minecraft.item.Items.TRIDENT);
        if (ranged) {
            int level = held.getOrDefault(ModDataComponents.ENHANCE_LEVEL, 0);
            if (level >= 1) {
                amount *= (float) EnhancementUtil.multiplier(level);
            }
        }

        // 覇王: 遠距離武器でも 1.3 倍（近接は属性側で反映済み）。
        if (UniqueContentRegistry.CONQUEROR.equals(UniqueContentRegistry.getAbility(player))) {
            amount *= 1.3F;
        }
        return amount;
    }
}
