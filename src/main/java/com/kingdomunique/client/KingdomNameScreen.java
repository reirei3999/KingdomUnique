package com.kingdomunique.client;

import com.kingdomunique.client.ui.KuTheme;
import com.kingdomunique.network.SetKingdomNamePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** 建国時に国名を決める入力画面。 */
@Environment(EnvType.CLIENT)
public final class KingdomNameScreen extends Screen {

    private TextFieldWidget nameField;

    public KingdomNameScreen() {
        super(Text.literal("王国の名を決める"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        nameField = new TextFieldWidget(this.textRenderer,
                cx - 110, cy - 30, 220, 20, Text.literal("国名"));
        nameField.setMaxLength(24);
        nameField.setText("新王国");
        nameField.setFocused(true);
        this.addDrawableChild(nameField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("決定"), button -> decide())
                .dimensions(cx - 110, cy + 10, 105, 20).build());
    }

    private void decide() {
        String name = nameField == null ? "" : nameField.getText().trim();
        if (name.isEmpty()) {
            name = "新王国";
        }
        ClientPlayNetworking.send(new SetKingdomNamePayload(name));
        this.close();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter / KP Enter
            decide();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int cy = this.height / 2;
        KuTheme.drawTitle(context, textRenderer, title, cx, cy - 60);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("この王国の名前を入力してください（名札に表示されます）"),
                cx, cy - 44, KuTheme.PRIMARY);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, KuTheme.BACKDROP);
        int w = Math.min(400, width - 24);
        KuTheme.drawPanel(context, (width - w) / 2, height / 2 - 70, w, 120, KuTheme.GOLD);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
