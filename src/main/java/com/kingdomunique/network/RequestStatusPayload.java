package com.kingdomunique.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: Kキー／コマンドによる確認画面の表示要求。 */
public record RequestStatusPayload() implements CustomPayload {

    public static final Id<RequestStatusPayload> ID =
            new Id<>(Identifier.of("kingdomunique", "request_status"));

    private static final PacketCodec<RegistryByteBuf, RequestStatusPayload> CODEC =
            CustomPayload.codecOf((payload, buf) -> {
            }, buf -> new RequestStatusPayload());

    public static final Type<? super RegistryByteBuf, RequestStatusPayload> TYPE =
            PayloadTypeRegistry.playC2S().register(ID, CODEC);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
