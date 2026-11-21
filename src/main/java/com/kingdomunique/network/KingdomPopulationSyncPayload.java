package com.kingdomunique.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C: 王国の人口・上限と国庫在庫（エメラルド/パン）を同期（HUD表示用）。 */
public record KingdomPopulationSyncPayload(int currentPopulation, int maxPopulation,
                                           int treasuryEmeralds, int treasuryBread) implements CustomPayload {

    public static final Id<KingdomPopulationSyncPayload> ID =
            new Id<>(Identifier.of("kingdomunique", "kingdom_population_sync"));

    private static final PacketCodec<RegistryByteBuf, KingdomPopulationSyncPayload> CODEC =
            CustomPayload.codecOf(
                    (payload, buf) -> {
                        buf.writeVarInt(payload.currentPopulation());
                        buf.writeVarInt(payload.maxPopulation());
                        buf.writeVarInt(payload.treasuryEmeralds());
                        buf.writeVarInt(payload.treasuryBread());
                    },
                    buf -> new KingdomPopulationSyncPayload(buf.readVarInt(), buf.readVarInt(),
                            buf.readVarInt(), buf.readVarInt())
            );

    public static final Type<? super RegistryByteBuf, KingdomPopulationSyncPayload> TYPE =
            PayloadTypeRegistry.playS2C().register(ID, CODEC);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
