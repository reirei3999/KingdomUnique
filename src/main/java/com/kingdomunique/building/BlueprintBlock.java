package com.kingdomunique.building;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.Map;

/** 設置して直接資材を投入すると自動建築が始まる設計図ブロック（素手で即時回収可能）。 */
public final class BlueprintBlock extends Block implements BlockEntityProvider {

    private final BuildingType type;

    public BlueprintBlock(BuildingType type, Settings settings) {
        super(settings);
        this.type = type;
    }

    public BuildingType getType() {
        return type;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BlueprintBlockEntity(pos, state, type);
    }

    /** どんな状態でも一瞬で壊せる。 */
    @Override
    public float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (world.isClient() || !(placer instanceof ServerPlayerEntity player)) {
            return;
        }
        BuildData data = BuildData.getOrCreate((ServerWorld) world);
        data.add(new BuildData.BuildSite(pos.toImmutable(), type, 0, false, player.getUuid()));
        player.sendMessage(Text.literal("§6[設計図] " + type.getDisplayName()
                + " に直接建材を右クリックで投入し、空手で右クリックして確認してください。"), false);
    }

    @Override
    public net.minecraft.util.ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world,
                                                             BlockPos pos, PlayerEntity player, Hand hand,
                                                             BlockHitResult hit) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return net.minecraft.util.ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(world.getBlockEntity(pos) instanceof BlueprintBlockEntity be)) {
            return net.minecraft.util.ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (be.isReady()) {
            player.sendMessage(Text.literal("§7[設計図] すでに建築が開始されています。"), false);
            return net.minecraft.util.ItemActionResult.success(true);
        }

        double multiplier = BuildingManager.costMultiplier(serverPlayer);
        ItemStack held = stack.copy();
        if (be.insert(held, multiplier)) {
            int inserted = stack.getCount() - held.getCount();
            stack.setCount(held.getCount());
            serverPlayer.sendMessage(Text.literal("§6[設計図] " + stack.getName().getString()
                    + " を " + inserted + " 個投入しました。"), true);
            // 必要資材が全て揃った瞬間に自動で建築を開始する。
            if (be.hasAllMaterials(type, multiplier)) {
                startBuilding(world, pos, be, serverPlayer);
            }
        } else {
            serverPlayer.sendMessage(Text.literal("§c[設計図] この資材は不要、または必要数を超えています。"), false);
        }
        return net.minecraft.util.ItemActionResult.success(true);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, BlockHitResult hit) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.SUCCESS;
        }
        if (!(world.getBlockEntity(pos) instanceof BlueprintBlockEntity be)) {
            return ActionResult.SUCCESS;
        }
        boolean adminCreative = serverPlayer.hasPermissionLevel(2) && serverPlayer.isCreative();

        if (be.isReady()) {
            // OP＋クリエイティブは建築中のサイトを素手右クリックで即完了させる。
            if (adminCreative) {
                BuildData data = BuildData.getOrCreate((ServerWorld) world);
                for (BuildData.BuildSite site : new java.util.ArrayList<>(data.sites())) {
                    if (site.pos().equals(pos)) {
                        BuildingManager.completeNow((ServerWorld) world, site, serverPlayer);
                        return ActionResult.SUCCESS;
                    }
                }
            }
            player.sendMessage(Text.literal("§7[設計図] すでに建築が開始されています。"), false);
            return ActionResult.SUCCESS;
        }

        double multiplier = BuildingManager.costMultiplier(serverPlayer);

        // OP＋クリエイティブ: 素手右クリックで不足している資材を1種類ずつ全量投入する。
        if (adminCreative) {
            for (Map.Entry<net.minecraft.item.Item, Integer> entry : type.materials().entrySet()) {
                int needed = Math.max(1, (int) Math.ceil(entry.getValue() * multiplier));
                int have = be.countOf(entry.getKey());
                if (have >= needed) {
                    continue;
                }
                ItemStack fill = new ItemStack(entry.getKey(), needed - have);
                be.insert(fill, multiplier);
                serverPlayer.sendMessage(Text.literal("§6[設計図] [Creator] "
                        + entry.getKey().getName().getString() + " を " + needed + " 個投入しました。"), false);
                break;
            }
            if (be.hasAllMaterials(type, multiplier)) {
                startBuilding(world, pos, be, serverPlayer);
            }
            return ActionResult.SUCCESS;
        }

        // 空手: 必要資材が揃っていれば、ここで初めて消費して建築を開始する。
        if (!be.hasAllMaterials(type, multiplier)) {
            serverPlayer.sendMessage(Text.literal("§c[設計図] 資材が不足しています。"), false);
            return ActionResult.SUCCESS;
        }
        startBuilding(world, pos, be, serverPlayer);
        return ActionResult.SUCCESS;
    }

    /** 資材を消費して建築サイトを「建築中」へ遷移させる。 */
    private void startBuilding(World world, BlockPos pos, BlueprintBlockEntity be,
                               ServerPlayerEntity serverPlayer) {
        double multiplier = BuildingManager.costMultiplier(serverPlayer);
        be.consume(type, multiplier);
        be.setReady(true);

        BuildData data = BuildData.getOrCreate((ServerWorld) world);
        for (BuildData.BuildSite candidate : new java.util.ArrayList<>(data.sites())) {
            if (candidate.pos().equals(pos)) {
                data.sites().remove(candidate);
                data.add(candidate.withReady(true));
                break;
            }
        }
        serverPlayer.sendMessage(Text.literal("§6[設計図] 資材が揃いました。建築士が作業を開始します。"), false);
    }

    /** 破壊時: 投入済み資材を全額返却し、建築サイトもキャンセルする。 */
    @Override
    public void onStateReplaced(BlockState state, net.minecraft.world.World world, BlockPos pos,
                                BlockState newState, boolean moved) {
        // クライアント側では BlockEntity の破壊演出のみ行い、ServerWorld へのキャストはしない。
        if (world.isClient()) {
            return;
        }
        if (!state.isOf(newState.getBlock())) {
            if (world.getBlockEntity(pos) instanceof BlueprintBlockEntity be) {
                be.dropAll(world, pos);
            }
            BuildData data = BuildData.getOrCreate((ServerWorld) world);
            for (BuildData.BuildSite site : new java.util.ArrayList<>(data.sites())) {
                if (site.pos().equals(pos)) {
                    data.sites().remove(site);
                    break;
                }
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
