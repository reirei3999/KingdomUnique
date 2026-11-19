package com.kingdomunique.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** クライアント → サーバーへの能力操作通知。 */
public record AbilityKeyPayload(Action action) implements CustomPayload {

    public static final CustomPayload.Id<AbilityKeyPayload> ID =
            new CustomPayload.Id<>(net.minecraft.util.Identifier.of("kingdomunique", "ability_key"));

    private static final PacketCodec<RegistryByteBuf, AbilityKeyPayload> CODEC = CustomPayload.codecOf(
            (payload, buf) -> buf.writeVarInt(payload.action().ordinal()),
            buf -> new AbilityKeyPayload(Action.fromOrdinal(buf.readVarInt()))
    );

    public static final CustomPayload.Type<? super RegistryByteBuf, AbilityKeyPayload> TYPE =
            PayloadTypeRegistry.playC2S().register(ID, CODEC);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public enum Action {
        ACTIVATE,
        MODE_SWITCH;

        public static Action fromOrdinal(int ordinal) {
            Action[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ACTIVATE;
        }
    }
}
