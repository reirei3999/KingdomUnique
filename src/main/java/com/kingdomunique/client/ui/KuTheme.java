package com.kingdomunique.client.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public final class KuTheme {
    public static final int BACKDROP = 0xFF0B0B10;
    public static final int CARD = 0xFF161B2A;
    public static final int PANEL = 0xFF111827;
    public static final int BORDER = 0xFF334155;
    public static final int HEADER = 0xFF1E293B;
    public static final int SHADOW = 0x90000000;
    public static final int GOLD = 0xFFE5B84B;
    public static final int CYAN = 0xFF38BDF8;
    public static final int PURPLE = 0xFFA855F7;
    public static final int PRIMARY = 0xFFFFFFFF;
    public static final int SECONDARY = 0xFFCBD5E1;
    public static final int BODY = 0xFFE2E8F0;
    public static final int MUTED = 0xFF94A3B8;
    public static final int MERIT = 0xFF86EFAC;
    public static final int DRAWBACK = 0xFFF87171;
    public static final int HOVER = 0xFFFFF2CE;
    public static final int INSET = 0xFF0B1120;

    private KuTheme() {
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0xFFFFFF) | (alpha << 24);
    }

    public static void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void drawPanel(DrawContext context, int x, int y, int w, int h, int accent) {
        context.fill(x + 3, y + 4, x + w + 3, y + h + 4, SHADOW);
        context.fill(x, y, x + w, y + h, PANEL);
        drawBorder(context, x, y, w, h, BORDER);
        context.fill(x + 1, y + 1, x + w - 1, y + Math.min(24, h - 1), HEADER);
        context.fill(x + 1, y + 1, x + w - 1, y + 2, accent);
    }

    public static void drawSlot(DrawContext context, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + h, INSET);
        drawBorder(context, x, y, w, h, BORDER);
    }

    public static void drawTitle(DrawContext context, TextRenderer renderer, Text title, int centerX, int y) {
        context.drawCenteredTextWithShadow(renderer, title, centerX, y, GOLD);
    }
}
