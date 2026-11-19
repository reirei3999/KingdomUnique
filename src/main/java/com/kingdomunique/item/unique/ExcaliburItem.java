package com.kingdomunique.item.unique;

import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;

/** 聖剣エクスカリバー。 */
public final class ExcaliburItem extends SwordItem implements UniqueWeapon {

    public ExcaliburItem(ToolMaterial material, Settings settings) {
        super(material, settings);
    }

    @Override
    public String getUniqueId() {
        return "excalibur";
    }
}
