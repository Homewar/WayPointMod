package com.example.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class WaypointSettingsScreen extends Screen {

    // "как Game Rules" — фиксированная ширина списка
    private static final int LIST_W = 310;
    private static final int ROW_H = 24;

    // кнопка ON/OFF справа
    private static final int TOGGLE_W = 60;
    private static final int TOGGLE_H = 20;

    private static final int SCROLL_STEP = 18;

    private final Screen parent;

    // bottom buttons
    private Button doneBtn;
    private Button cancelBtn;

    // list state
    private int listLeft, listRight, listTop, listBottom;
    private int scrollY, maxScroll;
    private int contentH;

    // entries
    private final List<Entry> entries = new ArrayList<>();
    private final List<Button> rowButtons = new ArrayList<>();

    // snapshot for Cancel
    private boolean snapEnabled;
    private boolean snapHudEnabled;
    private boolean snapLocatorHudEnabled;
    private boolean snapWaypointHudEnabled;
    private boolean snapAutoPointsEnabled;

    private boolean snapLocatorShowTicks;
    private boolean snapLocatorUseFlatBar;
    private boolean snapLocatorShowDirections;
    private boolean snapLocatorShowMarkers;

    public WaypointSettingsScreen(Screen parent) {
        super(Component.translatable("screen.waypointmod.settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        captureSnapshot();

        entries.clear();
        rowButtons.clear();
        this.clearWidgets();

        // centered list box
        int cx = this.width / 2;
        listLeft = cx - (LIST_W / 2);
        listRight = listLeft + LIST_W;

        listTop = 32;
        listBottom = this.height - 40; // место под Done/Cancel

        buildEntries();
        buildRowButtons();

        // scroll limits
        contentH = entries.size() * ROW_H;
        int viewportH = Math.max(0, listBottom - listTop);
        maxScroll = Math.max(0, contentH - viewportH);
        scrollY = Mth.clamp(scrollY, 0, maxScroll);
        layoutRowButtons();

        // bottom buttons like Game Rules: Done / Cancel
        int btnW = 150;
        int btnY = this.height - 28;
        doneBtn = Button.builder(Component.literal("Done"), b -> onDone())
                .bounds(cx - btnW - 5, btnY, btnW, 20)
                .build();
        cancelBtn = Button.builder(Component.literal("Cancel"), b -> onCancel())
                .bounds(cx + 5, btnY, btnW, 20)
                .build();

        addRenderableWidget(doneBtn);
        addRenderableWidget(cancelBtn);
    }

    private void buildEntries() {
        // Section: Master
        entries.add(Entry.header("Master"));
        entries.add(Entry.toggle("Waypoints", () -> WaypointStorage.isEnabled(), () -> WaypointStorage.toggle()));
        entries.add(Entry.toggle("HUD Master", () -> WaypointStorage.isHudEnabled(), () -> WaypointStorage.toggleHudEnabled()));
        entries.add(Entry.toggle("Locator HUD", () -> WaypointStorage.isLocatorHudEnabled(), () -> WaypointStorage.toggleLocatorHudEnabled()));
        entries.add(Entry.toggle("Waypoint HUD List", () -> WaypointStorage.isWaypointHudEnabled(), () -> WaypointStorage.toggleWaypointHudEnabled()));
        entries.add(Entry.toggle("Auto Points", () -> WaypointStorage.isAutoPointsEnabled(), () -> WaypointStorage.toggleAutoPoints()));

        // Section: Locator
        entries.add(Entry.header("Locator"));

        entries.add(Entry.toggle("Locator ticks",
                () -> WaypointStorage.isLocatorShowTicks(),
                () -> {
                    WaypointStorage.toggleLocatorShowTicks();
                    // если включили риски — отключаем flat bar
                    if (WaypointStorage.isLocatorShowTicks() && WaypointStorage.isLocatorUseFlatBar()) {
                        WaypointStorage.toggleLocatorUseFlatBar();
                    }
                }
        ));

        entries.add(Entry.toggle("Flat bar instead of ticks",
                () -> WaypointStorage.isLocatorUseFlatBar(),
                () -> {
                    WaypointStorage.toggleLocatorUseFlatBar();
                    // если включили flat bar — отключаем риски
                    if (WaypointStorage.isLocatorUseFlatBar() && WaypointStorage.isLocatorShowTicks()) {
                        WaypointStorage.toggleLocatorShowTicks();
                    }
                }
        ));

        entries.add(Entry.toggle("Compass labels (N/NE..)",
                () -> WaypointStorage.isLocatorShowDirections(),
                () -> WaypointStorage.toggleLocatorShowDirections()
        ));

        entries.add(Entry.toggle("Waypoint markers",
                () -> WaypointStorage.isLocatorShowMarkers(),
                () -> WaypointStorage.toggleLocatorShowMarkers()
        ));
    }

    private void buildRowButtons() {
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (!e.isToggle) continue;

            Button btn = Button.builder(Component.literal(""), b -> {
                        e.toggle.run();
                        updateButtonLabel((Button) b, e);
                    })
                    .bounds(0, 0, TOGGLE_W, TOGGLE_H) // layout later
                    .build();

            rowButtons.add(btn);
            addRenderableWidget(btn);
        }

        // initial label sync
        int bi = 0;
        for (Entry e : entries) {
            if (!e.isToggle) continue;
            updateButtonLabel(rowButtons.get(bi++), e);
        }
    }

    private void updateButtonLabel(Button btn, Entry e) {
        btn.setMessage(Component.literal(e.getState.getAsBoolean() ? "ON" : "OFF"));
    }

    private void layoutRowButtons() {
        int bi = 0;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (!e.isToggle) continue;

            int rowY = listTop + i * ROW_H - scrollY;
            int btnX = listRight - 6 - TOGGLE_W;
            int btnY = rowY + (ROW_H - TOGGLE_H) / 2;

            Button b = rowButtons.get(bi++);
            b.setX(btnX);
            b.setY(btnY);

            boolean visible = (rowY + ROW_H) > listTop && rowY < listBottom;
            b.visible = visible;
            b.active = visible; // вне окна не кликается
        }
    }

    private void captureSnapshot() {
        snapEnabled = WaypointStorage.isEnabled();
        snapHudEnabled = WaypointStorage.isHudEnabled();
        snapLocatorHudEnabled = WaypointStorage.isLocatorHudEnabled();
        snapWaypointHudEnabled = WaypointStorage.isWaypointHudEnabled();
        snapAutoPointsEnabled = WaypointStorage.isAutoPointsEnabled();

        snapLocatorShowTicks = WaypointStorage.isLocatorShowTicks();
        snapLocatorUseFlatBar = WaypointStorage.isLocatorUseFlatBar();
        snapLocatorShowDirections = WaypointStorage.isLocatorShowDirections();
        snapLocatorShowMarkers = WaypointStorage.isLocatorShowMarkers();
    }

    private void restoreSnapshot() {
        setIfDifferent(() -> WaypointStorage.isEnabled(), snapEnabled, WaypointStorage::toggle);
        setIfDifferent(() -> WaypointStorage.isHudEnabled(), snapHudEnabled, WaypointStorage::toggleHudEnabled);
        setIfDifferent(() -> WaypointStorage.isLocatorHudEnabled(), snapLocatorHudEnabled, WaypointStorage::toggleLocatorHudEnabled);
        setIfDifferent(() -> WaypointStorage.isWaypointHudEnabled(), snapWaypointHudEnabled, WaypointStorage::toggleWaypointHudEnabled);
        setIfDifferent(() -> WaypointStorage.isAutoPointsEnabled(), snapAutoPointsEnabled, WaypointStorage::toggleAutoPoints);

        setIfDifferent(() -> WaypointStorage.isLocatorShowTicks(), snapLocatorShowTicks, WaypointStorage::toggleLocatorShowTicks);
        setIfDifferent(() -> WaypointStorage.isLocatorUseFlatBar(), snapLocatorUseFlatBar, WaypointStorage::toggleLocatorUseFlatBar);
        setIfDifferent(() -> WaypointStorage.isLocatorShowDirections(), snapLocatorShowDirections, WaypointStorage::toggleLocatorShowDirections);
        setIfDifferent(() -> WaypointStorage.isLocatorShowMarkers(), snapLocatorShowMarkers, WaypointStorage::toggleLocatorShowMarkers);
    }

    private void setIfDifferent(BoolSupplier getter, boolean desired, Runnable toggler) {
        if (getter.getAsBoolean() != desired) toggler.run();
    }

    private void onDone() {
        onClose();
    }

    private void onCancel() {
        restoreSnapshot();
        onClose();
    }

    @Override
    public void onClose() {
        Minecraft mc = this.minecraft;
        if (mc != null) mc.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean inList(double mx, double my) {
        return mx >= listLeft && mx < listRight && my >= listTop && my < listBottom;
    }

    private boolean handleScroll(double mouseX, double mouseY, double amount) {
        if (!inList(mouseX, mouseY)) return false;

        int old = scrollY;
        scrollY = Mth.clamp(scrollY - (int) Math.round(amount * SCROLL_STEP), 0, maxScroll);
        if (scrollY != old) {
            layoutRowButtons();
            return true;
        }
        return false;
    }

    // 4-arg variant
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (handleScroll(mouseX, mouseY, vertical)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x88000000);
        g.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        g.enableScissor(listLeft, listTop, listRight, listBottom);

        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);

            int rowY = listTop + i * ROW_H - scrollY;
            if ((rowY + ROW_H) <= listTop || rowY >= listBottom) continue;

            int textX = listLeft + 6;
            int textY = rowY + (ROW_H - 8) / 2;

            if (e.isHeader) {
                g.drawCenteredString(this.font, Component.literal(e.label), this.width / 2, rowY + 6, 0xFFFFD34F);
            } else {
                g.drawString(this.font, Component.literal(e.label), textX, textY, 0xFFFFFFFF, false);
            }
        }

        for (Button b : rowButtons) {
            if (b.visible) b.render(g, mouseX, mouseY, partialTick);
        }

        g.disableScissor();

        drawScrollbar(g);

        doneBtn.render(g, mouseX, mouseY, partialTick);
        cancelBtn.render(g, mouseX, mouseY, partialTick);
    }

    private void drawScrollbar(GuiGraphics g) {
        int viewportH = Math.max(1, listBottom - listTop);
        if (contentH <= viewportH) return;

        int barX1 = listRight + 4;
        int barX2 = barX1 + 4;
        int barY1 = listTop;
        int barY2 = listBottom;

        // track
        g.fill(barX1, barY1, barX2, barY2, 0xFF202020);

        // thumb size & pos
        float ratio = viewportH / (float) contentH;
        int thumbH = Mth.clamp((int) (viewportH * ratio), 16, viewportH);
        int maxThumbMove = viewportH - thumbH;

        int thumbY = barY1 + (maxScroll == 0 ? 0 : (int) (maxThumbMove * (scrollY / (float) maxScroll)));
        g.fill(barX1, thumbY, barX2, thumbY + thumbH, 0xFFB0B0B0);
    }

    // --- small helpers & entry model ---
    private interface BoolSupplier { boolean getAsBoolean(); }

    private static final class Entry {
        final boolean isHeader;
        final boolean isToggle;

        final String label;
        final BoolSupplier getState;
        final Runnable toggle;

        private Entry(boolean isHeader, boolean isToggle, String label, BoolSupplier getState, Runnable toggle) {
            this.isHeader = isHeader;
            this.isToggle = isToggle;
            this.label = label;
            this.getState = getState;
            this.toggle = toggle;
        }

        static Entry header(String label) {
            return new Entry(true, false, label, null, null);
        }

        static Entry toggle(String label, BoolSupplier getState, Runnable toggle) {
            return new Entry(false, true, label, getState, toggle);
        }
    }
}
