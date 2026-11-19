package com.kingdomunique.item.unique;

import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;

/** 妖刀ムラマサ。 */
public final class MuramasaItem extends SwordItem implements UniqueWeapon {

    public MuramasaItem(ToolMaterial material, Settings settings) {
        super(material, settings);
    }

    @Override
    public String getUniqueId() {
        return "muramasa";
    }
}
