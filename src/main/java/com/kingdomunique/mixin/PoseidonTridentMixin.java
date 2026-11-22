package com.kingdomunique.mixin;

import com.kingdomunique.item.unique.PoseidonWhirlpool;
import com.kingdomunique.item.unique.UniqueWeapons;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TridentEntity.class)
public abstract class PoseidonTridentMixin {
    @Inject(method = "onEntityHit", at = @At("HEAD"))
    private void kingdomUnique$entityWhirlpool(EntityHitResult hit, CallbackInfo ci) {
        kingdomUnique$whirlpool(hit.getPos());
    }

    @Inject(method = "onBlockHitEnchantmentEffects", at = @At("HEAD"))
    private void kingdomUnique$blockWhirlpool(ServerWorld world, BlockHitResult hit, ItemStack stack, CallbackInfo ci) {
        kingdomUnique$whirlpool(hit.getPos().add(Vec3d.of(hit.getSide().getVector()).multiply(0.1)));
    }

    private void kingdomUnique$whirlpool(Vec3d center) {
        TridentEntity trident = (TridentEntity) (Object) this;
        ItemStack stack = trident.getWeaponStack();
        if (trident.getWorld() instanceof ServerWorld world && stack != null
                && stack.isOf(UniqueWeapons.POSEIDON_TRIDENT) && trident.getOwner() != null
                && trident.addCommandTag("kingdomunique_poseidon_impact")) {
            PoseidonWhirlpool.start(world, trident.getOwner().getUuid(), center);
        }
    }
}
