package com.kingdomunique.mixin;

import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.entity.VillagerProfessionUtil;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.village.VillagerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 国民のバニラ職業を役職に固定し、役職ブロックによる上書きと再 Stock を防ぐ。 */
@Mixin(VillagerEntity.class)
public abstract class VillagerProfessionMixin {

    @Inject(method = "setVillagerData", at = @At("HEAD"), cancellable = true)
    private void kingdomUnique$lockProfession(VillagerData data, CallbackInfo ci) {
        VillagerEntity self = (VillagerEntity) (Object) this;
        if (!(self instanceof CitizenVillager citizen)) {
            return;
        }
        String role = citizen.ku$getRole();
        if (role == null || role.isEmpty()) {
            return;
        }
        if (data.getProfession() != VillagerProfessionUtil.professionFor(role)) {
            self.setVillagerData(data.withProfession(
                    VillagerProfessionUtil.professionFor(role)));
            ci.cancel();
        }
    }

    /** 建築士（取引不可）は再 Stock させない。 */
    @Inject(method = "restock", at = @At("HEAD"), cancellable = true)
    private void kingdomUnique$noRestockForBuilder(CallbackInfo ci) {
        VillagerEntity self = (VillagerEntity) (Object) this;
        if (self instanceof CitizenVillager citizen
                && CitizenVillager.ROLE_BUILDER.equals(citizen.ku$getRole())) {
            ci.cancel();
        }
    }

    /** 鍛冶士は再 Stock 後に鉱石系の取引だけを残す。 */
    @Inject(method = "restock", at = @At("RETURN"))
    private void kingdomUnique$filterSmithTrades(CallbackInfo ci) {
        VillagerEntity self = (VillagerEntity) (Object) this;
        if (self instanceof CitizenVillager citizen
                && (CitizenVillager.ROLE_SMITH.equals(citizen.ku$getRole())
                || CitizenVillager.ROLE_FARMER.equals(citizen.ku$getRole()))) {
            VillagerProfessionUtil.applyRole(self, CitizenVillager.ROLE_SMITH);
        }
    }

    /** 取引画面を開く直前にバニラが职业取引を再生成するため、王国仕様へ上書きする。 */
    @Inject(method = "prepareOffersFor", at = @At("RETURN"))
    private void kingdomUnique$prepareCustomOffers(PlayerEntity player, CallbackInfo ci) {
        VillagerEntity self = (VillagerEntity) (Object) this;
        if (self instanceof CitizenVillager citizen) {
            String role = citizen.ku$getRole();
            if (role != null && !role.isEmpty()) {
                VillagerProfessionUtil.applyRole(self, role);
            }
        }
    }
}
