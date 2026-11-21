package com.kingdomunique.client;

import com.kingdomunique.client.ui.KuButton;
import com.kingdomunique.client.ui.KuTheme;
import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.trade.CitizenMerchantScreenHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.village.TradeOffer;

@Environment(EnvType.CLIENT)
public final class CitizenMerchantScreen extends HandledScreen<CitizenMerchantScreenHandler> {
    private static final int HEADER_HEIGHT = 48;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 16;
    private static final int ROW_WIDTH = 92;
    private static final int ROW_HEIGHT = 24;
    private static final int PAGE_SIZE = 5;

    private KuButton previous;
    private KuButton next;
    private int page;
    private int selected = -1;

    public CitizenMerchantScreen(CitizenMerchantScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 282;
        backgroundHeight = 168;
    }

    @Override
    protected void init() {
        super.init();
        y += HEADER_HEIGHT / 2;
        previous = addDrawableChild(KuButton.themed(Text.literal("<"), button -> changePage(-1))
                .dimensions(x + LIST_X, y + 142, 21, 18).accent(KuTheme.GOLD).build());
        next = addDrawableChild(KuButton.themed(Text.literal(">"), button -> changePage(1))
                .dimensions(x + LIST_X + ROW_WIDTH - 21, y + 142, 21, 18).accent(KuTheme.GOLD).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        previous.active = page > 0;
        next.active = page + 1 < pageCount();
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
        int index = offerAt(mouseX, mouseY);
        if (index >= 0) {
            TradeOffer offer = handler.getRecipes().get(index);
            int listX = mouseX - x - LIST_X;
            ItemStack stack = listX < 23 ? offer.getDisplayedFirstBuyItem()
                    : listX < 45 ? offer.getDisplayedSecondBuyItem()
                    : listX >= 61 ? offer.getSellItem() : ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                context.drawItemTooltip(textRenderer, stack, mouseX, mouseY);
            } else if (offer.isDisabled()) {
                context.drawTooltip(textRenderer, Text.literal("在庫切れ"), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        KuTheme.drawPanel(context, x, y - HEADER_HEIGHT, backgroundWidth, backgroundHeight + HEADER_HEIGHT, KuTheme.GOLD);
        context.fill(x + 1, y - 1, x + backgroundWidth - 1, y, KuTheme.BORDER);
        context.fill(x + 103, y, x + 104, y + backgroundHeight - 1, KuTheme.BORDER);
        KuTheme.drawSlot(context, x + 107, y + LIST_Y, 163, 46);
        for (Slot slot : handler.slots) {
            KuTheme.drawSlot(context, x + slot.x - 1, y + slot.y - 1, 18, 18);
        }
        Slot result = handler.getSlot(2);
        KuTheme.drawBorder(context, x + result.x - 1, y + result.y - 1, 18, 18, KuTheme.GOLD);
        context.drawCenteredTextWithShadow(textRenderer, "→", x + 194, y + result.y + 4, KuTheme.GOLD);
        drawOffers(context, offerAt(mouseX, mouseY));
    }

    private void drawOffers(DrawContext context, int hovered) {
        var offers = handler.getRecipes();
        int first = page * PAGE_SIZE;
        int left = x + LIST_X;
        for (int row = 0; row < PAGE_SIZE && first + row < offers.size(); row++) {
            int index = first + row;
            TradeOffer offer = offers.get(index);
            int top = y + LIST_Y + row * ROW_HEIGHT;
            boolean highlighted = index == selected || index == hovered;
            context.fill(left, top, left + ROW_WIDTH, top + ROW_HEIGHT - 2,
                    highlighted ? KuTheme.HEADER : KuTheme.CARD);
            KuTheme.drawBorder(context, left, top, ROW_WIDTH, ROW_HEIGHT - 2,
                    index == selected ? KuTheme.GOLD : index == hovered ? KuTheme.MUTED : KuTheme.BORDER);
            drawOfferItem(context, offer.getDisplayedFirstBuyItem(), left + 5, top + 3);
            drawOfferItem(context, offer.getDisplayedSecondBuyItem(), left + 27, top + 3);
            context.drawCenteredTextWithShadow(textRenderer, offer.isDisabled() ? "×" : "→", left + 53, top + 7,
                    offer.isDisabled() ? KuTheme.DRAWBACK : KuTheme.GOLD);
            drawOfferItem(context, offer.getSellItem(), left + 68, top + 3);
        }
    }

    private void drawOfferItem(DrawContext context, ItemStack stack, int left, int top) {
        if (!stack.isEmpty()) {
            context.drawItem(stack, left, top);
            context.drawItemInSlot(textRenderer, stack, left, top);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        var info = handler.getCitizenInfo();
        context.drawText(textRenderer, title, 8, -40, KuTheme.PRIMARY, false);
        drawLabel(context, Text.literal("王国：" + info.kingdom()), 8, -21, 139);
        drawLabel(context, Text.literal("役職：" + roleName(info.role())), 8, -9, 139);
        Text profession = Text.literal("職業：").append(professionName(info.profession()));
        if (info.level() != 0) {
            profession = profession.copy().append("  Lv. " + info.level());
            drawLabel(context, Text.literal("職業経験値：" + info.professionXp()), 150, -21, 124);
        }
        drawLabel(context, profession, 150, -39, 124);
        drawLabel(context, Text.literal("村人からの評判：" + info.reputation()), 150, -9, 124);

        context.drawText(textRenderer, "交易品", LIST_X, 4, KuTheme.PRIMARY, false);
        context.drawText(textRenderer, "交換", 108, 4, KuTheme.PRIMARY, false);
        context.drawText(textRenderer, "インベントリ", 108, 72, KuTheme.PRIMARY, false);
        context.drawCenteredTextWithShadow(textRenderer, (page + 1) + " / " + pageCount(), 54, 147, KuTheme.MUTED);

        var offers = handler.getRecipes();
        boolean hasSelection = selected >= 0 && selected < offers.size();
        boolean disabled = hasSelection && offers.get(selected).isDisabled();
        String status = !hasSelection ? (offers.isEmpty() ? "交易品なし" : "交易品を選択")
                : disabled ? "在庫切れ"
                : "残り取引回数：" + (offers.get(selected).getMaxUses() - offers.get(selected).getUses());
        context.drawText(textRenderer, status, 113, 22, disabled ? KuTheme.DRAWBACK : KuTheme.MUTED, false);
    }

    private void drawLabel(DrawContext context, Text label, int left, int top, int width) {
        context.drawText(textRenderer, KuButton.fitLabel(textRenderer, label, width), left, top, KuTheme.SECONDARY, false);
    }

    private static Text professionName(String profession) {
        return switch (profession) {
            case "armorer" -> Text.literal("防具鍛冶");
            case "butcher" -> Text.literal("肉屋");
            case "cartographer" -> Text.literal("製図家");
            case "cleric" -> Text.literal("聖職者");
            case "farmer" -> Text.literal("農民");
            case "fisherman" -> Text.literal("釣り人");
            case "fletcher" -> Text.literal("矢師");
            case "leatherworker" -> Text.literal("革細工師");
            case "librarian" -> Text.literal("司書");
            case "mason" -> Text.literal("石工");
            case "nitwit" -> Text.literal("無職");
            case "none" -> Text.literal("無職");
            case "shepherd" -> Text.literal("羊飼い");
            case "toolsmith" -> Text.literal("道具鍛冶");
            case "weaponsmith" -> Text.literal("武器鍛冶");
            default -> Text.translatable("entity.minecraft.villager." + profession);
        };
    }

    private static String roleName(String role) {
        return switch (role) {
            case CitizenVillager.ROLE_FARMER -> "農民";
            case CitizenVillager.ROLE_BUILDER -> "建築士";
            case CitizenVillager.ROLE_SMITH -> "鍛冶屋";
            default -> "一般国民";
        };
    }

    private int pageCount() {
        return Math.max(1, MathHelper.ceilDiv(handler.getRecipes().size(), PAGE_SIZE));
    }

    private boolean changePage(int direction) {
        int updated = MathHelper.clamp(page + direction, 0, pageCount() - 1);
        if (updated == page) {
            return false;
        }
        page = updated;
        return true;
    }

    private int offerAt(double mouseX, double mouseY) {
        double rowY = mouseY - y - LIST_Y;
        if (mouseX < x + LIST_X || mouseX >= x + LIST_X + ROW_WIDTH
                || rowY < 0 || rowY >= PAGE_SIZE * ROW_HEIGHT || rowY % ROW_HEIGHT >= ROW_HEIGHT - 2) {
            return -1;
        }
        int index = page * PAGE_SIZE + (int) rowY / ROW_HEIGHT;
        return index < handler.getRecipes().size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = offerAt(mouseX, mouseY);
        if (button == 0 && index >= 0) {
            if (client != null && client.getNetworkHandler() != null) {
                selected = index;
                handler.setRecipeIndex(index);
                handler.switchTo(index);
                client.getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(index));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= x + LIST_X && mouseX < x + LIST_X + ROW_WIDTH
                && mouseY >= y + LIST_Y && mouseY < y + 142 && vertical != 0) {
            return changePage(vertical > 0 ? -1 : 1);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    protected boolean isClickOutsideBounds(double mouseX, double mouseY, int left, int top, int button) {
        return super.isClickOutsideBounds(mouseX, mouseY, left, top, button)
                && !(mouseX >= left && mouseX < left + backgroundWidth && mouseY >= top - HEADER_HEIGHT && mouseY < top);
    }
}
