package com.kingdomunique.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TalariaJumpPayload() implements CustomPayload {
    public static final Id<TalariaJumpPayload> ID = new Id<>(Identifier.of("kingdomunique", "talaria_jump"));

    static {
        PayloadTypeRegistry.playC2S().register(ID, PacketCodec.unit(new TalariaJumpPayload()));
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
