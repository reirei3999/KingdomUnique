package com.kingdomunique.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** C2S: Gキーによる憑依切替通知（ペイロード本体は空）。 */
public record PossessionPayload() implements CustomPayload {

    public static final CustomPayload.Id<PossessionPayload> ID =
            new CustomPayload.Id<>(net.minecraft.util.Identifier.of("kingdomunique", "possession"));

    private static final PacketCodec<RegistryByteBuf, PossessionPayload> CODEC = CustomPayload.codecOf(
            (buf, payload) -> {
            },
            buf -> new PossessionPayload()
    );

    public static final CustomPayload.Type<? super RegistryByteBuf, PossessionPayload> TYPE =
            PayloadTypeRegistry.playC2S().register(ID, CODEC);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
