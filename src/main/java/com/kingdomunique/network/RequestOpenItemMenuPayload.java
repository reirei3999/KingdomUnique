package com.kingdomunique.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: Iキーによるアイテムメニュー開放要求（権限はサーバー側で検証）。 */
public record RequestOpenItemMenuPayload() implements CustomPayload {

    public static final CustomPayload.Id<RequestOpenItemMenuPayload> ID =
            new CustomPayload.Id<>(Identifier.of("kingdomunique", "request_open_item_menu"));

    private static final PacketCodec<RegistryByteBuf, RequestOpenItemMenuPayload> CODEC = CustomPayload.codecOf(
            (payload, buf) -> {
            },
            buf -> new RequestOpenItemMenuPayload()
    );

    public static final CustomPayload.Type<? super RegistryByteBuf, RequestOpenItemMenuPayload> TYPE =
            PayloadTypeRegistry.playC2S().register(ID, CODEC);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
