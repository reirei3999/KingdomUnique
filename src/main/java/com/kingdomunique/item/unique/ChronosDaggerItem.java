package com.kingdomunique.item.unique;

import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;

/** 空間の短剣クロノス・ダガー。 */
public final class ChronosDaggerItem extends SwordItem implements UniqueWeapon {

    public ChronosDaggerItem(ToolMaterial material, Settings settings) {
        super(material, settings);
    }

    @Override
    public String getUniqueId() {
        return "chronos_dagger";
    }
}
