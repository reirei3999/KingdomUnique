package com.kingdomunique.mixin;

import com.kingdomunique.item.unique.StormRaid;
import com.kingdomunique.item.unique.UniqueWeaponTriggers;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.raid.Raid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.UUID;

@Mixin(Raid.class)
public abstract class StormRaidMixin implements StormRaid {
    @Shadow @Final private ServerWorld world;
    @Shadow @Final private Set<UUID> heroesOfTheVillage;
    @Shadow public abstract boolean hasWon();

    @Unique private boolean ku$stormEligible;

    @Inject(method = "<init>(ILnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;)V",
            at = @At("RETURN"))
    private void ku$initStormRaid(int id, ServerWorld world, BlockPos center, CallbackInfo ci) {
        ku$stormEligible = world.isThundering();
    }

    @Inject(method = "<init>(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/nbt/NbtCompound;)V",
            at = @At("RETURN"))
    private void ku$loadStormRaid(ServerWorld world, NbtCompound nbt, CallbackInfo ci) {
        ku$stormEligible = nbt.getBoolean("KingdomUniqueStormRaid");
    }

    @Inject(method = "writeNbt", at = @At("RETURN"))
    private void ku$saveStormRaid(NbtCompound nbt, CallbackInfoReturnable<NbtCompound> cir) {
        cir.getReturnValue().putBoolean("KingdomUniqueStormRaid", ku$stormEligible);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void ku$checkThunder(CallbackInfo ci) {
        if (!world.isThundering()) {
            ku$stormEligible = false;
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void ku$rewardStormRaid(CallbackInfo ci) {
        if (ku$stormEligible && hasWon()) {
            ku$stormEligible = false;
            heroesOfTheVillage.stream().sorted()
                    .map(world::getEntity)
                    .filter(ServerPlayerEntity.class::isInstance)
                    .map(ServerPlayerEntity.class::cast)
                    .filter(player -> !player.isSpectator())
                    .findFirst()
                    .ifPresent(UniqueWeaponTriggers::onStormRaidWon);
        }
    }

    @Override
    public void ku$recordVillagerDeath() {
        ku$stormEligible = false;
    }
}
