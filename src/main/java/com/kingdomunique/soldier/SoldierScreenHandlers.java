package com.kingdomunique.soldier;

import com.kingdomunique.KingdomUniqueMod;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** 兵士の装備画面と対象エンティティIDの同期。 */
public final class SoldierScreenHandlers {
    public static final ExtendedScreenHandlerType<SoldierEquipmentScreenHandler, Integer> EQUIPMENT =
            Registry.register(Registries.SCREEN_HANDLER,
                    Identifier.of(KingdomUniqueMod.MOD_ID, "soldier_equipment"),
                    new ExtendedScreenHandlerType<>(SoldierEquipmentScreenHandler::new, PacketCodecs.VAR_INT));

    private SoldierScreenHandlers() {
    }

    public static void initialize() {
    }
}
