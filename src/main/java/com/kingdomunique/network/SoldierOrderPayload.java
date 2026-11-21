package com.kingdomunique.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** C2S: 兵士への命令（一斉/個別、移動座標、行動スタンス）。 */
public record SoldierOrderPayload(boolean isMassOrder, int targetEntityId,
                                  BlockPos moveTargetPos, int actionType) implements CustomPayload {

    public static final Id<SoldierOrderPayload> ID =
            new Id<>(Identifier.of("kingdomunique", "soldier_order"));

    private static final PacketCodec<RegistryByteBuf, SoldierOrderPayload> CODEC =
            CustomPayload.codecOf(
                    (payload, buf) -> {
                        buf.writeBoolean(payload.isMassOrder());
                        buf.writeInt(payload.targetEntityId());
                        buf.writeBlockPos(payload.moveTargetPos());
                        buf.writeVarInt(payload.actionType());
                    },
                    buf -> new SoldierOrderPayload(buf.readBoolean(), buf.readInt(),
                            buf.readBlockPos(), buf.readVarInt())
            );

    public static final Type<? super RegistryByteBuf, SoldierOrderPayload> TYPE =
            PayloadTypeRegistry.playC2S().register(ID, CODEC);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
