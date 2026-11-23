package com.kingdomunique.mixin;

import com.kingdomunique.item.unique.UniqueArmors;
import com.kingdomunique.war.KingdomDiplomacyState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerDamageMixin {
    @ModifyVariable(method = "addExperience", at = @At("HEAD"), argsOnly = true)
    private int kingdomUnique$doubleExperience(int points) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        return points > 0 && UniqueArmors.isWearing(player, UniqueArmors.MIMIR_HELMET)
                ? (int) Math.min((long) points * 2, Integer.MAX_VALUE - player.totalExperience) : points;
    }

    @Inject(method = "shouldDamagePlayer", at = @At("HEAD"), cancellable = true)
    private void kingdomUnique$blockAlliedPlayerDamage(PlayerEntity other, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (!player.getUuid().equals(other.getUuid())
                && KingdomDiplomacyState.get(player.getServerWorld()).isAlly(player.getUuid(), other.getUuid())) {
            cir.setReturnValue(false);
        }
    }
}
