package com.kingdomunique.client;

import com.kingdomunique.client.ui.KuTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

final class MaterialDetailsScreen extends Screen {
    private final List<String> lines;
    private int scroll;

    MaterialDetailsScreen(String title, List<String> lines) {
        super(Text.literal(title));
        this.lines = List.copyOf(lines);
    }

    private List<OrderedText> rows() {
        List<OrderedText> rows = new ArrayList<>();
        rows.addAll(textRenderer.wrapLines(title, Math.max(1, width - 32)));
        for (String line : lines) {
            rows.addAll(textRenderer.wrapLines(Text.literal(line), Math.max(1, width - 32)));
        }
        return rows;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, KuTheme.BACKDROP);
        List<OrderedText> rows = rows();
        int visible = Math.max(1, (height - 44) / 12);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visible)));
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            context.drawTextWithShadow(textRenderer, rows.get(scroll + i), 16, 16 + i * 12, KuTheme.BODY);
        }
        context.drawTextWithShadow(textRenderer, Text.literal("↑↓ / ホイール: スクロール  Esc: 戻る"),
                16, height - 20, KuTheme.MUTED);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= (int) Math.signum(verticalAmount);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
            scroll += keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN ? 1 : -1;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
