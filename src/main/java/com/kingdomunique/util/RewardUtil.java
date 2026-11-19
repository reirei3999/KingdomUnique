package com.kingdomunique.util;

import com.kingdomunique.item.unique.UniqueWeaponManager;
import com.kingdomunique.registry.ContentRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

/** 報酬アイテムの安全な生成・付与と、正しいカテゴリ名の通知をまとめる。 */
public final class RewardUtil {

    private RewardUtil() {
    }

    /**
     * IDに対応する実体アイテムがあれば必ず有効なスタックを生成して付与する。
     * パッシブ能力など実体が無い場合は false を返す（空気は絶対に渡さない）。
     */
    public static boolean grantPhysical(ServerPlayerEntity player, String id) {
        ItemStack stack = UniqueWeaponManager.createStackForId(id);
        if (stack.isEmpty() || stack.getItem() == Items.AIR) {
            return false;
        }
        if (stack.getCount() <= 0) {
            stack.setCount(1);
        }
        player.giveItemStack(stack);
        return true;
    }

    /** 能力／遺物／呪物のカテゴリに応じた正しい獲得メッセージ。 */
    public static String rewardMessage(ContentRegistry.Kind kind, String name) {
        return switch (kind) {
            case ABILITY -> "§6[KingdomUnique] 能力《" + name + "》を覚醒した！";
            case RELIC -> "§6[KingdomUnique] 遺物《" + name + "》を獲得した！";
            case CURSE -> "§6[KingdomUnique] 呪物《" + name + "》を獲得した！";
        };
    }
}
