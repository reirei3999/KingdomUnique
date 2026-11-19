package com.kingdomunique.mixin;

import com.kingdomunique.entity.CitizenVillager;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** バニラ村人に国民データを付与する（軽量・既存エンティティ再利用）。 */
@Mixin(VillagerEntity.class)
public abstract class CitizenVillagerMixin implements CitizenVillager {

    @Unique
    private String kingdomUnique$role = "";

    @Unique
    private String kingdomUnique$ownerUuid = "";

    @Override
    public String ku$getRole() {
        return kingdomUnique$role;
    }

    @Override
    public void ku$setRole(String role) {
        kingdomUnique$role = role == null ? "" : role;
    }

    @Override
    public String ku$getOwnerUuid() {
        return kingdomUnique$ownerUuid;
    }

    @Override
    public void ku$setOwnerUuid(String uuid) {
        kingdomUnique$ownerUuid = uuid == null ? "" : uuid;
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("HEAD"))
    private void kingdomUnique$writeCitizenData(NbtCompound nbt, CallbackInfo ci) {
        nbt.putString("ku_role", kingdomUnique$role);
        nbt.putString("ku_owner", kingdomUnique$ownerUuid);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("HEAD"))
    private void kingdomUnique$readCitizenData(NbtCompound nbt, CallbackInfo ci) {
        kingdomUnique$role = nbt.getString("ku_role");
        kingdomUnique$ownerUuid = nbt.getString("ku_owner");
    }
}
