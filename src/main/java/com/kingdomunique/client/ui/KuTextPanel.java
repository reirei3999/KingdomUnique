package com.kingdomunique.client.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Wrapped, clipped prose shared by the altar and journal. Scroll positions are in lines. */
@Environment(EnvType.CLIENT)
public final class KuTextPanel {
    private record Line(OrderedText text, int color) {}
    private final List<Line> lines = new ArrayList<>();
    private int scroll;
    private int maxScroll;
    private int x, y, width, height;

    public void begin(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        lines.clear();
    }

    public void paragraph(TextRenderer renderer, String text, int color) {
        for (OrderedText line : renderer.wrapLines(Text.literal(text), Math.max(1, width - 10))) {
            lines.add(new Line(line, color));
        }
    }

    public void render(DrawContext context, TextRenderer renderer) {
        int visible = Math.max(1, height / 12);
        maxScroll = Math.max(0, lines.size() - visible);
        scroll = Math.clamp(scroll, 0, maxScroll);
        context.enableScissor(x, y, x + width, y + height);
        for (int i = scroll; i < Math.min(lines.size(), scroll + visible); i++) {
            Line line = lines.get(i);
            context.drawTextWithShadow(renderer, line.text(), x, y + (i - scroll) * 12, line.color());
        }
        context.disableScissor();
        if (maxScroll > 0) {
            context.fill(x + width - 4, y, x + width - 2, y + height, KuTheme.BORDER);
            int thumb = Math.max(8, height * visible / lines.size());
            int top = y + (height - thumb) * scroll / maxScroll;
            context.fill(x + width - 4, top, x + width - 2, top + thumb, KuTheme.GOLD);
        }
    }

    public void reset() { scroll = 0; }
    public void scroll(int delta) {
        // At 320x240 a multi-row hand can leave a one-line viewport. A wheel/page
        // action must never skip unseen effect or drawback lines in that case.
        int visible = Math.max(1, height / 12);
        int step = Math.clamp(delta, -visible, visible);
        scroll = Math.clamp(scroll + step, 0, maxScroll);
    }
    public void end() { scroll = maxScroll; }
    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
    public String hint() {
        return maxScroll == 0 ? "" : "詳細 " + (scroll + 1) + "〜"
                + Math.min(lines.size(), scroll + Math.max(1, height / 12)) + " / " + lines.size() + "行 ・ ↑↓";
    }
}
