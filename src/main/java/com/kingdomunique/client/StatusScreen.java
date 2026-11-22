package com.kingdomunique.client;

import com.kingdomunique.client.UniqueJournalCatalog.Entry;
import com.kingdomunique.client.UniqueJournalCatalog.Section;
import com.kingdomunique.client.ui.KuButton;
import com.kingdomunique.client.ui.KuTextPanel;
import com.kingdomunique.client.ui.KuTheme;
import com.kingdomunique.network.SyncPlayerStatusPayload;
import com.kingdomunique.registry.AbilityDescriptions;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** K / /ku info: the unique journal, including unowned entries, with existing relic/curse details retained. */
@Environment(EnvType.CLIENT)
public final class StatusScreen extends Screen {
    private SyncPlayerStatusPayload status;
    private SyncPlayerStatusPayload lastLiveStatus;
    private Section section = Section.SKILL;
    private List<Entry> entries = UniqueJournalCatalog.entries(section);
    private int selectedIndex;
    private int listOffset;
    private final KuTextPanel details = new KuTextPanel();
    private final List<KuButton> entryButtons = new ArrayList<>();
    private final List<KuButton> tabs = new ArrayList<>();
    private KuButton previous, next, closeButton;
    private int cooldownTicks;

    public StatusScreen() { this(StatusClient.latest()); }

