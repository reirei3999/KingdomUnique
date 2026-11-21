package com.kingdomunique.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C: 国名入力画面を開くよう指示する。 */
public record RequestKingdomNamePayload() implements CustomPayload {

    public static final Id<RequestKingdomNamePayload> ID =
            new Id<>(Identifier.of("kingdomunique", "request_kingdom_name"));

    private static final PacketCodec<RegistryByteBuf, RequestKingdomNamePayload> CODEC =
            CustomPayload.codecOf((payload, buf) -> {
            }, buf -> new RequestKingdomNamePayload());

    public static final Type<? super RegistryByteBuf, RequestKingdomNamePayload> TYPE =
            PayloadTypeRegistry.playS2C().register(ID, CODEC);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
