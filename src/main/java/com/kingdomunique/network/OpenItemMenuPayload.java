package com.kingdomunique.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C: 専用アイテムメニューを開く。 */
public record OpenItemMenuPayload() implements CustomPayload {

    public static final CustomPayload.Id<OpenItemMenuPayload> ID =
            new CustomPayload.Id<>(Identifier.of("kingdomunique", "open_item_menu"));

    private static final PacketCodec<RegistryByteBuf, OpenItemMenuPayload> CODEC = CustomPayload.codecOf(
            (payload, buf) -> {
            },
            buf -> new OpenItemMenuPayload()
    );

    public static final CustomPayload.Type<? super RegistryByteBuf, OpenItemMenuPayload> TYPE =
            PayloadTypeRegistry.playS2C().register(ID, CODEC);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
