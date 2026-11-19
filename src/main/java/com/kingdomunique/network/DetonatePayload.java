package com.kingdomunique.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** C2S: 左クリックによる光の矢の起爆通知。 */
public record DetonatePayload() implements CustomPayload {

    public static final CustomPayload.Id<DetonatePayload> ID =
            new CustomPayload.Id<>(net.minecraft.util.Identifier.of("kingdomunique", "detonate"));

    private static final PacketCodec<RegistryByteBuf, DetonatePayload> CODEC = CustomPayload.codecOf(
            (buf, payload) -> {
            },
            buf -> new DetonatePayload()
    );

    public static final CustomPayload.Type<? super RegistryByteBuf, DetonatePayload> TYPE =
            PayloadTypeRegistry.playC2S().register(ID, CODEC);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
