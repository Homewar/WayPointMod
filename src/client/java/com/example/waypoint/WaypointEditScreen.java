package com.example.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class WaypointEditScreen extends Screen {
    private static final int PANEL_W = 248;
    private static final int PANEL_H = 166;

    // Смещение ВНУТРЕННЕГО контента вниз относительно рамки
    private static final int FIELD_SHIFT = 16;

    // Настройки вертикальных расстояний
    private static final int ROW_H = 18;         // высота поля
    private static final int GAP = 10;           // расстояние между блоками (поле -> следующая подпись)
    private static final int LABEL_OFFSET = 10;  // отступ первой подписи от baseY
    private static final int LABEL_TO_FIELD = 10; // расстояние подпись -> поле

    private final Screen parent;
    private final String originalNameOrNull;
    private final WaypointStorage.Waypoint draft;

    private EditBox name;
    private EditBox xField, yField, zField;
    private EditBox colorField;

    // Y координаты подписей (чтобы render совпадал с init)
    private int labelNameY;
    private int labelXyzY;
    private int labelColorY;

    public WaypointEditScreen(Screen parent, String originalNameOrNull, WaypointStorage.Waypoint draft) {
        super(Component.translatable("screen.waypointmod.edit_title"));
        this.parent = parent;
        this.originalNameOrNull = originalNameOrNull;
        this.draft = draft;
    }

    @Override
    protected void init() {
        int x0 = (this.width - PANEL_W) / 2;
        int yFrame = (this.height - PANEL_H) / 2;

        // контент ниже рамки
        int baseY = yFrame + FIELD_SHIFT;

        // вычисляем Y для подписи/поля "Name"
        labelNameY = baseY + LABEL_OFFSET;
        int nameY = labelNameY + LABEL_TO_FIELD;

        // блок XYZ
        labelXyzY = nameY + ROW_H + GAP;
        int xyzY = labelXyzY + LABEL_TO_FIELD;

        // блок Color
        labelColorY = xyzY + ROW_H + GAP;
        int colorY = labelColorY + LABEL_TO_FIELD;

        // кнопки
        int btnY = colorY + ROW_H + GAP;

        name = new EditBox(this.font, x0 + 10, nameY, PANEL_W - 20, ROW_H, Component.empty());
        name.setValue(draft.name == null ? "" : draft.name);
        this.addRenderableWidget(name);

        xField = new EditBox(this.font, x0 + 10, xyzY, 70, ROW_H, Component.empty());
        yField = new EditBox(this.font, x0 + 88, xyzY, 70, ROW_H, Component.empty());
        zField = new EditBox(this.font, x0 + 166, xyzY, 72, ROW_H, Component.empty());
        xField.setValue(Integer.toString(draft.x));
        yField.setValue(Integer.toString(draft.y));
        zField.setValue(Integer.toString(draft.z));
        this.addRenderableWidget(xField);
        this.addRenderableWidget(yField);
        this.addRenderableWidget(zField);

        colorField = new EditBox(this.font, x0 + 10, colorY, PANEL_W - 20, ROW_H, Component.empty());
        colorField.setValue(String.format("#%06X", (draft.color & 0xFFFFFF)));
        this.addRenderableWidget(colorField);

        this.addRenderableWidget(Button.builder(Component.translatable("screen.waypointmod.save"), b -> save())
                .bounds(x0 + 10, btnY, (PANEL_W - 24) / 2, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("screen.waypointmod.cancel"), b -> onClose())
                .bounds(x0 + 14 + (PANEL_W - 24) / 2, btnY, (PANEL_W - 24) / 2, 20)
                .build());

        this.setInitialFocus(name);
    }

    private void save() {
        Minecraft mc = Minecraft.getInstance();

        Integer x = parseInt(xField.getValue());
        Integer y = parseInt(yField.getValue());
        Integer z = parseInt(zField.getValue());
        Integer rgb = parseColor(colorField.getValue());

        if (x == null || y == null || z == null) return;

        String newName = name.getValue().trim();
        if (newName.isEmpty()) newName = (originalNameOrNull != null ? originalNameOrNull : "New");

        // если переименовали — удалим старую
        if (originalNameOrNull != null && !originalNameOrNull.equalsIgnoreCase(newName)) {
            WaypointStorage.remove(mc, originalNameOrNull);
        }

        WaypointStorage.set(mc, newName, x, y, z, rgb);

        // перенос hidden (если в WaypointStorage добавлены методы)
        WaypointStorage.setHidden(mc, newName, draft.hidden);

        onClose();
    }

    private Integer parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return null; }
    }

    private Integer parseColor(String s) {
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() != 6) return null;
        try { return Integer.parseInt(t, 16) & 0xFFFFFF; } catch (Exception e) { return null; }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
            if (parent instanceof WaypointScreen ws) {
                ws.forceRefreshAfterChild();
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // НЕ вызываем renderBackground -> чтобы не ловить "blur once per frame"
        g.fill(0, 0, this.width, this.height, 0x88000000);

        int x0 = (this.width - PANEL_W) / 2;
        int yFrame = (this.height - PANEL_H) / 2;

        // Рамка НЕ смещается
        g.fill(x0, yFrame, x0 + PANEL_W, yFrame + PANEL_H, 0xCC000000);
        g.fill(x0, yFrame, x0 + PANEL_W, yFrame + 1, 0xFFFFFFFF);
        g.fill(x0, yFrame + PANEL_H - 1, x0 + PANEL_W, yFrame + PANEL_H, 0xFFFFFFFF);
        g.fill(x0, yFrame, x0 + 1, yFrame + PANEL_H, 0xFFFFFFFF);
        g.fill(x0 + PANEL_W - 1, yFrame, x0 + PANEL_W, yFrame + PANEL_H, 0xFFFFFFFF);

        // Заголовок в рамке
        g.drawString(this.font, this.title, x0 + 10, yFrame + 6, 0xFFFFFFFF, true);

        // Подписи (смещённый контент)
        g.drawString(this.font, Component.translatable("screen.waypointmod.name"), x0 + 10, labelNameY, 0xFFBBBBBB, true);
        g.drawString(this.font, Component.translatable("screen.waypointmod.xyz"), x0 + 10, labelXyzY, 0xFFBBBBBB, true);
        g.drawString(this.font, Component.translatable("screen.waypointmod.color"), x0 + 10, labelColorY, 0xFFBBBBBB, true);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