    public StatusScreen(SyncPlayerStatusPayload payload) {
        super(Text.literal("ユニーク手帳"));
        status = payload;
        cooldownTicks = payload.cooldownTicks();
        lastLiveStatus = StatusClient.latest();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(payload.abilityId())) selectedIndex = i;
        }
    }

    private int panelWidth() { return Math.min(680, width - 16); }
    private int panelHeight() { return Math.min(420, height - 16); }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return (height - panelHeight()) / 2; }
    private int listWidth() { return Math.max(98, Math.min(200, (panelWidth() - 30) * 2 / 5)); }
    private int visibleEntries() { return Math.max(1, (panelHeight() - 126) / 36); }
    private int detailX() { return left() + listWidth() + 22; }
    private int detailWidth() { return panelWidth() - listWidth() - 32; }

    @Override
    protected void init() {
        // Keep logical focus across resize/paging instead of moving it to Close.
        int focusedEntry = entryButtons.indexOf(getFocused());
        int focusedTab = tabs.indexOf(getFocused());
        boolean previousFocused = previous != null && getFocused() == previous;
        boolean nextFocused = next != null && getFocused() == next;
        boolean closeFocused = closeButton != null && getFocused() == closeButton;
        entryButtons.clear();
        tabs.clear();
        int tabWidth = (panelWidth() - 24) / Section.values().length;
        for (Section tab : Section.values()) {
            tabs.add(addDrawableChild(KuButton.themed(Text.literal(tab.label), button -> changeSection(tab))
                    .accent(tab.accent()).card(net.minecraft.item.ItemStack.EMPTY, null, () -> section == tab)
                    .dimensions(left() + 10 + tab.ordinal() * tabWidth, top() + 30, tabWidth - 4, 22).build()));
        }
        int visible = visibleEntries();
        listOffset = Math.clamp(listOffset, 0, Math.max(0, entries.size() - visible));
        // Resizing must not hide the selected entry.
        if (selectedIndex < listOffset) listOffset = selectedIndex;
        if (selectedIndex >= listOffset + visible) listOffset = selectedIndex - visible + 1;
        for (int i = 0; i < Math.min(visible, entries.size() - listOffset); i++) {
            int index = listOffset + i;
            Entry entry = entries.get(index);
            entryButtons.add(addDrawableChild(KuButton.themed(Text.literal(entry.name()), button -> select(index))
                    .accent(section.accent()).card(entry.icon(), Text.literal(entry.category()), () -> selectedIndex == index)
                    .dimensions(left() + 10, top() + 66 + i * 36, listWidth(), 32).build()));
        }
        previous = addDrawableChild(KuButton.themed(Text.literal("前へ"), button -> page(-1))
                .accent(section.accent()).dimensions(left() + 10, top() + panelHeight() - 56, listWidth() / 2 - 2, 20).build());
        next = addDrawableChild(KuButton.themed(Text.literal("次へ"), button -> page(1))
                .accent(section.accent()).dimensions(left() + 12 + listWidth() / 2, top() + panelHeight() - 56,
                        listWidth() / 2 - 2, 20).build());
        previous.active = listOffset > 0;
        next.active = listOffset + visible < entries.size();
        closeButton = addDrawableChild(KuButton.themed(Text.literal("閉じる (K / Esc)"), button -> close())
                .accent(KuTheme.GOLD).dimensions(left() + panelWidth() - 118, top() + panelHeight() - 27, 108, 20).build());
        if (focusedTab >= 0) setInitialFocus(tabs.get(focusedTab));
        else if (previousFocused && previous.active) setInitialFocus(previous);
        else if (nextFocused && next.active) setInitialFocus(next);
        else if (closeFocused) setInitialFocus(closeButton);
        else if (!entryButtons.isEmpty()) setInitialFocus(entryButtons.get(focusedEntry < 0
                ? Math.clamp(selectedIndex - listOffset, 0, entryButtons.size() - 1)
                : Math.min(focusedEntry, entryButtons.size() - 1)));
        else setInitialFocus(tabs.get(section.ordinal()));
    }

    private void rebuild() { clearChildren(); init(); }

    private void changeSection(Section value) {
        if (section == value) return;
        section = value;
        entries = UniqueJournalCatalog.entries(section);
        selectedIndex = 0;
        listOffset = 0;
        details.reset();
        rebuild();
        setFocused(tabs.get(section.ordinal()));
    }

    private void select(int index) {
        selectedIndex = Math.clamp(index, 0, entries.size() - 1);
        details.reset();
    }

    private void page(int direction) {
        int target = Math.clamp(listOffset + direction * visibleEntries(), 0, Math.max(0, entries.size() - visibleEntries()));
        if (target == listOffset) return;
        listOffset = target;
        select(listOffset);
        rebuild();
    }

    @Override
    public void tick() {
        SyncPlayerStatusPayload live = StatusClient.latest();
        if (live != lastLiveStatus) {
            status = live;
            lastLiveStatus = live;
            cooldownTicks = live.cooldownTicks();
        } else if (cooldownTicks > 0) cooldownTicks--;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, KuTheme.BACKDROP);
        KuTheme.drawPanel(context, left(), top(), panelWidth(), panelHeight(), KuTheme.GOLD);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawTextWithShadow(textRenderer, title, left() + 10, top() + 9, KuTheme.GOLD);
        context.drawTextWithShadow(textRenderer, "効果と入手条件", left() + panelWidth() - 98, top() + 9, KuTheme.SECONDARY);
        context.drawTextWithShadow(textRenderer, section.label + " " + (selectedIndex + 1) + " / " + entries.size() + " 件",
                left() + 10, top() + 55, section.accent());
        KuTheme.drawSlot(context, detailX() - 3, top() + 57, detailWidth() + 3, panelHeight() - 91);
        if (!entries.isEmpty()) drawEntry(context, entries.get(selectedIndex));
        context.drawTextWithShadow(textRenderer, panelWidth() < 420 ? "←→: 項目  ↑↓: 詳細" : "↑↓: 詳細  ←→: 項目  Tab: 移動",
                left() + 10, top() + panelHeight() - 19, KuTheme.MUTED);
    }

    private void drawEntry(DrawContext context, Entry entry) {
        int x = detailX() + 4;
        int y = top() + 64;
        context.drawItem(entry.icon(), x, y);
        context.drawTextWithShadow(textRenderer, entry.category(), x + 23, y + 4, section.accent());
        details.begin(x, y + 25, detailWidth() - 10, panelHeight() - 131);
        details.paragraph(textRenderer, entry.name(), section.accent());
        if (entry.section() == Section.SKILL && entry.id().equals(status.abilityId())) {
            details.paragraph(textRenderer, status.abilitiesSealed() ? "覚醒中 ・現在は能力を封印されています"
                    : AbilityDescriptions.cooldownTicks(entry.id()) == 0 ? "覚醒中 ・常在効果"
                    : "覚醒中 ・残りクールタイム: " + (int) Math.ceil(cooldownTicks / 20.0) + "秒", KuTheme.MERIT);
            if ("divine_smith".equals(entry.id())) {
                details.paragraph(textRenderer, "採掘範囲: " + status.miningRange() + "×" + status.miningRange() + "ブロック", KuTheme.SECONDARY);
            }
        } else if (section == Section.SKILL || section == Section.RELIC || section == Section.CURSE) {
            details.paragraph(textRenderer, status.ownedIds().contains(entry.id()) ? "取得記録あり" : "未取得 ・図鑑の説明", KuTheme.SECONDARY);
        }
        details.paragraph(textRenderer, "【操作・発動】", KuTheme.SECONDARY);
        details.paragraph(textRenderer, entry.controls(), KuTheme.BODY);
        details.paragraph(textRenderer, "【効果】", KuTheme.MERIT);
        for (String line : entry.effects()) details.paragraph(textRenderer, line, KuTheme.BODY);
        details.paragraph(textRenderer, "【入手条件】", KuTheme.GOLD);
        details.paragraph(textRenderer, entry.acquisition(), KuTheme.BODY);
        if (section == Section.ARMOR) details.paragraph(textRenderer,
                "サバイバルで達成し、その防具が世界で未取得の場合に授与されます。", KuTheme.SECONDARY);
        else details.paragraph(textRenderer, "世界で取得済みの力は、ほかのプレイヤーには授与されません。", KuTheme.SECONDARY);
        details.render(context, textRenderer);
        if (!details.hint().isEmpty()) context.drawTextWithShadow(textRenderer, details.hint(),
                x, top() + panelHeight() - 44, KuTheme.SECONDARY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_K) { close(); return true; }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            details.scroll(keyCode == GLFW.GLFW_KEY_UP ? -1 : keyCode == GLFW.GLFW_KEY_DOWN ? 1
                    : keyCode == GLFW.GLFW_KEY_PAGE_UP ? -5 : 5);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) { details.reset(); return true; }
        if (keyCode == GLFW.GLFW_KEY_END) { details.end(); return true; }
        if ((keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) && !entries.isEmpty()) {
            select(Math.floorMod(selectedIndex + (keyCode == GLFW.GLFW_KEY_LEFT ? -1 : 1), entries.size()));
            if (selectedIndex < listOffset || selectedIndex >= listOffset + visibleEntries()) {
                listOffset = selectedIndex;
                rebuild();
            }
            setFocused(entryButtons.get(selectedIndex - listOffset));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical != 0 && details.contains(mouseX, mouseY)) { details.scroll(vertical > 0 ? -2 : 2); return true; }
        if (vertical != 0 && mouseX >= left() + 10 && mouseX < left() + 10 + listWidth()
                && mouseY >= top() + 55 && mouseY < top() + panelHeight() - 34) {
            page(vertical > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean shouldPause() { return false; }
}
