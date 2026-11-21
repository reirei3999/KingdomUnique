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
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import com.mojang.datafixers.util.Pair;

/** 王国兵士の装備編成画面（メインハンド＋防具4部位）。 */
public class SoldierEquipmentScreenHandler extends ScreenHandler {

    private final VillagerEntity soldier;
    private final Inventory equipment;
    private boolean initializing = true;

    private static final EquipmentSlot[] SLOT_ORDER = {
            EquipmentSlot.MAINHAND,
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    /** クライアント側コンストラクタ（スロット内容はサーバーから同期される）。 */
    public SoldierEquipmentScreenHandler(int syncId, PlayerInventory playerInventory, int entityId) {
        this(syncId, playerInventory,
                playerInventory.player.getWorld().getEntityById(entityId) instanceof VillagerEntity villager
                        ? villager : null);
    }

    public SoldierEquipmentScreenHandler(int syncId, PlayerInventory playerInventory, VillagerEntity soldier) {
        super(SoldierScreenHandlers.EQUIPMENT, syncId);
        this.soldier = soldier;
        this.equipment = new SimpleInventory(5) {
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
        initializing = false;

        this.addSlot(new Slot(equipment, 0, 104, 90) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return isWeaponOrTool(stack);
            }

            @Override
            public Pair<Identifier, Identifier> getBackgroundSprite() {
                return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE,
                        Identifier.ofVanilla("item/empty_slot_sword"));
            }
        });
        for (int i = 1; i <= 4; i++) {
            final EquipmentSlot target = SLOT_ORDER[i];
            this.addSlot(new Slot(equipment, i, 16, 32 + (i - 1) * 20) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    return stack.getItem() instanceof ArmorItem armor && armor.getSlotType() == target;
                }

                @Override
                public int getMaxItemCount() {
                    return 1;
                }

                @Override
                public Pair<Identifier, Identifier> getBackgroundSprite() {
                    Identifier texture = switch (target) {
                        case HEAD -> PlayerScreenHandler.EMPTY_HELMET_SLOT_TEXTURE;
                        case CHEST -> PlayerScreenHandler.EMPTY_CHESTPLATE_SLOT_TEXTURE;
                        case LEGS -> PlayerScreenHandler.EMPTY_LEGGINGS_SLOT_TEXTURE;
                        default -> PlayerScreenHandler.EMPTY_BOOTS_SLOT_TEXTURE;
                    };
                    return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, texture);
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 47 + col * 18, 142 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 47 + col * 18, 200));
        }
    }

    /** スロット変更を即座に兵士の実装備へ反映する。 */
    private void syncToSoldierEntity() {
        if (initializing || soldier == null || soldier.getWorld().isClient()) {
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
            if (slotIndex < 5) {
                if (!this.insertItem(original, 5, this.slots.size(), true)) {
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

    public VillagerEntity getSoldier() {
        return soldier;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        syncToSoldierEntity();
    }
}
