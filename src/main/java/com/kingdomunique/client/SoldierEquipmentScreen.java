package com.kingdomunique.client;

import com.kingdomunique.soldier.SoldierEquipmentScreenHandler;
import com.kingdomunique.util.EnhancementUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
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
        panel(context, x, y, backgroundWidth, backgroundHeight, 0xFFC6C6C6, false);
        context.fill(x + 10, y + 22, x + 246, y + 23, 0xFF999999);
        panel(context, x + 40, y + 30, 58, 82, 0xFF42484A, true);
        panel(context, x + 132, y + 30, 112, 82, 0xFFB5B5B5, true);
        for (Slot slot : handler.slots) {
            panel(context, x + slot.x - 1, y + slot.y - 1, 18, 18, 0xFF8B8B8B, true);
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
        context.drawText(textRenderer, "役職：兵士", left, y + 37, 0xFF303030, false);
        float health = soldier.getHealth();
        float maxHealth = soldier.getMaxHealth();
        context.drawText(textRenderer, "HP " + (int) Math.ceil(health) + " / " + (int) Math.ceil(maxHealth),
                left, y + 51, 0xFF303030, false);
        context.fill(left, y + 63, left + 96, y + 70, 0xFF454545);
        int filled = MathHelper.clamp((int) (94 * health / Math.max(1, maxHealth)), 0, 94);
        context.fill(left + 1, y + 64, left + 1 + filled, y + 69, 0xFF579F45);
        context.drawText(textRenderer, "攻撃力  " + String.format(Locale.ROOT, "%.1f", attackDamage()),
                left, y + 79, 0xFF303030, false);
        int armor = 0;
        for (int i = 1; i < 5; i++) {
            if (handler.getSlot(i).getStack().getItem() instanceof ArmorItem item) {
                armor += item.getProtection();
            }
        }
        context.drawText(textRenderer, "防御力  " + armor, left, y + 95, 0xFF303030, false);
    }

    private double attackDamage() {
        ItemStack weapon = handler.getSlot(0).getStack();
        if (weapon.isEmpty()) {
            return 2.0;
        }
        double damage = 1.0;
        AttributeModifiersComponent modifiers = weapon.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers != null) {
            for (AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
                if (entry.attribute().equals(EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
                    damage += entry.modifier().value();
                }
            }
        }
        return Math.max(2.0, damage * EnhancementUtil.multiplier(EnhancementUtil.getLevel(weapon)));
    }

    private static void panel(DrawContext context, int x, int y, int width, int height, int color, boolean inset) {
        int light = inset ? 0xFF555555 : 0xFFFFFFFF;
        int dark = inset ? 0xFFFFFFFF : 0xFF555555;
        context.fill(x, y, x + width, y + height, color);
        context.fill(x, y, x + width - 1, y + 1, light);
        context.fill(x, y, x + 1, y + height - 1, light);
        context.fill(x + 1, y + height - 1, x + width, y + height, dark);
        context.fill(x + width - 1, y + 1, x + width, y + height, dark);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, titleX, titleY, 0xFF404040, false);
        context.drawText(textRenderer, "インベントリ", playerInventoryTitleX, playerInventoryTitleY,
                0xFF404040, false);
        context.drawText(textRenderer, "装備", 16, 115, 0xFF404040, false);
        context.drawText(textRenderer, "武器", 101, 77, 0xFF404040, false);
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
