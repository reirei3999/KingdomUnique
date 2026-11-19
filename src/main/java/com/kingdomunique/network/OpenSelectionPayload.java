package com.kingdomunique.network;

import com.kingdomunique.registry.ContentRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

/** サーバー → クライアント：選択GUIを開き、抽選済みの選択肢を渡す。 */
public record OpenSelectionPayload(ContentRegistry.Kind kind, List<String> optionIds) implements CustomPayload {

    public static final CustomPayload.Id<OpenSelectionPayload> ID =
            new CustomPayload.Id<>(net.minecraft.util.Identifier.of("kingdomunique", "open_selection"));

    private static final PacketCodec<RegistryByteBuf, OpenSelectionPayload> CODEC = CustomPayload.codecOf(
            (payload, buf) -> {
                buf.writeVarInt(payload.kind().ordinal());
                buf.writeVarInt(payload.optionIds().size());
                for (String id : payload.optionIds()) {
                    buf.writeString(id);
                }
            },
            buf -> {
                ContentRegistry.Kind[] kinds = ContentRegistry.Kind.values();
                int kindIndex = buf.readVarInt();
                ContentRegistry.Kind kind = kinds[kindIndex >= 0 && kindIndex < kinds.length ? kindIndex : 0];
                int size = buf.readVarInt();
                List<String> ids = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    ids.add(buf.readString());
                }
                return new OpenSelectionPayload(kind, ids);
            }
    );

    public static final CustomPayload.Type<? super RegistryByteBuf, OpenSelectionPayload> TYPE =
            PayloadTypeRegistry.playS2C().register(ID, CODEC);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
