package com.kingdomunique.soldier;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolItem;
import net.minecraft.item.TridentItem;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;

/** 王国兵士の装備編成画面（メインハンド＋防具4部位）。汎用9x1画面を流用する。 */
public class SoldierEquipmentScreenHandler extends ScreenHandler {

    private final VillagerEntity soldier;
    private final Inventory equipment;

    private static final EquipmentSlot[] SLOT_ORDER = {
            EquipmentSlot.MAINHAND,
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    /** クライアント側コンストラクタ（スロット内容はサーバーから同期される）。 */
    public SoldierEquipmentScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, null);
    }

    public SoldierEquipmentScreenHandler(int syncId, PlayerInventory playerInventory, VillagerEntity soldier) {
        super(ScreenHandlerType.GENERIC_9X1, syncId);
        this.soldier = soldier;
        this.equipment = new SimpleInventory(9) {
            @Override
            public void markDirty() {
                super.markDirty();
                syncToSoldierEntity();
            }
        };

        if (soldier != null) {
            for (int i = 0; i < SLOT_ORDER.length; i++) {
                equipment.setStack(i, soldier.getEquippedStack(SLOT_ORDER[i]).copy());
            }
        }

        this.addSlot(new Slot(equipment, 0, 8, 18) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return isWeaponOrTool(stack);
            }
        });
        for (int i = 1; i <= 4; i++) {
            final EquipmentSlot target = SLOT_ORDER[i];
            this.addSlot(new Slot(equipment, i, 8 + i * 18, 18) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    return stack.getItem() instanceof ArmorItem armor && armor.getSlotType() == target;
                }
            });
        }
        for (int i = 5; i < 9; i++) {
            this.addSlot(new Slot(equipment, i, 8 + i * 18, 18) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean canTakeItems(PlayerEntity playerEntity) {
                    return false;
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 50 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 108));
        }
    }

    /** スロット変更を即座に兵士の実装備へ反映する。 */
    private void syncToSoldierEntity() {
        if (soldier == null || soldier.getWorld().isClient()) {
            return;
        }
        for (int i = 0; i < SLOT_ORDER.length; i++) {
            EquipmentSlot slot = SLOT_ORDER[i];
            soldier.equipStack(slot, equipment.getStack(i).copy());
            soldier.setEquipmentDropChance(slot, 0.0f);
        }
        // Gemini 修正: バニラAIによる勝手なアイテム回収・上書きを禁止し、武器が消えるのを防ぐ。
        soldier.setCanPickUpLoot(false);
        soldier.setPersistent();
    }

    private static boolean isWeaponOrTool(ItemStack stack) {
        var item = stack.getItem();
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem
                || item instanceof MaceItem || item instanceof RangedWeaponItem
                || item instanceof ShieldItem || item instanceof ToolItem;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasStack()) {
            ItemStack original = slot.getStack();
            result = original.copy();
            if (slotIndex < 9) {
                if (!this.insertItem(original, 9, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.insertItem(original, 0, 5, false)) {
                return ItemStack.EMPTY;
            }
            if (original.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }
        return result;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return soldier == null || (soldier.isAlive() && player.squaredDistanceTo(soldier) <= 64.0);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        syncToSoldierEntity();
    }
}
