package com.kingdomunique.mixin;

import com.kingdomunique.selection.AncientCityChest;
import com.kingdomunique.selection.AncientCityRelicTrigger;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChestBlockEntity.class)
public abstract class AncientCityChestMixin implements AncientCityChest {
    @Unique private boolean kingdomunique$ancientCityLoot;

    @Override
    public void kingdomunique$markLootChest() {
        kingdomunique$ancientCityLoot = true;
        ((ChestBlockEntity) (Object) this).markDirty();
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void kingdomunique$readOrigin(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup, CallbackInfo ci) {
        kingdomunique$ancientCityLoot |= nbt.getBoolean("KingdomUniqueAncientCityLoot");
    }

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void kingdomunique$writeOrigin(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup, CallbackInfo ci) {
        nbt.putBoolean("KingdomUniqueAncientCityLoot", kingdomunique$ancientCityLoot);
    }

    @Inject(method = "onOpen", at = @At("TAIL"))
    private void kingdomunique$opened(PlayerEntity player, CallbackInfo ci) {
        if (kingdomunique$ancientCityLoot && player instanceof ServerPlayerEntity serverPlayer
                && !player.isSpectator()) {
            AncientCityRelicTrigger.onChestOpened(serverPlayer, ((ChestBlockEntity) (Object) this).getPos());
        }
    }
}
