package com.kingdomunique.network;

import com.kingdomunique.registry.ContentRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

public record SelectionResultPayload(ContentRegistry.Kind kind, boolean accepted, String message,
                                     List<String> optionIds) implements CustomPayload {
    public static final CustomPayload.Id<SelectionResultPayload> ID =
            new CustomPayload.Id<>(net.minecraft.util.Identifier.of("kingdomunique", "selection_result"));

    private static final PacketCodec<RegistryByteBuf, SelectionResultPayload> CODEC = CustomPayload.codecOf(
            (payload, buf) -> {
                buf.writeVarInt(payload.kind().ordinal());
                buf.writeBoolean(payload.accepted());
                buf.writeString(payload.message());
                buf.writeVarInt(payload.optionIds().size());
                for (String id : payload.optionIds()) buf.writeString(id);
            },
            buf -> {
                ContentRegistry.Kind[] kinds = ContentRegistry.Kind.values();
                int index = buf.readVarInt();
                ContentRegistry.Kind kind = kinds[index >= 0 && index < kinds.length ? index : 0];
                boolean accepted = buf.readBoolean();
                String message = buf.readString();
                int size = buf.readVarInt();
                List<String> ids = new ArrayList<>();
                for (int i = 0; i < size; i++) ids.add(buf.readString());
                return new SelectionResultPayload(kind, accepted, message, ids);
            });

    public static final CustomPayload.Type<? super RegistryByteBuf, SelectionResultPayload> TYPE =
            PayloadTypeRegistry.playS2C().register(ID, CODEC);

    public static void initialize() {
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
