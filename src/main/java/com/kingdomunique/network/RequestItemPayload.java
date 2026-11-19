package com.kingdomunique.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: メニューで選んだ専用アイテムの要求。 */
public record RequestItemPayload(String itemId) implements CustomPayload {

    public static final CustomPayload.Id<RequestItemPayload> ID =
            new CustomPayload.Id<>(Identifier.of("kingdomunique", "request_item"));

    private static final PacketCodec<RegistryByteBuf, RequestItemPayload> CODEC = CustomPayload.codecOf(
            (payload, buf) -> buf.writeString(payload.itemId()),
            buf -> new RequestItemPayload(buf.readString())
    );

    public static final CustomPayload.Type<? super RegistryByteBuf, RequestItemPayload> TYPE =
            PayloadTypeRegistry.playC2S().register(ID, CODEC);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
