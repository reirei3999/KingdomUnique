package com.kingdomunique.trade;

import com.kingdomunique.KingdomUniqueMod;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.s2c.play.SetTradeOffersS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.village.SimpleMerchant;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;

public final class CitizenMerchantScreenHandler extends MerchantScreenHandler {
    public record CitizenInfo(String kingdom, String role, String profession, int level,
                              int professionXp, int reputation) {
        public static final PacketCodec<RegistryByteBuf, CitizenInfo> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, CitizenInfo::kingdom,
                PacketCodecs.STRING, CitizenInfo::role,
                PacketCodecs.STRING, CitizenInfo::profession,
                PacketCodecs.VAR_INT, CitizenInfo::level,
                PacketCodecs.VAR_INT, CitizenInfo::professionXp,
                PacketCodecs.VAR_INT, CitizenInfo::reputation,
                CitizenInfo::new);
    }

    public static final ExtendedScreenHandlerType<CitizenMerchantScreenHandler, CitizenInfo> TYPE =
            Registry.register(Registries.SCREEN_HANDLER, Identifier.of(KingdomUniqueMod.MOD_ID, "citizen_merchant"),
                    new ExtendedScreenHandlerType<>(CitizenMerchantScreenHandler::new, CitizenInfo.CODEC));

    public static void initialize() {
    }

    private final CitizenInfo citizenInfo;

    private CitizenMerchantScreenHandler(int syncId, PlayerInventory inventory, CitizenInfo citizenInfo) {
        super(syncId, inventory);
        this.citizenInfo = citizenInfo;
    }

    public CitizenMerchantScreenHandler(int syncId, PlayerInventory inventory, CitizenInfo citizenInfo,
                                       TradeOfferList offers) {
        super(syncId, inventory, new SimpleMerchant(inventory.player) {
            @Override
            public void trade(TradeOffer offer) {
                super.trade(offer);
                if (inventory.player instanceof ServerPlayerEntity player
                        && player.currentScreenHandler instanceof CitizenMerchantScreenHandler handler) {
                    handler.sendOffers(player);
                }
            }
        });
        this.citizenInfo = citizenInfo;
        setOffers(offers);
    }

    @Override
    public ScreenHandlerType<?> getType() {
        return TYPE;
    }

    public CitizenInfo getCitizenInfo() {
        return citizenInfo;
    }

    public void sendOffers(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new SetTradeOffersS2CPacket(syncId, getRecipes(), 0, 0, false, false));
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        if (index != OUTPUT_ID) {
            return super.quickMove(player, index);
        }
        // Vanilla's result transfer plays a sound by casting its merchant to an Entity.
        Slot result = getSlot(index);
        ItemStack stack = result.getStack();
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack original = stack.copy();
        if (!insertItem(stack, OUTPUT_ID + 1, slots.size(), true)) {
            return ItemStack.EMPTY;
        }
        result.onQuickTransfer(stack, original);
        if (stack.isEmpty()) {
            result.setStack(ItemStack.EMPTY);
        } else {
            result.markDirty();
        }
        result.onTakeItem(player, stack);
        return original;
    }
}
