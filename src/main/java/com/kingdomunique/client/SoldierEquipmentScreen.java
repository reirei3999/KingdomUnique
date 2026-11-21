package com.kingdomunique.client;

import com.kingdomunique.client.ui.KuTheme;
import com.kingdomunique.soldier.SoldierCombatHandler;
import com.kingdomunique.soldier.SoldierEquipmentScreenHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;

/** 兵士の姿と戦力を確認しながら装備を編成する画面。 */
@Environment(EnvType.CLIENT)
public final class SoldierEquipmentScreen extends HandledScreen<SoldierEquipmentScreenHandler> {
    private static final String[] SLOT_NAMES = {"武器", "頭", "胴", "脚", "足"};

    public SoldierEquipmentScreen(SoldierEquipmentScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 256;
        backgroundHeight = 230;
        titleX = 12;
        titleY = 8;
        playerInventoryTitleX = 47;
        playerInventoryTitleY = 130;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        KuTheme.drawPanel(context, x, y, backgroundWidth, backgroundHeight, KuTheme.GOLD);
        KuTheme.drawSlot(context, x + 40, y + 30, 58, 82);
        KuTheme.drawSlot(context, x + 132, y + 30, 112, 82);
        for (Slot slot : handler.slots) {
            KuTheme.drawSlot(context, x + slot.x - 1, y + slot.y - 1, 18, 18);
        }
        VillagerEntity soldier = handler.getSoldier();
        if (soldier != null && !soldier.isRemoved()) {
            InventoryScreen.drawEntity(context, x + 42, y + 32, x + 96, y + 110,
                    34, 0.0625F, mouseX, mouseY, soldier);
            drawInfo(context, soldier);
        }
    }

    private void drawInfo(DrawContext context, VillagerEntity soldier) {
        int left = x + 140;
        context.drawText(textRenderer, "役職：兵士", left, y + 37, KuTheme.SECONDARY, false);
        float health = soldier.getHealth();
        float maxHealth = soldier.getMaxHealth();
        context.drawText(textRenderer, "HP " + (int) Math.ceil(health) + " / " + (int) Math.ceil(maxHealth),
                left, y + 51, KuTheme.SECONDARY, false);
        context.fill(left, y + 63, left + 96, y + 70, KuTheme.BORDER);
        int filled = MathHelper.clamp((int) (94 * health / Math.max(1, maxHealth)), 0, 94);
        context.fill(left + 1, y + 64, left + 1 + filled, y + 69, KuTheme.MERIT);
        context.drawText(textRenderer, "攻撃力  " + String.format(Locale.ROOT, "%.1f", attackDamage()),
                left, y + 79, KuTheme.SECONDARY, false);
        double[] armor = {0.0};
        EquipmentSlot[] armorSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < armorSlots.length; i++) {
            handler.getSlot(i + 1).getStack().applyAttributeModifiers(armorSlots[i], (attribute, modifier) -> {
                if (attribute.equals(EntityAttributes.GENERIC_ARMOR)) {
                    armor[0] += modifier.value();
                }
            });
        }
        context.drawText(textRenderer, "防御力  " + String.format(Locale.ROOT, "%.1f", armor[0]),
                left, y + 95, KuTheme.SECONDARY, false);
    }

    private double attackDamage() {
        return SoldierCombatHandler.calculateWeaponDamage(handler.getSlot(0).getStack());
    }
    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, titleX, titleY, KuTheme.PRIMARY, false);
        context.drawText(textRenderer, "インベントリ", playerInventoryTitleX, playerInventoryTitleY,
                KuTheme.PRIMARY, false);
        context.drawText(textRenderer, "装備", 16, 115, KuTheme.PRIMARY, false);
        context.drawText(textRenderer, "武器", 101, 77, KuTheme.PRIMARY, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
        if (focusedSlot != null && focusedSlot.id < 5 && !focusedSlot.hasStack()) {
            context.drawTooltip(textRenderer, Text.literal(SLOT_NAMES[focusedSlot.id]), mouseX, mouseY);
        }
    }
}
