package com.example.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class WaypointScreen extends Screen {

    private static final int LIST_W = 310;
    private static final int SEARCH_H = 20;

    private static final int ROW_H = 22;
    private static final int ROW_GAP = 2;

    // row layout
    private static final int BTN_W = 20;
    private static final int BTN_GAP = 2;
    private static final int BTN_COUNT = 4;

    // icon textures
    private static final int ICON_TEX = 16;
    private static final int ICON_DRAW = 16;

    private static final Identifier ICO_EYE_OPEN = id("waypointmod:textures/gui/icons/eye_open.png");
    private static final Identifier ICO_EYE_CLOSED = id("waypointmod:textures/gui/icons/eye_closed.png");
    private static final Identifier ICO_CHAT = id("waypointmod:textures/gui/icons/chat.png");

    private static Identifier id(String s) {
        return Objects.requireNonNull(Identifier.tryParse(s), "Bad Identifier: " + s);
    }

    private EditBox search;

    private Button addBtn;
    private Button doneBtn;
    private Button settingsBtn;

    // tabs
    private enum Tab { ALL, DEATH, FAV, GLOBAL }
    private Tab tab = Tab.ALL;

    private Button tabAllBtn;
    private Button tabDeathBtn;
    private Button tabFavBtn;
    private Button tabGlobalBtn;

    // list geometry
    private int listLeft, listRight;
    private int listTop, listBottom;

    private int scrollY, maxScroll, contentH;

    private List<WaypointStorage.Waypoint> filtered = List.of();
    private final List<Row> rows = new ArrayList<>();

    private int rowSlots = 0;

    private static final class Row {
        Button main;   // big left area (click -> edit)
        Button hide;   // eye icon
        Button fav;    // star
        Button send;   // chat icon
        Button del;    // X

        Identifier eyeIcon = ICO_EYE_CLOSED;
        WaypointStorage.Waypoint wp;
        boolean visible;
    }

    public WaypointScreen() {
        super(Component.translatable("screen.waypointmod.title"));
    }

    @Override
    protected void init() {
        this.clearWidgets();
        rows.clear();

        int cx = this.width / 2;

        listLeft = cx - (LIST_W / 2);
        listRight = listLeft + LIST_W;

        int y = 20;

        // search
        search = new EditBox(this.font, listLeft, y, LIST_W, SEARCH_H,
                Component.translatable("screen.waypointmod.search"));
        search.setResponder(s -> refreshAndLayout()); // without recreating row buttons
        addRenderableWidget(search);
        y += SEARCH_H + 6;

        // tabs row
        int tabY = y;
        int tabH = 20;
        int tabW = (LIST_W - 6) / 4;
        int gap = 2;

        tabAllBtn = Button.builder(Component.translatable("screen.waypointmod.tab.all"), b -> setTab(Tab.ALL))
                .bounds(listLeft, tabY, tabW, tabH)
                .build();
        addRenderableWidget(tabAllBtn);

        tabDeathBtn = Button.builder(Component.translatable("screen.waypointmod.tab.death"), b -> setTab(Tab.DEATH))
                .bounds(listLeft + tabW + gap, tabY, tabW, tabH)
                .build();
        addRenderableWidget(tabDeathBtn);

        tabFavBtn = Button.builder(Component.translatable("screen.waypointmod.tab.fav"), b -> setTab(Tab.FAV))
                .bounds(listLeft + (tabW + gap) * 2, tabY, tabW, tabH)
                .build();
        addRenderableWidget(tabFavBtn);

        tabGlobalBtn = Button.builder(Component.translatable("screen.waypointmod.tab.global"), b -> setTab(Tab.GLOBAL))
                .bounds(listLeft + (tabW + gap) * 3, tabY, tabW, tabH)
                .build();
        tabGlobalBtn.active = false; // future feature
        addRenderableWidget(tabGlobalBtn);

        y += tabH + 10;

        // list viewport
        listTop = y;
        listBottom = this.height - 52;
        if (listBottom < listTop + 30) listBottom = listTop + 30;

        // bottom buttons
        int btnY = this.height - 28;

        addBtn = Button.builder(Component.translatable("screen.waypointmod.add"), b -> addHere())
                .bounds(listLeft, btnY, 80, 20)
                .build();
        addRenderableWidget(addBtn);

        settingsBtn = Button.builder(Component.translatable("screen.waypointmod.settings"), b -> {
                    if (this.minecraft != null) this.minecraft.setScreen(new WaypointSettingsScreen(this));
                })
                .bounds(listLeft + 84, btnY, 100, 20)
                .build();
        addRenderableWidget(settingsBtn);

        doneBtn = Button.builder(Component.translatable("screen.waypointmod.done"), b -> onClose())
                .bounds(listRight - 80, btnY, 80, 20)
                .build();
        addRenderableWidget(doneBtn);

        // create row slots once for current viewport
        createRowWidgets();

        // fill data
        refreshAndLayout();
        this.setInitialFocus(search);
    }

    private void createRowWidgets() {
        int viewportH = Math.max(1, listBottom - listTop);
        rowSlots = Math.max(1, viewportH / (ROW_H + ROW_GAP)) + 2;

        int rightBtnAreaW = BTN_COUNT * BTN_W + (BTN_COUNT - 1) * BTN_GAP;
        int mainW = LIST_W - rightBtnAreaW - 6;

        for (int i = 0; i < rowSlots; i++) {
            final int slot = i;
            Row r = new Row();

            r.main = Button.builder(Component.literal(""), b -> editRowSlot(slot))
                    .bounds(listLeft, 0, mainW, ROW_H)
                    .build();

            int bx = listLeft + mainW + 6;

            r.hide = Button.builder(Component.empty(), b -> toggleHiddenSlot(slot))
                    .bounds(bx + (BTN_W + BTN_GAP) * 0, 0, BTN_W, ROW_H)
                    .build();

            r.fav = Button.builder(Component.literal("☆"), b -> toggleFavoriteSlot(slot))
                    .bounds(bx + (BTN_W + BTN_GAP) * 1, 0, BTN_W, ROW_H)
                    .build();

            r.send = Button.builder(Component.empty(), b -> sendSlotToChat(slot))
                    .bounds(bx + (BTN_W + BTN_GAP) * 2, 0, BTN_W, ROW_H)
                    .build();

            r.del = Button.builder(Component.literal("✖"), b -> deleteSlot(slot))
                    .bounds(bx + (BTN_W + BTN_GAP) * 3, 0, BTN_W, ROW_H)
                    .build();

            rows.add(r);

            addRenderableWidget(r.main);
            addRenderableWidget(r.hide);
            addRenderableWidget(r.fav);
            addRenderableWidget(r.send);
            addRenderableWidget(r.del);
        }
    }

    private void refreshAndLayout() {
        refreshFiltered();
        recomputeScrollLimits();
        layoutRows();
        updateTabButtons();
    }

    private void refreshFiltered() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            filtered = List.of();
            scrollY = 0;
            return;
        }

        List<WaypointStorage.Waypoint> all = WaypointStorage.listForCurrentAll(mc);

        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        ArrayList<WaypointStorage.Waypoint> tmp = new ArrayList<>();

        for (var w : all) {
            boolean isDeath = "death".equalsIgnoreCase(w.kind);

            if (tab == Tab.DEATH && !isDeath) continue;
            if (tab == Tab.FAV && !w.favorite) continue;
            if (tab == Tab.ALL && isDeath) continue;    // "Метки" без смертей
            if (tab == Tab.GLOBAL) continue;            // future

            String n = (w.name == null ? "" : w.name);
            if (q.isEmpty() || n.toLowerCase(Locale.ROOT).contains(q)) tmp.add(w);
        }

        // favorites first, then distance
        tmp.sort((a, b) -> {
            if (a.favorite != b.favorite) return a.favorite ? -1 : 1;
            return Double.compare(distSq(mc, a), distSq(mc, b));
        });

        filtered = tmp;
    }

    private void recomputeScrollLimits() {
        contentH = filtered.size() * (ROW_H + ROW_GAP);
        int viewportH = Math.max(1, listBottom - listTop);
        maxScroll = Math.max(0, contentH - viewportH);
        scrollY = Mth.clamp(scrollY, 0, maxScroll);
    }

    private void layoutRows() {
        Minecraft mc = Minecraft.getInstance();

        int step = (ROW_H + ROW_GAP);
        int first = scrollY / step;

        for (int slot = 0; slot < rows.size(); slot++) {
            Row r = rows.get(slot);

            int idx = first + slot;
            WaypointStorage.Waypoint w = (idx >= 0 && idx < filtered.size()) ? filtered.get(idx) : null;

            int y = listTop + slot * step - (scrollY % step);

            boolean vis = (w != null) && (y + ROW_H) > listTop && y < listBottom;

            r.wp = w;
            r.visible = vis;

            setRowWidget(r.main, y, vis);
            setRowWidget(r.hide, y, vis);
            setRowWidget(r.fav,  y, vis);
            setRowWidget(r.send, y, vis);
            setRowWidget(r.del,  y, vis);

            if (!vis || w == null) continue;

            String name = (w.name == null ? Component.translatable("screen.waypointmod.unnamed").getString() : w.name);
            int dist = (mc.player == null) ? 0 : (int) Math.round(Math.sqrt(distSq(mc, w)));

            String flags = "";
            if (w.hidden) flags += " [H]";
            boolean isDeath = "death".equalsIgnoreCase(w.kind);
            if (isDeath) flags += " [D]";

            String star = w.favorite ? "★ " : "";
            int color = isDeath ? 0xFF5555 : 0xFFFFFF;

            r.main.setMessage(Component.empty()
                    .append(Component.literal(star + name + flags + "  ("))
                    .append(Component.translatable("screen.waypointmod.distance_m", dist))
                    .append(Component.literal(")"))
                    .withStyle(style -> style.withColor(color)));

            r.eyeIcon = w.hidden ? ICO_EYE_OPEN : ICO_EYE_CLOSED;
            r.fav.setMessage(w.favorite ? Component.literal("★") : Component.literal("☆"));
        }
    }

    private void setRowWidget(Button b, int y, boolean vis) {
        b.setY(y);
        b.visible = vis;
        b.active = vis;
    }

    private WaypointStorage.Waypoint wpForSlot(int slot) {
        if (slot < 0 || slot >= rows.size()) return null;
        return rows.get(slot).wp;
    }

    private void editRowSlot(int slot) {
        WaypointStorage.Waypoint w = wpForSlot(slot);
        if (w == null || this.minecraft == null) return;
        this.minecraft.setScreen(new WaypointEditScreen(this, w.name, cloneWp(w)));
    }

    private void deleteSlot(int slot) {
        Minecraft mc = Minecraft.getInstance();
        WaypointStorage.Waypoint w = wpForSlot(slot);
        if (w == null) return;
        WaypointStorage.remove(mc, w.name);
        refreshAndLayout();
    }

    private void toggleHiddenSlot(int slot) {
        Minecraft mc = Minecraft.getInstance();
        WaypointStorage.Waypoint w = wpForSlot(slot);
        if (w == null) return;
        WaypointStorage.toggleHidden(mc, w.name);
        refreshAndLayout();
    }

    private void toggleFavoriteSlot(int slot) {
        Minecraft mc = Minecraft.getInstance();
        WaypointStorage.Waypoint w = wpForSlot(slot);
        if (w == null) return;
        WaypointStorage.toggleFavorite(mc, w.name);
        refreshAndLayout();
    }

    private void sendSlotToChat(int slot) {
        Minecraft mc = Minecraft.getInstance();
        WaypointStorage.Waypoint w = wpForSlot(slot);
        if (w == null) return;

        String name = (w.name == null || w.name.isBlank()) ? "wp" : w.name;
        name = name.replaceAll("\\s+", "_");

        String msg = "WP|" + name + "|" + w.x + "|" + w.y + "|" + w.z;
        mc.setScreen(new ChatScreen(msg, false));
    }

    private void addHere() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int x = Mth.floor(mc.player.getX());
        int y = Mth.floor(mc.player.getY());
        int z = Mth.floor(mc.player.getZ());

        WaypointStorage.Waypoint draft = new WaypointStorage.Waypoint(
                "New",
                WaypointStorage.currentWorldId(mc),
                WaypointStorage.currentDimId(mc),
                x, y, z,
                0x55FF55);
        draft.kind = "normal";
        draft.createdAt = System.currentTimeMillis();
        draft.hidden = false;
        draft.favorite = false;

        if (this.minecraft != null) this.minecraft.setScreen(new WaypointEditScreen(this, null, draft));
    }

    private boolean handleScroll(double mouseX, double mouseY, double amount) {
        if (mouseX < listLeft || mouseX >= listRight || mouseY < listTop || mouseY >= listBottom) return false;

        int old = scrollY;
        scrollY = Mth.clamp(scrollY - (int) Math.round(amount * 18), 0, maxScroll);
        if (scrollY != old) {
            layoutRows();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (handleScroll(mouseX, mouseY, vertical)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x88000000);

        g.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);

        // scissor for list
        g.enableScissor(listLeft, listTop, listRight, listBottom);
        for (Row r : rows) {
            if (!r.visible) continue;

            r.main.render(g, mouseX, mouseY, partialTick);
            r.hide.render(g, mouseX, mouseY, partialTick);
            r.fav.render(g, mouseX, mouseY, partialTick);
            r.send.render(g, mouseX, mouseY, partialTick);
            r.del.render(g, mouseX, mouseY, partialTick);

            drawCenteredIcon(g, r.hide, r.eyeIcon);
            drawCenteredIcon(g, r.send, ICO_CHAT);
        }
        g.disableScissor();

        // render non-list widgets (avoid double-render of rows)
        search.render(g, mouseX, mouseY, partialTick);
        tabAllBtn.render(g, mouseX, mouseY, partialTick);
        tabDeathBtn.render(g, mouseX, mouseY, partialTick);
        tabFavBtn.render(g, mouseX, mouseY, partialTick);
        tabGlobalBtn.render(g, mouseX, mouseY, partialTick);

        addBtn.render(g, mouseX, mouseY, partialTick);
        settingsBtn.render(g, mouseX, mouseY, partialTick);
        doneBtn.render(g, mouseX, mouseY, partialTick);

        drawScrollbar(g);
    }

    private void drawScrollbar(GuiGraphics g) {
        int viewportH = Math.max(1, listBottom - listTop);
        if (contentH <= viewportH) return;

        int x1 = listRight + 4;
        int x2 = x1 + 4;
        int y1 = listTop;
        int y2 = listBottom;

        g.fill(x1, y1, x2, y2, 0xFF202020);

        float ratio = viewportH / (float) contentH;
        int thumbH = Mth.clamp((int) (viewportH * ratio), 16, viewportH);
        int maxMove = viewportH - thumbH;

        int thumbY = y1 + (maxScroll == 0 ? 0 : (int) (maxMove * (scrollY / (float) maxScroll)));
        g.fill(x1, thumbY, x2, thumbY + thumbH, 0xFFB0B0B0);
    }

    private static void drawCenteredIcon(GuiGraphics g, Button btn, Identifier icon) {
        int cx = btn.getX() + (btn.getWidth() - ICON_DRAW) / 2;
        int cy = btn.getY() + (btn.getHeight() - ICON_DRAW) / 2;

        g.blit(
                RenderPipelines.GUI_TEXTURED,
                icon,
                cx, cy,
                0f, 0f,
                ICON_DRAW, ICON_DRAW,
                ICON_TEX, ICON_TEX);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static double distSq(Minecraft mc, WaypointStorage.Waypoint w) {
        if (mc.player == null) return 0;
        double dx = (w.x + 0.5) - mc.player.getX();
        double dy = (w.y + 0.0) - mc.player.getY();
        double dz = (w.z + 0.5) - mc.player.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static WaypointStorage.Waypoint cloneWp(WaypointStorage.Waypoint src) {
        WaypointStorage.Waypoint w = new WaypointStorage.Waypoint(
                src.name, src.world, src.dimension, src.x, src.y, src.z, src.color);
        w.kind = src.kind;
        w.createdAt = src.createdAt;
        w.hidden = src.hidden;
        w.favorite = src.favorite;
        return w;
    }

    public void forceRefreshAfterChild() {
        refreshAndLayout();
    }

    private void setTab(Tab t) {
        if (this.tab == t) return;
        this.tab = t;
        this.scrollY = 0;
        refreshAndLayout();
    }

    private void updateTabButtons() {
        if (tabAllBtn != null)   tabAllBtn.active = (tab != Tab.ALL);
        if (tabDeathBtn != null) tabDeathBtn.active = (tab != Tab.DEATH);
        if (tabFavBtn != null)   tabFavBtn.active = (tab != Tab.FAV);
        if (tabGlobalBtn != null) tabGlobalBtn.active = false;
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        init();
    }
}