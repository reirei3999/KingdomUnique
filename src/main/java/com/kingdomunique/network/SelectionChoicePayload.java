package com.kingdomunique.network;

import com.kingdomunique.registry.ContentRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** クライアント → サーバー：選択されたIDを通知する。 */
public record SelectionChoicePayload(ContentRegistry.Kind kind, String chosenId) implements CustomPayload {

    public static final CustomPayload.Id<SelectionChoicePayload> ID =
            new CustomPayload.Id<>(net.minecraft.util.Identifier.of("kingdomunique", "selection_choice"));

    private static final PacketCodec<RegistryByteBuf, SelectionChoicePayload> CODEC = CustomPayload.codecOf(
            (payload, buf) -> {
                buf.writeVarInt(payload.kind().ordinal());
                buf.writeString(payload.chosenId());
            },
            buf -> {
                ContentRegistry.Kind[] kinds = ContentRegistry.Kind.values();
                int index = buf.readVarInt();
                ContentRegistry.Kind kind = kinds[index >= 0 && index < kinds.length ? index : 0];
                return new SelectionChoicePayload(kind, buf.readString());
            }
    );

    public static final CustomPayload.Type<? super RegistryByteBuf, SelectionChoicePayload> TYPE =
            PayloadTypeRegistry.playC2S().register(ID, CODEC);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
