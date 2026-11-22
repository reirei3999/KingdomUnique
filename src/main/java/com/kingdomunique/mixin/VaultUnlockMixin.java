package com.kingdomunique.mixin;

import com.kingdomunique.item.unique.UniqueArmorTriggers;
import net.minecraft.block.BlockState;
import net.minecraft.block.VaultBlock;
import net.minecraft.block.entity.VaultBlockEntity;
import net.minecraft.block.vault.VaultConfig;
import net.minecraft.block.vault.VaultServerData;
import net.minecraft.block.vault.VaultSharedData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VaultBlockEntity.Server.class)
public abstract class VaultUnlockMixin {
    @Inject(method = "tryUnlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/vault/VaultServerData;markPlayerAsRewarded(Lnet/minecraft/entity/player/PlayerEntity;)V",
            shift = At.Shift.AFTER))
    private static void kingdomUnique$recordOminousVault(ServerWorld world, BlockPos pos, BlockState state,
            VaultConfig config, VaultServerData serverData, VaultSharedData sharedData,
            PlayerEntity player, ItemStack key, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity serverPlayer && state.get(VaultBlock.OMINOUS)) {
            UniqueArmorTriggers.onVaultUnlocked(serverPlayer, world, pos);
        }
    }
}
