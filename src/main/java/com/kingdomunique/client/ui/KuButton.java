package com.kingdomunique.client.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

@Environment(EnvType.CLIENT)
public final class KuButton extends ButtonWidget {
    private final int accent;
    private final int textColor;
    private final ItemStack icon;
    private final Text category;
    private final BooleanSupplier selected;
    private final Tooltip fullLabel;

    private KuButton(Builder builder) {
        super(builder.x, builder.y, builder.width, builder.height, builder.message,
                builder.action, DEFAULT_NARRATION_SUPPLIER);
        accent = builder.accent;
        textColor = builder.textColor;
        icon = builder.icon;
        category = builder.category;
        selected = builder.selected;
        fullLabel = Tooltip.of(builder.message);
    }

    public static Builder themed(Text message, PressAction action) {
        return new Builder(message, action);
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        boolean highlighted = active && (isHovered() || isFocused() || selected.getAsBoolean());
        int fill = !active ? KuTheme.INSET : highlighted ? KuTheme.HEADER : KuTheme.CARD;
        context.fill(getX(), getY(), getX() + width, getY() + height, fill);
        KuTheme.drawBorder(context, getX(), getY(), width, height, highlighted ? accent : KuTheme.BORDER);
        int color = active ? textColor : KuTheme.MUTED;
        Text label = active ? getMessage() : Text.literal(getMessage().getString().replaceAll("§[0-9A-FK-ORa-fk-or]", ""));
        int labelWidth = Math.max(1, width - (category == null ? 12 : 32));
        Text fitted = fitLabel(renderer, label, labelWidth);
        setTooltip(renderer.getWidth(label) > labelWidth ? fullLabel : null);
        if (category == null) {
            context.drawCenteredTextWithShadow(renderer, fitted, getX() + width / 2,
                    getY() + (height - 8) / 2, color);
        } else {
            context.drawItem(icon, getX() + 5, getY() + (height - 16) / 2);
            context.drawTextWithShadow(renderer, fitted, getX() + 26, getY() + 5, color);
            context.drawTextWithShadow(renderer, category, getX() + 26, getY() + 18,
                    active ? accent : KuTheme.MUTED);
        }
    }

    public static Text fitLabel(TextRenderer renderer, Text label, int width) {
        if (renderer.getWidth(label) <= width) {
            return label;
        }
        String ellipsis = "…";
        if (renderer.getWidth(ellipsis) > width) {
            return Text.empty();
        }
        return Text.literal(renderer.trimToWidth(label.getString(), width - renderer.getWidth(ellipsis)) + ellipsis)
                .setStyle(label.getStyle());
    }

    public static final class Builder {
        private final Text message;
        private final PressAction action;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;
        private int accent = KuTheme.CYAN;
        private int textColor = KuTheme.PRIMARY;
        private ItemStack icon = ItemStack.EMPTY;
        private Text category;
        private BooleanSupplier selected = () -> false;

        private Builder(Text message, PressAction action) {
            this.message = message;
            this.action = action;
        }

        public Builder dimensions(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder accent(int accent) {
            this.accent = accent;
            return this;
        }

        public Builder textColor(int color) {
            textColor = color;
            return this;
        }

        public Builder card(ItemStack icon, Text category, BooleanSupplier selected) {
            this.icon = icon;
            this.category = category;
            this.selected = selected;
            return this;
        }

        public KuButton build() {
            return new KuButton(this);
        }
    }
}
