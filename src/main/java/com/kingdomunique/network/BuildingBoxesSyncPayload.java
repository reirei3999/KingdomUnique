package com.kingdomunique.network;

import com.kingdomunique.building.BuildingType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** S2C: 完成済み／建築中の建築物の占有 BlockBox を同期（設計図プレビューの重複判定用）。 */
public record BuildingBoxesSyncPayload(Identifier dimension, List<BlockBox> occupiedBoxes,
                                       Set<BuildingType> restrictedTypes) implements CustomPayload {

    public static final Id<BuildingBoxesSyncPayload> ID =
            new Id<>(Identifier.of("kingdomunique", "building_boxes_sync"));

    private static final PacketCodec<RegistryByteBuf, BuildingBoxesSyncPayload> CODEC =
            CustomPayload.codecOf(
                    (payload, buf) -> {
                        buf.writeIdentifier(payload.dimension());
                        buf.writeVarInt(payload.occupiedBoxes().size());
                        for (BlockBox box : payload.occupiedBoxes()) {
                            buf.writeInt(box.getMinX());
                            buf.writeInt(box.getMinY());
                            buf.writeInt(box.getMinZ());
                            buf.writeInt(box.getMaxX());
                            buf.writeInt(box.getMaxY());
                            buf.writeInt(box.getMaxZ());
                        }
                        buf.writeVarInt(payload.restrictedTypes().size());
                        for (BuildingType type : payload.restrictedTypes()) {
                            buf.writeEnumConstant(type);
                        }
                    },
                    buf -> {
                        Identifier dimension = buf.readIdentifier();
                        int size = buf.readVarInt();
                        List<BlockBox> boxes = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            boxes.add(new BlockBox(buf.readInt(), buf.readInt(), buf.readInt(),
                                    buf.readInt(), buf.readInt(), buf.readInt()));
                        }
                        Set<BuildingType> restricted = EnumSet.noneOf(BuildingType.class);
                        int count = buf.readVarInt();
                        for (int i = 0; i < count; i++) {
                            restricted.add(buf.readEnumConstant(BuildingType.class));
                        }
                        return new BuildingBoxesSyncPayload(dimension, boxes, restricted);
                    }
            );

    public static final Type<? super RegistryByteBuf, BuildingBoxesSyncPayload> TYPE =
            PayloadTypeRegistry.playS2C().register(ID, CODEC);

    public static void initialize() {
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
