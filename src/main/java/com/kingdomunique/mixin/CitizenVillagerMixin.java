package com.kingdomunique.mixin;

import com.kingdomunique.entity.CitizenVillager;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
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

    /** 兵士の武器ロック（Gemini 提示: バニラAIによる消去を防ぐ）。 */
    @Unique
    private ItemStack kingdomUnique$lockedWeapon = ItemStack.EMPTY;

    @Override
    public void ku$setLockedWeapon(ItemStack stack) {
        this.kingdomUnique$lockedWeapon = (stack == null || stack.isEmpty())
                ? ItemStack.EMPTY : stack.copy();
        ((VillagerEntity) (Object) this).equipStack(EquipmentSlot.MAINHAND,
                this.kingdomUnique$lockedWeapon.copy());
    }

    @Override
    public ItemStack ku$getLockedWeapon() {
        return this.kingdomUnique$lockedWeapon;
    }

    /** 毎tick: バニラAIが武器を消したらロック武器を即座に復元する。 */
    @Inject(method = "tick", at = @At("TAIL"))
    private void kingdomUnique$keepSoldierWeapon(CallbackInfo ci) {
        VillagerEntity self = (VillagerEntity) (Object) this;
        if (self.getWorld().isClient() || !CitizenVillager.ROLE_SOLDIER.equals(kingdomUnique$role)) {
            return;
        }
        self.setCanPickUpLoot(false);
        ItemStack current = self.getEquippedStack(EquipmentSlot.MAINHAND);
        if (!kingdomUnique$lockedWeapon.isEmpty()
                && !ItemStack.areItemsAndComponentsEqual(current, kingdomUnique$lockedWeapon)) {
            self.equipStack(EquipmentSlot.MAINHAND, kingdomUnique$lockedWeapon.copy());
        }
    }

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
        if (!kingdomUnique$lockedWeapon.isEmpty()) {
            VillagerEntity self = (VillagerEntity) (Object) this;
            nbt.put("ku_locked_weapon",
                    kingdomUnique$lockedWeapon.encode(self.getRegistryManager()));
        }
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("HEAD"))
    private void kingdomUnique$readCitizenData(NbtCompound nbt, CallbackInfo ci) {
        kingdomUnique$role = nbt.getString("ku_role");
        kingdomUnique$ownerUuid = nbt.getString("ku_owner");
        if (nbt.contains("ku_locked_weapon", NbtElement.COMPOUND_TYPE)) {
            VillagerEntity self = (VillagerEntity) (Object) this;
            kingdomUnique$lockedWeapon = ItemStack.fromNbt(self.getRegistryManager(),
                    nbt.getCompound("ku_locked_weapon")).orElse(ItemStack.EMPTY);
            if (!kingdomUnique$lockedWeapon.isEmpty()) {
                self.equipStack(EquipmentSlot.MAINHAND, kingdomUnique$lockedWeapon.copy());
            }
        }
    }
}
