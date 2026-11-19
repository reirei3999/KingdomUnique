package com.kingdomunique.network;

import com.kingdomunique.registry.ContentRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: 能力／遺物／呪物の選択画面を任意に開く要求。 */
public record RequestSelectionPayload(ContentRegistry.Kind kind) implements CustomPayload {

    public static final Id<RequestSelectionPayload> ID =
            new Id<>(Identifier.of("kingdomunique", "request_selection"));

    private static final PacketCodec<RegistryByteBuf, RequestSelectionPayload> CODEC =
            CustomPayload.codecOf(
                    (payload, buf) -> buf.writeVarInt(payload.kind().ordinal()),
                    buf -> {
                        ContentRegistry.Kind[] kinds = ContentRegistry.Kind.values();
                        int index = buf.readVarInt();
                        return new RequestSelectionPayload(
                                kinds[index >= 0 && index < kinds.length ? index : 0]);
                    }
            );

    public static final Type<? super RegistryByteBuf, RequestSelectionPayload> TYPE =
            PayloadTypeRegistry.playC2S().register(ID, CODEC);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
