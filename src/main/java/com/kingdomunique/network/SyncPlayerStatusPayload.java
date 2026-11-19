package com.kingdomunique.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** S2C: 所持ID一覧・能力CT・採掘範囲を同期し、確認画面の表示も指示する。 */
public record SyncPlayerStatusPayload(List<String> ownedIds, String abilityId,
                                      int cooldownTicks, int miningRange,
                                      boolean openScreen) implements CustomPayload {

    public static final Id<SyncPlayerStatusPayload> ID =
            new Id<>(Identifier.of("kingdomunique", "sync_player_status"));

    private static final PacketCodec<RegistryByteBuf, SyncPlayerStatusPayload> CODEC =
            CustomPayload.codecOf(
                    (payload, buf) -> {
                        buf.writeVarInt(payload.ownedIds().size());
                        for (String id : payload.ownedIds()) {
                            buf.writeString(id);
                        }
                        buf.writeString(payload.abilityId() == null ? "" : payload.abilityId());
                        buf.writeVarInt(payload.cooldownTicks());
                        buf.writeVarInt(payload.miningRange());
                        buf.writeBoolean(payload.openScreen());
                    },
                    buf -> {
                        int size = buf.readVarInt();
                        List<String> ids = new ArrayList<>();
                        for (int i = 0; i < size; i++) {
                            ids.add(buf.readString());
                        }
                        String ability = buf.readString();
                        return new SyncPlayerStatusPayload(ids, ability, buf.readVarInt(),
                                buf.readVarInt(), buf.readBoolean());
                    }
            );

    public static final Type<? super RegistryByteBuf, SyncPlayerStatusPayload> TYPE =
            PayloadTypeRegistry.playS2C().register(ID, CODEC);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
