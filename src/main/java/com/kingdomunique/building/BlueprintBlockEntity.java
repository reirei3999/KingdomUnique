package com.kingdomunique.building;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/** 設計図ブロックに投入された建材を永続保存する BlockEntity。 */
public final class BlueprintBlockEntity extends BlockEntity {

    private final SimpleInventory inventory;
    private final BuildingType type;
    private boolean ready = false;

    public BlueprintBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlueprintEntities.BLUEPRINT_ENTITY, pos, state);
        this.type = ((BlueprintBlock) state.getBlock()).getType();
        int slots = type.materials().entrySet().stream()
                .mapToInt(entry -> Math.ceilDiv(entry.getValue(), entry.getKey().getMaxCount()))
                .sum();
        this.inventory = new SimpleInventory(Math.max(1, slots)) {
            @Override
            public void markDirty() {
                BlueprintBlockEntity.this.markDirty();
            }
        };
    }

    public SimpleInventory inventory() {
        return inventory;
    }

    public BuildingType buildingType() {
        return type;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean value) {
        this.ready = value;
        markDirty();
    }

    /** 旧 API 互換（コスト倍率1.0）。 */
    public boolean insert(ItemStack stack) {
        return insert(stack, 1.0D);
    }

    /**
     * 必要な個数だけを手持ちから取り出して投入する（余分な資材は消费しない）。
     *
     * @return 1個でも投入できたか
     */
    public boolean insert(ItemStack stack, double costMultiplier) {
        if (stack.isEmpty() || ready) {
            return false;
        }
        int needed = 0;
        for (Map.Entry<Item, Integer> entry : type.materials().entrySet()) {
            if (entry.getKey() == stack.getItem()) {
                needed = BuildingType.requiredAmount(entry.getValue(), costMultiplier);
                break;
            }
        }
        if (needed <= 0) {
            return false;
        }
        int remaining = needed - countOf(stack.getItem());
        if (remaining <= 0) {
            return false;
        }
        int move = Math.min(stack.getCount(), remaining);
        int original = move;

        for (int i = 0; i < inventory.size() && move > 0; i++) {
            ItemStack slot = inventory.getStack(i);
            if (!slot.isEmpty() && slot.isOf(stack.getItem())
                    && slot.getCount() < slot.getMaxCount()) {
                int n = Math.min(move, slot.getMaxCount() - slot.getCount());
                slot.increment(n);
                stack.decrement(n);
                move -= n;
            }
        }
        for (int i = 0; i < inventory.size() && move > 0; i++) {
            if (inventory.getStack(i).isEmpty()) {
                int n = Math.min(move, stack.getMaxCount());
                ItemStack part = stack.copy();
                part.setCount(n);
                inventory.setStack(i, part);
                stack.decrement(n);
                move -= n;
            }
        }
        if (move < original) {
            markDirty();
            return true;
        }
        return false;
    }

    public int countOf(Item item) {
        int total = 0;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isOf(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** 資材が全て揃っているか（必要数はコスト倍率適用後の値）。 */
    public boolean hasAllMaterials(BuildingType type, double costMultiplier) {
        for (Map.Entry<Item, Integer> entry : type.materials().entrySet()) {
            int needed = BuildingType.requiredAmount(entry.getValue(), costMultiplier);
            if (countOf(entry.getKey()) < needed) {
                return false;
            }
        }
        return true;
    }

    /** 建築開始時に資材を消費する。 */
    public void consume(BuildingType type, double costMultiplier) {
        for (Map.Entry<Item, Integer> entry : type.materials().entrySet()) {
            int remaining = BuildingType.requiredAmount(entry.getValue(), costMultiplier);
            for (int i = 0; i < inventory.size() && remaining > 0; i++) {
                ItemStack stack = inventory.getStack(i);
                if (stack.isOf(entry.getKey())) {
                    int take = Math.min(remaining, stack.getCount());
                    stack.decrement(take);
                    remaining -= take;
                }
            }
        }
        markDirty();
    }

    /** 全資材をドロップ用に取り出す（破壊時の返却）。 */
    public void dropAll(net.minecraft.world.WorldAccess world, BlockPos pos) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                net.minecraft.entity.ItemEntity entity = new net.minecraft.entity.ItemEntity(
                        (net.minecraft.world.World) world, pos.getX() + 0.5, pos.getY() + 0.5,
                        pos.getZ() + 0.5, stack.copy());
                world.spawnEntity(entity);
                inventory.setStack(i, ItemStack.EMPTY);
            }
        }
        markDirty();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        nbt.put("inventory", inventory.toNbtList(registryLookup));
        nbt.putBoolean("ready", ready);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        net.minecraft.nbt.NbtList list = nbt.getList("inventory", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        inventory.readNbtList(list, registryLookup);
        ready = nbt.getBoolean("ready");
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
