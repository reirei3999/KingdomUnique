package com.kingdomunique.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C: 王国の現在人口と上限を同期（統合HUDの国民表示用）。 */
public record KingdomPopulationSyncPayload(int currentPopulation, int maxPopulation) implements CustomPayload {

    public static final Id<KingdomPopulationSyncPayload> ID =
            new Id<>(Identifier.of("kingdomunique", "kingdom_population_sync"));

    private static final PacketCodec<RegistryByteBuf, KingdomPopulationSyncPayload> CODEC =
            CustomPayload.codecOf(
                    (payload, buf) -> {
                        buf.writeVarInt(payload.currentPopulation());
                        buf.writeVarInt(payload.maxPopulation());
                    },
                    buf -> new KingdomPopulationSyncPayload(buf.readVarInt(), buf.readVarInt())
            );

    public static final Type<? super RegistryByteBuf, KingdomPopulationSyncPayload> TYPE =
            PayloadTypeRegistry.playS2C().register(ID, CODEC);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
