package com.kingdomunique.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** S2C: 設計図の建材リストと建築範囲をクライアントへ渡す。 */
public record BlueprintInfoPayload(BlockPos pos, String buildingName, int radius, List<String> lines) implements CustomPayload {

    public static final CustomPayload.Id<BlueprintInfoPayload> ID =
            new CustomPayload.Id<>(Identifier.of("kingdomunique", "blueprint_info"));

    private static final PacketCodec<RegistryByteBuf, BlueprintInfoPayload> CODEC = CustomPayload.codecOf(
            (payload, buf) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeString(payload.buildingName());
                buf.writeVarInt(payload.radius());
                buf.writeVarInt(payload.lines().size());
                for (String line : payload.lines()) {
                    buf.writeString(line);
                }
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                String name = buf.readString();
                int radius = buf.readVarInt();
                int count = buf.readVarInt();
                List<String> lines = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    lines.add(buf.readString());
                }
                return new BlueprintInfoPayload(pos, name, radius, lines);
            }
    );

    public static final CustomPayload.Type<? super RegistryByteBuf, BlueprintInfoPayload> TYPE =
            PayloadTypeRegistry.playS2C().register(ID, CODEC);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
