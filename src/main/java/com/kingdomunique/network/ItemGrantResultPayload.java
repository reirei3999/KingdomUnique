package com.kingdomunique.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ItemGrantResultPayload(String itemId, long requestId, boolean success, String itemName, int quantity,
                                     String reason) implements CustomPayload {
    public static final CustomPayload.Id<ItemGrantResultPayload> ID =
            new CustomPayload.Id<>(Identifier.of("kingdomunique", "item_grant_result"));

    private static final PacketCodec<RegistryByteBuf, ItemGrantResultPayload> CODEC = CustomPayload.codecOf(
            (payload, buf) -> {
                buf.writeString(payload.itemId());
                buf.writeLong(payload.requestId());
                buf.writeBoolean(payload.success());
                buf.writeString(payload.itemName());
                buf.writeVarInt(payload.quantity());
                buf.writeString(payload.reason());
            },
            buf -> new ItemGrantResultPayload(buf.readString(), buf.readLong(), buf.readBoolean(), buf.readString(),
                    buf.readVarInt(), buf.readString())
    );

    public static final CustomPayload.Type<? super RegistryByteBuf, ItemGrantResultPayload> TYPE =
            PayloadTypeRegistry.playS2C().register(ID, CODEC);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
