package com.kingdomunique.client;

import com.kingdomunique.network.AbilityKeyPayload;
import com.kingdomunique.network.PossessionPayload;
import com.kingdomunique.network.DetonatePayload;
import com.kingdomunique.network.RequestOpenItemMenuPayload;
import com.kingdomunique.network.RequestStatusPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** クライアント側のキーバインド登録とパケット送信。 */
@Environment(EnvType.CLIENT)
public final class KeyBindings {

    private static KeyBinding activateKey;
    private static KeyBinding modeKey;
    private static KeyBinding possessionKey;
    private static KeyBinding itemMenuKey;
    private static KeyBinding statusKey;
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
