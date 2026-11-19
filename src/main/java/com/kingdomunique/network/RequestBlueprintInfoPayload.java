package com.kingdomunique.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** C2S: 視線中の設計図ブロックの情報を要求。 */
public record RequestBlueprintInfoPayload(BlockPos pos) implements CustomPayload {

    public static final CustomPayload.Id<RequestBlueprintInfoPayload> ID =
            new CustomPayload.Id<>(Identifier.of("kingdomunique", "request_blueprint_info"));

    private static final PacketCodec<RegistryByteBuf, RequestBlueprintInfoPayload> CODEC = CustomPayload.codecOf(
            (payload, buf) -> buf.writeBlockPos(payload.pos()),
            buf -> new RequestBlueprintInfoPayload(buf.readBlockPos())
    );

    public static final CustomPayload.Type<? super RegistryByteBuf, RequestBlueprintInfoPayload> TYPE =
            PayloadTypeRegistry.playC2S().register(ID, CODEC);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
