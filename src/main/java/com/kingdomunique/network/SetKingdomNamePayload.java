package com.kingdomunique.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: プレイヤーが決定した国名。 */
public record SetKingdomNamePayload(String name) implements CustomPayload {

    public static final Id<SetKingdomNamePayload> ID =
            new Id<>(Identifier.of("kingdomunique", "set_kingdom_name"));

    private static final PacketCodec<RegistryByteBuf, SetKingdomNamePayload> CODEC =
            CustomPayload.codecOf(
                    (payload, buf) -> buf.writeString(payload.name()),
                    buf -> new SetKingdomNamePayload(buf.readString())
            );

    public static final Type<? super RegistryByteBuf, SetKingdomNamePayload> TYPE =
            PayloadTypeRegistry.playC2S().register(ID, CODEC);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
