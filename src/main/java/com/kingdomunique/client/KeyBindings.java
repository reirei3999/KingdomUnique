package com.kingdomunique.client;

import com.kingdomunique.network.AbilityKeyPayload;
import com.kingdomunique.network.PossessionPayload;
import com.kingdomunique.network.DetonatePayload;
import com.kingdomunique.network.RequestOpenItemMenuPayload;
import com.kingdomunique.network.RequestStatusPayload;
import com.kingdomunique.network.SoldierOrderPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

/** クライアント側のキーバインド登録とパケット送信。 */
@Environment(EnvType.CLIENT)
public final class KeyBindings {

    private static KeyBinding activateKey;
    private static KeyBinding modeKey;
    private static KeyBinding possessionKey;
    private static KeyBinding itemMenuKey;
    private static KeyBinding statusKey;
    // 兵士指揮キー（H: 一斉/個別切替, J: 移動命令 / スニーク+J: スタンス切替）
    private static KeyBinding toggleOrderScopeKey;
    private static KeyBinding issueSoldierOrderKey;
    private static boolean isMassOrderMode = true;
    private static int selectedSoldierEntityId = -1;
    private static boolean initialized = false;
    /** 左クリックのエッジ検出用（wasPressed はバニラに消費されるため自前で管理する）。 */
    private static boolean prevAttackPressed = false;

    private KeyBindings() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        activateKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.kingdomunique.activate",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.kingdomunique"
        ));
        modeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.kingdomunique.mode",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.kingdomunique"
        ));

        possessionKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.kingdomunique.possession",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.kingdomunique"
        ));

        itemMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.kingdomunique.item_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                "category.kingdomunique"
        ));

        statusKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.kingdomunique.status",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.kingdomunique"
        ));

        toggleOrderScopeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.kingdomunique.toggle_order_scope",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.kingdomunique"
        ));
        issueSoldierOrderKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.kingdomunique.issue_soldier_order",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "category.kingdomunique"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.currentScreen != null) {
                return;
            }
            if (activateKey.wasPressed()) {
                ClientPlayNetworking.send(new AbilityKeyPayload(AbilityKeyPayload.Action.ACTIVATE));
            }
            if (modeKey.wasPressed()) {
                ClientPlayNetworking.send(new AbilityKeyPayload(AbilityKeyPayload.Action.MODE_SWITCH));
            }
            if (possessionKey.wasPressed()) {
                ClientPlayNetworking.send(new PossessionPayload());
            }
            // ブロックを押さず空中をクリックした場合も発射できるよう、
            // バニラに消費されない isPressed で自前エッジ検出する。
            boolean attackPressed = client.options.attackKey.isPressed();
            if (attackPressed && !prevAttackPressed) {
                ClientPlayNetworking.send(new DetonatePayload());
            }
            prevAttackPressed = attackPressed;
            if (itemMenuKey.wasPressed()
                    && ClientPlayNetworking.canSend(RequestOpenItemMenuPayload.ID)) {
                ClientPlayNetworking.send(new RequestOpenItemMenuPayload());
            }
            if (statusKey.wasPressed()) {
                // 待ち時間なしで詳細画面を開き、ついでにサーバーへ最新状態を要求する。
                client.setScreen(new StatusScreen());
                if (ClientPlayNetworking.canSend(RequestStatusPayload.ID)) {
                    ClientPlayNetworking.send(new RequestStatusPayload());
                }
            }

            // --- 兵士指揮: Hキーで一斉/個別切替、Jキーで移動命令（スニーク+Jでスタンス切替） ---
            if (client.player == null) {
                return;
            }
            while (toggleOrderScopeKey.wasPressed()) {
                isMassOrderMode = !isMassOrderMode;
                if (!isMassOrderMode && client.targetedEntity instanceof VillagerEntity v) {
                    selectedSoldierEntityId = v.getId();
                }
                String msg = isMassOrderMode ? "§6[兵士指揮] §f命令範囲: §a一斉命令（周辺32マス全員）"
                        : "§6[兵士指揮] §f命令範囲: §b個別命令（対象: "
                        + (selectedSoldierEntityId == -1 ? "未選択" : "#" + selectedSoldierEntityId) + "）";
                client.player.sendMessage(Text.literal(msg), true);
            }
            while (issueSoldierOrderKey.wasPressed()) {
                if (!isMassOrderMode && client.targetedEntity instanceof VillagerEntity v) {
                    selectedSoldierEntityId = v.getId();
                    client.player.sendMessage(Text.literal("§b[個別指揮] §f兵士 (#"
                            + selectedSoldierEntityId + ") を選択しました。"), true);
                    continue;
                }
                if (client.player.isSneaking()) {
                    ClientPlayNetworking.send(new SoldierOrderPayload(
                            isMassOrderMode, selectedSoldierEntityId, BlockPos.ORIGIN, 1));
                } else {
                    HitResult hit = client.player.raycast(40.0D, 0.0f, false);
                    if (hit.getType() == HitResult.Type.BLOCK) {
                        BlockPos dest = ((BlockHitResult) hit).getBlockPos().up();
                        ClientPlayNetworking.send(new SoldierOrderPayload(
                                isMassOrderMode, selectedSoldierEntityId, dest, 0));
                    }
                }
            }
        });
    }

    /** クライアント初期化時に呼ばれる（将来のHUD登録用の足跡）。 */
    public static Text categoryText() {
        return Text.translatable("category.kingdomunique");
    }

    public static KeyBinding getActivateKey() {
        return activateKey;
    }

    public static KeyBinding getModeKey() {
        return modeKey;
    }
}
