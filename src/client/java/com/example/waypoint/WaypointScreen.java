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
    private static final int PANEL_W = 248;
    private static final int PANEL_H = 222;

    private static final int PAD = 10;
    private static final int ROW_H = 18;

    // Actions: Edit, Eye, Fav, Chat, Delete
    private static final int ACT_W = 18;
    private static final int ACT_GAP = 2;
    private static final int ACT_MAIN_GAP = 2;
    private static final int ACT_COUNT = 5;

    // Текстуры иконок (только для глаза и чата)
    // Рекомендуется делать PNG 16x16 и рисовать 16x16 (без обрезки)
    private static final int ICON_TEX = 16;
    private static final int ICON_DRAW = 16;

    private static final Identifier ICO_EYE_OPEN   = id("waypointmod:textures/gui/icons/eye_open.png");
    private static final Identifier ICO_EYE_CLOSED = id("waypointmod:textures/gui/icons/eye_closed.png");
    private static final Identifier ICO_CHAT       = id("waypointmod:textures/gui/icons/chat.png");

    private static Identifier id(String s) {
        return Objects.requireNonNull(Identifier.tryParse(s), "Bad Identifier: " + s);
    }

    private EditBox search;

    private Button prevBtn;
    private Button nextBtn;
    private Button addBtn;

    private Button toggleModBtn;
    private Button autoPointsBtn;

    private final List<Row> rows = new ArrayList<>();

    private List<WaypointStorage.Waypoint> filtered = List.of();
    private int page = 0;

    private int pageSize = 6;
    private int titleY = 0;

    private static final class Row {
        Button main;

        Button edit;    // текст
        Button hide;    // обычная кнопка + рисуем глаз поверх
        Button fav;     // текст
        Button send;    // обычная кнопка + рисуем чат поверх
        Button del;     // текст

        // какая иконка глаза сейчас нужна
        Identifier eyeIcon = ICO_EYE_CLOSED;
    }

    public WaypointScreen() {
        super(Component.translatable("screen.waypointmod.title"));
    }

    @Override
    protected void init() {
        rows.clear();

        int x0 = (this.width - PANEL_W) / 2;
        int y0 = (this.height - PANEL_H) / 2;

        titleY = y0 + 6;

        int searchY = y0 + 18;
        int listTop = y0 + 42;

        int togglesY = y0 + 150;
        int navY = y0 + 176;

        search = new EditBox(this.font, x0 + PAD, searchY, PANEL_W - PAD * 2, 18,
                Component.translatable("screen.waypointmod.search"));
        search.setResponder(s -> refresh());
        this.addRenderableWidget(search);

        int listBottom = togglesY - 4;
        pageSize = Math.max(1, (listBottom - listTop) / ROW_H);

        int actionsW = ACT_COUNT * ACT_W + (ACT_COUNT - 1) * ACT_GAP;
        int mainW = (PANEL_W - PAD * 2) - actionsW - ACT_MAIN_GAP;

        for (int i = 0; i < pageSize; i++) {
            int yy = listTop + i * ROW_H;
            final int rowIndex = i;

            Row r = new Row();

            r.main = Button.builder(Component.literal(""), b -> onRowClick(rowIndex))
                    .bounds(x0 + PAD, yy, mainW, ROW_H)
                    .build();

            int ax = x0 + PAD + mainW + ACT_MAIN_GAP;

            // Edit (текстовая)
            r.edit = Button.builder(Component.literal("✎"), b -> editRow(rowIndex))
                    .bounds(ax + (ACT_W + ACT_GAP) * 0, yy, ACT_W, ROW_H)
                    .build();

            // Eye (обычная кнопка, текст пустой; иконку рисуем в render)
            r.hide = Button.builder(Component.empty(), b -> toggleHiddenRow(rowIndex))
                    .bounds(ax + (ACT_W + ACT_GAP) * 1, yy, ACT_W, ROW_H)
                    .build();

            // Fav (текстовая)
            r.fav = Button.builder(Component.literal("☆"), b -> toggleFavoriteRow(rowIndex))
                    .bounds(ax + (ACT_W + ACT_GAP) * 2, yy, ACT_W, ROW_H)
                    .build();

            // Chat (обычная кнопка, текст пустой; иконку рисуем в render)
            r.send = Button.builder(Component.empty(), b -> sendRowToChat(rowIndex))
                    .bounds(ax + (ACT_W + ACT_GAP) * 3, yy, ACT_W, ROW_H)
                    .build();

            // Delete (текстовая)
            r.del = Button.builder(Component.literal("✖"), b -> deleteRow(rowIndex))
                    .bounds(ax + (ACT_W + ACT_GAP) * 4, yy, ACT_W, ROW_H)
                    .build();

            rows.add(r);

            this.addRenderableWidget(r.main);
            this.addRenderableWidget(r.edit);
            this.addRenderableWidget(r.hide);
            this.addRenderableWidget(r.fav);
            this.addRenderableWidget(r.send);
            this.addRenderableWidget(r.del);
        }

        int halfW = (PANEL_W - PAD * 2 - 2) / 2;

        toggleModBtn = Button.builder(Component.translatable("screen.waypointmod.toggle"), b -> {
                    WaypointStorage.toggle();
                    refreshButtons();
                })
                .bounds(x0 + PAD, togglesY, halfW, 20)
                .build();
        this.addRenderableWidget(toggleModBtn);

        autoPointsBtn = Button.builder(Component.translatable("screen.waypointmod.auto_points"), b -> {
                    WaypointStorage.toggleAutoPoints();
                    refreshButtons();
                })
                .bounds(x0 + PAD + halfW + 2, togglesY, halfW, 20)
                .build();
        this.addRenderableWidget(autoPointsBtn);

        prevBtn = Button.builder(Component.translatable("screen.waypointmod.prev"), b -> {
            page = Math.max(0, page - 1);
            rebuildRows();
        }).bounds(x0 + PAD, navY, 52, 20).build();
        this.addRenderableWidget(prevBtn);

        nextBtn = Button.builder(Component.translatable("screen.waypointmod.next"), b -> {
            int maxPage = Math.max(0, (filtered.size() - 1) / pageSize);
            page = Math.min(maxPage, page + 1);
            rebuildRows();
        }).bounds(x0 + PAD + 56, navY, 52, 20).build();
        this.addRenderableWidget(nextBtn);

        addBtn = Button.builder(Component.translatable("screen.waypointmod.add"), b -> addHere())
                .bounds(x0 + PAD + 112, navY, PANEL_W - PAD * 2 - 112, 20)
                .build();
        this.addRenderableWidget(addBtn);

        refresh();
        this.setInitialFocus(search);
    }

    private void refresh() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            filtered = List.of();
            page = 0;
            rebuildRows();
            refreshButtons();
            return;
        }

        List<WaypointStorage.Waypoint> all = WaypointStorage.listForCurrentAll(mc);

        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        ArrayList<WaypointStorage.Waypoint> tmp = new ArrayList<>();
        for (var w : all) {
            String n = (w.name == null ? "" : w.name);
            if (q.isEmpty() || n.toLowerCase(Locale.ROOT).contains(q)) tmp.add(w);
        }

        // Избранные первыми, затем по дистанции
        tmp.sort((a, b) -> {
            if (a.favorite != b.favorite) return a.favorite ? -1 : 1;
            return Double.compare(distSq(mc, a), distSq(mc, b));
        });

        filtered = tmp;

        int maxPage = Math.max(0, (filtered.size() - 1) / pageSize);
        page = Mth.clamp(page, 0, maxPage);

        rebuildRows();
        refreshButtons();
    }

    private WaypointStorage.Waypoint getAtRow(int rowIndex) {
        int idx = page * pageSize + rowIndex;
        if (idx < 0 || idx >= filtered.size()) return null;
        return filtered.get(idx);
    }

    private void rebuildRows() {
        Minecraft mc = Minecraft.getInstance();

        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            WaypointStorage.Waypoint w = getAtRow(i);

            if (w != null) {
                String name = (w.name == null ? "(unnamed)" : w.name);
                int dist = (mc.player == null) ? 0 : (int) Math.round(Math.sqrt(distSq(mc, w)));

                String flags = "";
                if (w.hidden) flags += " [H]";
                if ("death".equalsIgnoreCase(w.kind)) flags += " [D]";

                String star = w.favorite ? "★ " : "";
                r.main.setMessage(Component.literal(star + name + flags + "  (" + dist + "m)"));

                r.main.visible = true; r.main.active = true;

                r.edit.setMessage(Component.literal("✎"));
                r.edit.visible = true; r.edit.active = true;

                r.eyeIcon = w.hidden ? ICO_EYE_OPEN : ICO_EYE_CLOSED;
                r.hide.visible = true; r.hide.active = true;

                r.fav.setMessage(w.favorite ? Component.literal("★") : Component.literal("☆"));
                r.fav.visible = true; r.fav.active = true;

                r.send.visible = true; r.send.active = true;

                r.del.setMessage(Component.literal("✖"));
                r.del.visible = true; r.del.active = true;

            } else {
                r.main.setMessage(Component.literal(""));
                r.main.visible = true; r.main.active = false;

                r.edit.visible = false; r.edit.active = false;
                r.hide.visible = false; r.hide.active = false;
                r.fav.visible = false;  r.fav.active = false;
                r.send.visible = false; r.send.active = false;
                r.del.visible = false;  r.del.active = false;
            }
        }

        int maxPage = Math.max(0, (filtered.size() - 1) / pageSize);
        prevBtn.active = page > 0;
        nextBtn.active = page < maxPage;
    }

    private void onRowClick(int rowIndex) {
        editRow(rowIndex);
    }

    private void refreshButtons() {
        toggleModBtn.setMessage(WaypointStorage.isEnabled()
                ? Component.translatable("screen.waypointmod.on")
                : Component.translatable("screen.waypointmod.off"));

        autoPointsBtn.setMessage(WaypointStorage.isAutoPointsEnabled()
                ? Component.translatable("screen.waypointmod.auto_points.on")
                : Component.translatable("screen.waypointmod.auto_points.off"));
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
                0x55FF55
        );
        draft.kind = "normal";
        draft.createdAt = System.currentTimeMillis();
        draft.hidden = false;
        draft.favorite = false;

        this.minecraft.setScreen(new WaypointEditScreen(this, null, draft));
    }

    private void editRow(int rowIndex) {
        WaypointStorage.Waypoint w = getAtRow(rowIndex);
        if (w == null || this.minecraft == null) return;
        this.minecraft.setScreen(new WaypointEditScreen(this, w.name, cloneWp(w)));
    }

    private void deleteRow(int rowIndex) {
        Minecraft mc = Minecraft.getInstance();
        WaypointStorage.Waypoint w = getAtRow(rowIndex);
        if (w == null) return;
        WaypointStorage.remove(mc, w.name);
        refresh();
    }

    private void toggleHiddenRow(int rowIndex) {
        Minecraft mc = Minecraft.getInstance();
        WaypointStorage.Waypoint w = getAtRow(rowIndex);
        if (w == null) return;
        WaypointStorage.toggleHidden(mc, w.name);
        refresh();
    }

    private void toggleFavoriteRow(int rowIndex) {
        Minecraft mc = Minecraft.getInstance();
        WaypointStorage.Waypoint w = getAtRow(rowIndex);
        if (w == null) return;
        WaypointStorage.toggleFavorite(mc, w.name);
        refresh();
    }

    private void sendRowToChat(int rowIndex) {
        Minecraft mc = Minecraft.getInstance();
        WaypointStorage.Waypoint w = getAtRow(rowIndex);
        if (w == null) return;

        String name = (w.name == null || w.name.isBlank()) ? "wp" : w.name;
        name = name.replaceAll("\\s+", "_");

        String msg = "WP|" + name + "|" + w.x + "|" + w.y + "|" + w.z;
        mc.setScreen(new ChatScreen(msg, false));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x88000000);

        int x0 = (this.width - PANEL_W) / 2;
        int y0 = (this.height - PANEL_H) / 2;

        g.fill(x0, y0, x0 + PANEL_W, y0 + PANEL_H, 0xCC000000);
        g.fill(x0, y0, x0 + PANEL_W, y0 + 1, 0xFFFFFFFF);
        g.fill(x0, y0 + PANEL_H - 1, x0 + PANEL_W, y0 + PANEL_H, 0xFFFFFFFF);
        g.fill(x0, y0, x0 + 1, y0 + PANEL_H, 0xFFFFFFFF);
        g.fill(x0 + PANEL_W - 1, y0, x0 + PANEL_W, y0 + PANEL_H, 0xFFFFFFFF);

        g.drawString(this.font, this.title, x0 + PAD, titleY, 0xFFFFFFFF, true);

        // Сначала рисуем все виджеты (кнопки + их ванильный фон)
        super.render(g, mouseX, mouseY, partialTick);

        // Потом поверх — иконки в кнопках hide/send
        for (Row r : rows) {
            if (r.hide.visible) {
                drawCenteredIcon(g, r.hide, r.eyeIcon);
                if (!r.hide.active) overlayDisable(g, r.hide);
            }
            if (r.send.visible) {
                drawCenteredIcon(g, r.send, ICO_CHAT);
                if (!r.send.active) overlayDisable(g, r.send);
            }
        }
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
                ICON_TEX, ICON_TEX
        );
    }

    private static void overlayDisable(GuiGraphics g, Button btn) {
        int cx = btn.getX() + (btn.getWidth() - ICON_DRAW) / 2;
        int cy = btn.getY() + (btn.getHeight() - ICON_DRAW) / 2;
        g.fill(cx, cy, cx + ICON_DRAW, cy + ICON_DRAW, 0x80000000);
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
                src.name, src.world, src.dimension, src.x, src.y, src.z, src.color
        );
        w.kind = src.kind;
        w.createdAt = src.createdAt;
        w.hidden = src.hidden;
        w.favorite = src.favorite;
        return w;
    }

    public void forceRefreshAfterChild() {
        this.page = 0;
        refresh();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        refresh();
    }
}
