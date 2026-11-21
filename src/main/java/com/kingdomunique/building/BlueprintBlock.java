package com.kingdomunique.building;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.Optional;

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
        return new BlueprintBlockEntity(pos, state);
    }

    /** どんな状態でも一瞬で壊せる。 */
    @Override
    public float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        if (context.getWorld() instanceof ServerWorld world
                && context.getPlayer() instanceof ServerPlayerEntity player) {
            Optional<Text> error = BuildingValidator.buildingLimit(world, player.getUuid(), type, false);
            if (error.isPresent()) {
                player.sendMessage(error.get(), false);
                if (!player.isCreative()) {
                    player.currentScreenHandler.syncState();
                }
                return null;
            }
        }
        return super.getPlacementState(context);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (world.isClient() || !(placer instanceof ServerPlayerEntity player)) {
            return;
        }
        BuildData data = BuildData.getOrCreate((ServerWorld) world);
        data.add(new BuildData.BuildSite(pos.toImmutable(), type, 0, false, player.getUuid(),
                player.getHorizontalFacing().getOpposite()));
        if (player.isCreative()) {
            completeCreative((ServerWorld) world, pos, player);
            return;
        }
        player.sendMessage(Text.literal("§6[設計図] " + type.getDisplayName()
                + " に直接建材を右クリックで投入し、空手で右クリックして確認してください。"), false);
    }

    @Override
    public net.minecraft.util.ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world,
                                                             BlockPos pos, PlayerEntity player, Hand hand,
                                                             BlockHitResult hit) {
        if (stack.isEmpty() || world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
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
            Text name = stack.getName();
            stack.setCount(held.getCount());
            serverPlayer.sendMessage(Text.literal("§6[設計図] " + name.getString()
                    + " を " + inserted + " 個投入しました。"), true);
            // 必要資材が全て揃った瞬間に自動で建築を開始する。
            if (be.hasAllMaterials(type, multiplier)) {
                startBuilding((ServerWorld) world, pos, be, serverPlayer, multiplier);
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
        if (serverPlayer.isCreative() && completeCreative((ServerWorld) world, pos, serverPlayer)) {
            return ActionResult.SUCCESS;
        }

        if (be.isReady()) {
            player.sendMessage(Text.literal("§7[設計図] すでに建築が開始されています。"), false);
            return ActionResult.SUCCESS;
        }

        double multiplier = BuildingManager.costMultiplier(serverPlayer);

        // 空手: 必要資材が揃っていれば、ここで初めて消費して建築を開始する。
        if (!be.hasAllMaterials(type, multiplier)) {
            serverPlayer.sendMessage(Text.literal("§c[設計図] 資材が不足しています。"), false);
            return ActionResult.SUCCESS;
        }
        startBuilding((ServerWorld) world, pos, be, serverPlayer, multiplier);
        return ActionResult.SUCCESS;
    }

    private boolean completeCreative(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
        BuildData.BuildSite site = BuildData.getOrCreate(world).sites().stream()
                .filter(candidate -> candidate.pos().equals(pos))
                .findFirst().orElse(null);
        if (site == null || !site.owner().equals(player.getUuid())) {
            return false;
        }
        Optional<Text> error = BuildingValidator.validate(world, site);
        if (error.isPresent()) {
            player.sendMessage(error.get(), false);
            return true;
        }
        Box playerBox = player.getBoundingBox();
        boolean wasClear = !world.getBlockCollisions(player, playerBox).iterator().hasNext();
        if (BuildingManager.completeNow(world, site, player) && wasClear
                && world.getBlockCollisions(player, playerBox).iterator().hasNext()) {
            for (int y = player.getBlockY() + 1; y + playerBox.getLengthY() <= world.getTopY(); y++) {
                if (world.isSpaceEmpty(player, playerBox.offset(0, y - player.getY(), 0))) {
                    player.requestTeleport(player.getX(), y, player.getZ());
                    break;
                }
            }
        }
        return true;
    }

    /** 資材を消費して建築サイトを「建築中」へ遷移させる。 */
    private void startBuilding(ServerWorld world, BlockPos pos, BlueprintBlockEntity be,
                               ServerPlayerEntity serverPlayer, double multiplier) {
        BuildData data = BuildData.getOrCreate(world);
        BuildData.BuildSite site = data.sites().stream()
                .filter(candidate -> candidate.pos().equals(pos))
                .findFirst().orElse(null);
        if (site == null || site.ready()) {
            return;
        }
        Optional<Text> error = BuildingValidator.validate(world, site);
        if (error.isPresent()) {
            serverPlayer.sendMessage(error.get(), false);
            return;
        }
        be.consume(type, multiplier);
        be.setReady(true);

        data.remove(site);
        data.add(site.withReady(true));
        com.kingdomunique.status.StatusManager.syncBuildingBoxesToWorld(world);
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
                    data.remove(site);
                    break;
                }
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
