package com.kingdomunique.war;

import com.kingdomunique.component.ModDataComponents;
import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.effect.CurseEffects;
import com.kingdomunique.effect.RelicEffects;
import com.kingdomunique.item.unique.UniqueWeaponManager;
import com.kingdomunique.item.unique.UniqueWeapons;
import com.kingdomunique.registry.ContentRegistry;
import com.kingdomunique.registry.CurseRegistry;
import com.kingdomunique.registry.RelicRegistry;
import com.kingdomunique.util.RewardUtil;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** 敵対王を倒した際に遺物・呪物・神話武器を1つ奪取する。 */
public final class SpoilsSystem {

    private SpoilsSystem() {
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(SpoilsSystem::onDeath);
    }

    private static void onDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayerEntity victim)
                || !(source.getAttacker() instanceof ServerPlayerEntity winner)
                || victim == winner) {
            return;
        }
        DiplomacyData diplomacy = DiplomacyData.getOrCreate(
                winner.getServer().getWorld(World.OVERWORLD));
        if (diplomacy.isAllied(victim.getUuid(), winner.getUuid())) {
            return;
        }

        WorldUniqueData data = WorldUniqueData.getOrCreate(
                victim.getServer().getWorld(World.OVERWORLD));
        List<String> candidates = new ArrayList<>();
        for (String id : data.getClaimedIds(victim.getUuid())) {
            if (RelicRegistry.isValid(id) || CurseRegistry.isValid(id)
                    || java.util.Arrays.asList(UniqueWeapons.ALL_IDS).contains(id)) {
                candidates.add(id);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }

        String stolen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        if (!data.transferId(victim.getUuid(), winner.getUuid(), stolen)) {
            return;
        }

        ItemStack reward = UniqueWeaponManager.createStackForId(stolen);
        if (!reward.isEmpty()) {
            removeOriginalCopies(victim, reward.get(ModDataComponents.UNIQUE_WEAPON_ID));
            RewardUtil.deliver(winner, reward);
        }

        // 所有権の移動を即時反映する。
        RelicEffects.applyAttributes(victim);
        RelicEffects.applyAttributes(winner);
        CurseEffects.applyAttributes(victim);
        CurseEffects.applyAttributes(winner);

        winner.getServer().getPlayerManager().broadcast(
                Text.literal("§4§l【王権略奪】§r " + winner.getName().getString()
                        + " が " + victim.getName().getString() + " から《"
                        + nameOf(stolen) + "》を奪い取った！"), false);
    }

    /** 敗者の所持品と死亡時のドロップから同じ固有IDの実体を取り除く。 */
    private static void removeOriginalCopies(ServerPlayerEntity victim, String componentId) {
        if (componentId == null) {
            return;
        }
        for (int i = 0; i < victim.getInventory().size(); i++) {
            ItemStack stack = victim.getInventory().getStack(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (componentId.equals(stack.get(ModDataComponents.UNIQUE_WEAPON_ID))) {
                victim.getInventory().setStack(i, ItemStack.EMPTY);
            }
        }
        List<ItemEntity> drops = new ArrayList<>();
        for (var entity : victim.getServerWorld().iterateEntities()) {
            if (entity instanceof ItemEntity drop
                    && componentId.equals(drop.getStack().get(ModDataComponents.UNIQUE_WEAPON_ID))) {
                drops.add(drop);
            }
        }
        drops.forEach(ItemEntity::discard);
    }

    private static String nameOf(String id) {
        for (String weaponId : UniqueWeapons.ALL_IDS) {
            if (weaponId.equals(id)) {
                return switch (id) {
                    case "excalibur" -> "聖剣 エクスカリバー";
                    case "gae_bolg" -> "魔槍 ゲイボルグ";
                    case "muramasa" -> "妖刀 ムラマサ";
                    case "apollo_bow" -> "神弓 アポロンの太陽弓";
                    case "mjolnir" -> "雷神銃 ミョルニル";
                    case "kusanagi" -> "草薙の剣";
                    case "poseidon_trident" -> "トライデント・オブ・ポセイドン";
                    case "twilight_scythe" -> "黄昏の鎌（ハルパー）";
                    default -> "空間の短剣 クロノス・ダガー";
                };
            }
        }
        return ContentRegistry.nameOf(id);
    }
}
