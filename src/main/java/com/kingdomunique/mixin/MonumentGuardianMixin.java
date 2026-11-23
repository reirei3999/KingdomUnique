package com.kingdomunique.mixin;

import com.kingdomunique.item.unique.UniqueWeaponTriggers;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.minecraft.structure.OceanMonumentGenerator$Piece")
public abstract class MonumentGuardianMixin {
    @ModifyArg(method = "spawnElderGuardian", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/StructureWorldAccess;spawnEntityAndPassengers(Lnet/minecraft/entity/Entity;)V"))
    private Entity ku$recordMonumentOrigin(Entity guardian) {
        UniqueWeaponTriggers.markMonumentGuardian(guardian);
        return guardian;
    }
}
