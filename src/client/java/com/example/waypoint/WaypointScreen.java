package com.example.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class WaypointScreen extends Screen {
    private static final int PANEL_W = 248;
    private static final int PANEL_H = 222;

    private EditBox search;

    private Button prevBtn;
    private Button nextBtn;

    private Button addBtn;
    private Button editBtn;
    private Button delBtn;
    private Button hideBtn;
    //private Button copyBtn;
    private Button sendBtn;
    private Button toggleModBtn;

    private final List<Button> rowButtons = new ArrayList<>();

    private List<WaypointStorage.Waypoint> filtered = List.of();
    private int page = 0;
    private int selectedIndex = -1; // индекс в filtered

    private int pageSize = 6; // вычисляется в init()
    private int titleY = 0;

    public WaypointScreen() {
        super(Component.translatable("screen.waypointmod.title"));
    }

    @Override
    protected void init() {
        rowButtons.clear();

        int x0 = (this.width - PANEL_W) / 2;
        int y0 = (this.height - PANEL_H) / 2;

        // Раскладка внутри рамки
        titleY = y0 + 6;
        int searchY = y0 + 18;

        int listTop = y0 + 42;

        int toggleY = y0 + 152; // Enabled под списком
        int navY    = y0 + 176; // Prev/Next/Add/Edit
        int actY    = y0 + 198; // Delete/Hide/Copy

        search = new EditBox(this.font, x0 + 10, searchY, PANEL_W - 20, 18,
                Component.translatable("screen.waypointmod.search"));
        search.setResponder(s -> refresh());
        this.addRenderableWidget(search);

        // Динамический размер страницы (сколько строк помещается до toggleY)
        int rowH = 18;
        int listBottom = toggleY - 4;
        pageSize = Math.max(1, (listBottom - listTop) / rowH);

        for (int i = 0; i < pageSize; i++) {
            int yy = listTop + i * rowH;
            final int rowIndex = i;

            Button row = Button.builder(Component.literal(""), b -> onRowClick(rowIndex))
                    .bounds(x0 + 10, yy, PANEL_W - 20, rowH)
                    .build();

            rowButtons.add(row);
            this.addRenderableWidget(row);
        }

        toggleModBtn = Button.builder(Component.translatable("screen.waypointmod.toggle"), b -> {
                    WaypointStorage.toggle();
                    refreshButtons();
                })
                .bounds(x0 + 10, toggleY, PANEL_W - 20, 20)
                .build();
        this.addRenderableWidget(toggleModBtn);

        prevBtn = Button.builder(Component.translatable("screen.waypointmod.prev"), b -> {
            page = Math.max(0, page - 1);
            selectedIndex = -1;
            rebuildRows();
        }).bounds(x0 + 10, navY, 52, 20).build();
        this.addRenderableWidget(prevBtn);

        nextBtn = Button.builder(Component.translatable("screen.waypointmod.next"), b -> {
            int maxPage = Math.max(0, (filtered.size() - 1) / pageSize);
            page = Math.min(maxPage, page + 1);
            selectedIndex = -1;
            rebuildRows();
        }).bounds(x0 + 66, navY, 52, 20).build();
        this.addRenderableWidget(nextBtn);

        addBtn = Button.builder(Component.translatable("screen.waypointmod.add"), b -> addHere())
                .bounds(x0 + 122, navY, 52, 20).build();
        this.addRenderableWidget(addBtn);

        editBtn = Button.builder(Component.translatable("screen.waypointmod.edit"), b -> editSelected())
                .bounds(x0 + 178, navY, 60, 20).build();
        this.addRenderableWidget(editBtn);

        delBtn = Button.builder(Component.translatable("screen.waypointmod.delete"), b -> deleteSelected())
                .bounds(x0 + 10, actY, 72, 20).build();
        this.addRenderableWidget(delBtn);

        hideBtn = Button.builder(Component.translatable("screen.waypointmod.hide"), b -> toggleHiddenSelected())
                .bounds(x0 + 86, actY, 72, 20).build();
        this.addRenderableWidget(hideBtn);

        sendBtn = Button.builder(Component.translatable("screen.waypointmod.send"), b -> sendSelectedToChat())
            .bounds(x0 + 162, actY, 76, 20).build();
        this.addRenderableWidget(sendBtn);

        refresh();
        this.setInitialFocus(search);
    }

    private void refresh() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            filtered = List.of();
            page = 0;
            selectedIndex = -1;
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

        tmp.sort(Comparator.comparingDouble(w -> distSq(mc, w)));
        filtered = tmp;

        int maxPage = Math.max(0, (filtered.size() - 1) / pageSize);
        page = Mth.clamp(page, 0, maxPage);

        selectedIndex = -1;
        rebuildRows();
        refreshButtons();
    }

    private void rebuildRows() {
        int start = page * pageSize;

        for (int i = 0; i < rowButtons.size(); i++) {
            Button row = rowButtons.get(i);
            int idx = start + i;

            if (idx >= 0 && idx < filtered.size()) {
                WaypointStorage.Waypoint w = filtered.get(idx);
                String name = (w.name == null ? "(unnamed)" : w.name);

                int dist = (int) Math.round(Math.sqrt(distSq(Minecraft.getInstance(), w)));
                String flags = (w.hidden ? " [H]" : "");
                if ("death".equalsIgnoreCase(w.kind)) flags += " [D]";

                row.setMessage(Component.literal(name + flags + "  " + w.x + " " + w.y + " " + w.z + "  (" + dist + "m)"));
                row.visible = true;
                row.active = true;
            } else {
                row.setMessage(Component.literal(""));
                row.visible = true;
                row.active = false;
            }
        }

        int maxPage = Math.max(0, (filtered.size() - 1) / pageSize);
        prevBtn.active = page > 0;
        nextBtn.active = page < maxPage;
    }

    private void onRowClick(int rowIndex) {
        int idx = page * pageSize + rowIndex;
        if (idx < 0 || idx >= filtered.size()) return;
        selectedIndex = idx;
        refreshButtons();
    }

    private WaypointStorage.Waypoint selected() {
        if (selectedIndex < 0 || selectedIndex >= filtered.size()) return null;
        return filtered.get(selectedIndex);
    }

    private void refreshButtons() {
        WaypointStorage.Waypoint sel = selected();
        boolean has = sel != null;

        editBtn.active = has;
        delBtn.active = has;
        hideBtn.active = has;
        sendBtn.active = has;

        toggleModBtn.setMessage(WaypointStorage.isEnabled()
                ? Component.translatable("screen.waypointmod.on")
                : Component.translatable("screen.waypointmod.off"));

        if (has) {
            hideBtn.setMessage(sel.hidden
                    ? Component.translatable("screen.waypointmod.show")
                    : Component.translatable("screen.waypointmod.hide"));
        } else {
            hideBtn.setMessage(Component.translatable("screen.waypointmod.hide"));
        }
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

        this.minecraft.setScreen(new WaypointEditScreen(this, null, draft));
    }

    private void editSelected() {
        WaypointStorage.Waypoint sel = selected();
        if (sel == null) return;
        this.minecraft.setScreen(new WaypointEditScreen(this, sel.name, cloneWp(sel)));
    }

    private void deleteSelected() {
        Minecraft mc = Minecraft.getInstance();
        WaypointStorage.Waypoint sel = selected();
        if (sel == null) return;
        WaypointStorage.remove(mc, sel.name);
        refresh();
    }

    private void toggleHiddenSelected() {
        Minecraft mc = Minecraft.getInstance();
        WaypointStorage.Waypoint sel = selected();
        if (sel == null) return;
        WaypointStorage.toggleHidden(mc, sel.name);
        refresh();
    }

    private void sendSelectedToChat() {
        Minecraft mc = Minecraft.getInstance();
        WaypointStorage.Waypoint sel = selected();
        if (sel == null || mc.player == null) return;
        String name = (sel.name == null ? "wp" : sel.name);

        String msg = name + ":" + sel.x + " " + sel.y + " " + sel.z;

        mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen(msg, false));
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

        g.drawString(this.font, this.title, x0 + 10, titleY, 0xFFFFFFFF, true);

        super.render(g, mouseX, mouseY, partialTick);
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
        return w;
    }

    public void forceRefreshAfterChild() {
        this.page = 0;
        this.selectedIndex = -1;
        refresh();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        refresh();
    }
}
