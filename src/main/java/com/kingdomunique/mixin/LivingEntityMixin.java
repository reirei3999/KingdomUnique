package com.kingdomunique.mixin;

import com.kingdomunique.ability.AbilityPassives;
import com.kingdomunique.ability.AbilityState;
import com.kingdomunique.ability.AlliedMobManager;
import com.kingdomunique.effect.CurseEffects;
import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.kingdom.PolicyManager;
import com.kingdomunique.registry.UniqueContentRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 能力によるダメージ補正とダメージ時刻の記録。 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float kingdomUnique$modifyDamage(float amount, DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;
        LivingEntity attacker = source.getAttacker() instanceof LivingEntity living ? living : null;

        // 味方化Mobはプレイヤーを傷つけない。
        if (self instanceof ServerPlayerEntity && attacker instanceof MobEntity
                && !AlliedMobManager.canAttackPlayer((MobEntity) attacker, self)) {
            return 0.0F;
        }

        // 兵士NPCの攻撃力へ王国政策（富国強兵令/殖産興業令）と軍旗を反映する。
        if (attacker instanceof CitizenVillager soldier && !self.getWorld().isClient()) {
            String owner = soldier.ku$getOwnerUuid();
            if (!owner.isEmpty()) {
                try {
                    amount *= (float) PolicyManager.soldierAttackMultiplier(
                            (net.minecraft.server.world.ServerWorld) self.getWorld(),
                            java.util.UUID.fromString(owner));
                } catch (IllegalArgumentException ignored) {
                    // 不正なUUIDは無視する。
                }
            }
        }

        if (attacker instanceof ServerPlayerEntity player && !CurseEffects.isEnvyReflection(source)) {
            if (UniqueContentRegistry.has(player, UniqueContentRegistry.CONQUEROR)) {
                amount *= 1.3F;
            }
            if (UniqueContentRegistry.has(player, UniqueContentRegistry.SHADOW_STEP)
                    && AbilityState.get(player.getUuid()).isShadowDamageBuff()) {
                amount *= 1.5F;
                AbilityState.get(player.getUuid()).setShadowDamageBuff(false);
            }
        }
        return amount;
    }

    @Inject(method = "damage", at = @At("RETURN"))
    private void kingdomUnique$recordDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayerEntity player && amount > 0.0F && cir.getReturnValue()) {
            AbilityPassives.markDamaged(player);
        }
    }
}
