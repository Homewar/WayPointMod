package com.example.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Random;

public class WaypointEditScreen extends Screen {
    // layout (vanilla-ish, centered column)
    private static final int LIST_W = 310;

    // HSV picker base sizes
    private static final int SV_W = 128;
    private static final int SV_H = 72;
    private static final int HUE_W = 10;

    // minimal SV height when screen is short
    private static final int SV_MIN_H = 48;

    // performance vs quality
    private static final int DRAW_STEP = 2;

    private final Screen parent;
    private final String originalNameOrNull;
    private final WaypointStorage.Waypoint draft;

    private EditBox name;
    private EditBox xField, yField, zField;

    private Button saveBtn;
    private Button cancelBtn;
    private Button randomBtn;
    private Button resetBtn;

    private boolean useAutoColor;

    // HSV state
    private float hue; // 0..1
    private float sat; // 0..1
    private float val; // 0..1
    private int selectedColor; // 0xRRGGBB

    // drag state
    private boolean draggingSV = false;
    private boolean draggingHue = false;

    // current picker rect (computed in render; used for input)
    private int curSvX, curSvY, curHueX, curHueY, curSvH;

    public WaypointEditScreen(Screen parent, String originalNameOrNull, WaypointStorage.Waypoint draft) {
        super(Component.translatable("screen.waypointmod.edit_title"));
        this.parent = parent;
        this.originalNameOrNull = originalNameOrNull;
        this.draft = draft;

        this.selectedColor = (draft.color & 0xFFFFFF);
        float[] hsv = rgbToHsv(this.selectedColor);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];

        this.useAutoColor = false;
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int cx = this.width / 2;
        int left = cx - (LIST_W / 2);

        int y = 32;

        // Name
        name = new EditBox(this.font, left, y, LIST_W, 20, Component.empty());
        name.setValue(draft.name == null ? "" : draft.name);
        this.addRenderableWidget(name);
        y += 28;

        // XYZ row
        int w3 = (LIST_W - 8) / 3;
        int gap = 6; // было 4
        xField = new EditBox(this.font, left, y, w3, 20, Component.empty());
        yField = new EditBox(this.font, left + w3 + gap, y, w3, 20, Component.empty());
        zField = new EditBox(this.font, left + (w3 + gap) * 2, y, LIST_W - (w3 + gap) * 2, 20, Component.empty());

        xField.setValue(Integer.toString(draft.x));
        yField.setValue(Integer.toString(draft.y));
        zField.setValue(Integer.toString(draft.z));

        this.addRenderableWidget(xField);
        this.addRenderableWidget(yField);
        this.addRenderableWidget(zField);
        y += 36;

        // Random / Reset row (above picker area)
        randomBtn = Button.builder(Component.translatable("screen.waypointmod.color.random"), b -> {
                    useAutoColor = false;
                    selectedColor = new Random().nextInt(0x1000000) & 0xFFFFFF;
                    float[] hsv = rgbToHsv(selectedColor);
                    hue = hsv[0];
                    sat = hsv[1];
                    val = hsv[2];
                })
                .bounds(left, y, 100, 20)
                .build();
        this.addRenderableWidget(randomBtn);

        resetBtn = Button.builder(Component.translatable("screen.waypointmod.color.reset"), b -> {
                    useAutoColor = false;
                    selectedColor = (draft.color & 0xFFFFFF);
                    float[] hsv = rgbToHsv(selectedColor);
                    hue = hsv[0];
                    sat = hsv[1];
                    val = hsv[2];
                })
                .bounds(left + 104, y, 100, 20)
                .build();
        this.addRenderableWidget(resetBtn);

        // bottom buttons like vanilla
        int btnY = this.height - 28;
        int half = (LIST_W - 4) / 2;

        saveBtn = Button.builder(Component.translatable("screen.waypointmod.save"), b -> save())
                .bounds(left, btnY, half, 20)
                .build();
        this.addRenderableWidget(saveBtn);

        cancelBtn = Button.builder(Component.translatable("screen.waypointmod.cancel"), b -> onClose())
                .bounds(left + half + 4, btnY, half, 20)
                .build();
        this.addRenderableWidget(cancelBtn);

        this.setInitialFocus(name);
    }

    private void save() {
        Minecraft mc = Minecraft.getInstance();

        Integer x = parseInt(xField.getValue());
        Integer y = parseInt(yField.getValue());
        Integer z = parseInt(zField.getValue());
        if (x == null || y == null || z == null) return;

        String newName = name.getValue().trim();
        if (newName.isEmpty()) newName = (originalNameOrNull != null ? originalNameOrNull : "New");

        if (originalNameOrNull != null && !originalNameOrNull.equalsIgnoreCase(newName)) {
            WaypointStorage.remove(mc, originalNameOrNull);
        }

        Integer color = useAutoColor ? null : (selectedColor & 0xFFFFFF);
        WaypointStorage.set(mc, newName, x, y, z, color);

        WaypointStorage.setHidden(mc, newName, draft.hidden);
        WaypointStorage.setFavorite(mc, newName, draft.favorite);

        onClose();
    }

    private Integer parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // translucent overlay (as you want)
        g.fill(0, 0, this.width, this.height, 0x88000000);

        // title centered
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);

        int cx = this.width / 2;
        int left = cx - (LIST_W / 2);

        // labels
        g.drawString(this.font, Component.translatable("screen.waypointmod.name"), left, 24, 0xFFAAAAAA, false);
        g.drawString(this.font, Component.translatable("screen.waypointmod.xyz"), left, 52, 0xFFAAAAAA, false);
        g.drawString(this.font, Component.translatable("screen.waypointmod.color"), left, 84, 0xFFAAAAAA, false);

        // picker layout (must not overlap bottom buttons)
        int svX = left;
        int baseSvY = 96;

        if (resetBtn != null) {
            baseSvY = resetBtn.getY() + resetBtn.getHeight() + 10; // 10px отступ после кнопок
        } else if (randomBtn != null) {
            baseSvY = randomBtn.getY() + randomBtn.getHeight() + 10;
        }

        int buttonsTop = this.height - 28; // save/cancel row top
        int maxBottom = buttonsTop - 10;   // 10px gap над save/cancel

        int availH = maxBottom - baseSvY;
        int svH = Math.min(SV_H, availH);
        svH = Mth.clamp(svH, SV_MIN_H, SV_H);

        int svY = baseSvY;
        if (availH < SV_MIN_H) {
            svH = SV_MIN_H;
            svY = Math.max(baseSvY, maxBottom - svH);
        }

        int hueX = svX + SV_W + 6;
        int hueY = svY;

        int previewX = hueX + HUE_W + 10;
        int previewY = svY;

        // store for input
        curSvX = svX;
        curSvY = svY;
        curHueX = hueX;
        curHueY = hueY;
        curSvH = svH;

        // draw picker
        drawSVSquare(g, svX, svY, svH);
        drawHueBar(g, hueX, hueY, svH);

        drawSVMarker(g, svX, svY, svH);
        drawHueMarker(g, hueX, hueY, svH);

        int preview = useAutoColor ? (draft.color & 0xFFFFFF) : (selectedColor & 0xFFFFFF);
        drawPreview(g, previewX, previewY, preview);

        String hex = String.format("#%06X", preview & 0xFFFFFF);
        g.drawString(this.font, Component.literal(hex), previewX, previewY + 22, 0xFFFFFFFF, false);

        // widgets
        super.render(g, mouseX, mouseY, partialTick);
    }

    // ----------- input signatures (1.21.11) -----------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        if (button == 0) {
            if (inside(mouseX, mouseY, curSvX, curSvY, SV_W, curSvH)) {
                draggingSV = true;
                useAutoColor = false;
                updateSV(mouseX, mouseY);
                return true;
            }
            if (inside(mouseX, mouseY, curHueX, curHueY, HUE_W, curSvH)) {
                draggingHue = true;
                useAutoColor = false;
                updateHue(mouseX, mouseY);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        if (button == 0) {
            if (draggingSV) {
                updateSV(mouseX, mouseY);
                return true;
            }
            if (draggingHue) {
                updateHue(mouseX, mouseY);
                return true;
            }
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            draggingSV = false;
            draggingHue = false;
        }
        return super.mouseReleased(event);
    }

    // ----------- picker drawing -----------

    private void drawSVSquare(GuiGraphics g, int x, int y, int svH) {
        for (int yy = 0; yy < svH; yy += DRAW_STEP) {
            float v = 1f - (yy / (float) (svH - 1));
            for (int xx = 0; xx < SV_W; xx += DRAW_STEP) {
                float s = (xx / (float) (SV_W - 1));
                int rgb = hsvToRgb(hue, s, v);
                g.fill(x + xx, y + yy,
                        x + Math.min(xx + DRAW_STEP, SV_W),
                        y + Math.min(yy + DRAW_STEP, svH),
                        0xFF000000 | rgb);
            }
        }

        // border
        g.fill(x - 1, y - 1, x + SV_W + 1, y, 0xFF000000);
        g.fill(x - 1, y + svH, x + SV_W + 1, y + svH + 1, 0xFF000000);
        g.fill(x - 1, y - 1, x, y + svH + 1, 0xFF000000);
        g.fill(x + SV_W, y - 1, x + SV_W + 1, y + svH + 1, 0xFF000000);
    }

    private void drawHueBar(GuiGraphics g, int x, int y, int svH) {
        for (int yy = 0; yy < svH; yy += DRAW_STEP) {
            float h = 1f - (yy / (float) (svH - 1));
            int rgb = hsvToRgb(h, 1f, 1f);
            g.fill(x, y + yy, x + HUE_W, y + Math.min(yy + DRAW_STEP, svH), 0xFF000000 | rgb);
        }

        // border
        g.fill(x - 1, y - 1, x + HUE_W + 1, y, 0xFF000000);
        g.fill(x - 1, y + svH, x + HUE_W + 1, y + svH + 1, 0xFF000000);
        g.fill(x - 1, y - 1, x, y + svH + 1, 0xFF000000);
        g.fill(x + HUE_W, y - 1, x + HUE_W + 1, y + svH + 1, 0xFF000000);
    }

    private void drawSVMarker(GuiGraphics g, int svX, int svY, int svH) {
        int mx = svX + Math.round(sat * (SV_W - 1));
        int my = svY + Math.round((1f - val) * (svH - 1));

        int c = 0xFFFFFFFF;
        g.fill(mx - 4, my, mx + 5, my + 1, c);
        g.fill(mx, my - 4, mx + 1, my + 5, c);

        int o = 0xFF000000;
        g.fill(mx - 4, my - 1, mx + 5, my, o);
        g.fill(mx - 4, my + 1, mx + 5, my + 2, o);
        g.fill(mx - 1, my - 4, mx, my + 5, o);
        g.fill(mx + 1, my - 4, mx + 2, my + 5, o);
    }

    private void drawHueMarker(GuiGraphics g, int hueX, int hueY, int svH) {
        int my = hueY + Math.round((1f - hue) * (svH - 1));
        int c = 0xFFFFFFFF;

        g.fill(hueX - 2, my - 1, hueX + HUE_W + 2, my, c);
        g.fill(hueX - 2, my, hueX + HUE_W + 2, my + 1, c);
        g.fill(hueX - 2, my + 1, hueX + HUE_W + 2, my + 2, c);

        int o = 0xFF000000;
        g.fill(hueX - 2, my - 2, hueX + HUE_W + 2, my - 1, o);
        g.fill(hueX - 2, my + 2, hueX + HUE_W + 2, my + 3, o);
    }

    private void drawPreview(GuiGraphics g, int x, int y, int rgb) {
        int argb = 0xFF000000 | (rgb & 0xFFFFFF);
        g.fill(x, y, x + 18, y + 18, argb);

        g.fill(x - 1, y - 1, x + 19, y, 0xFF000000);
        g.fill(x - 1, y + 18, x + 19, y + 19, 0xFF000000);
        g.fill(x - 1, y - 1, x, y + 19, 0xFF000000);
        g.fill(x + 18, y - 1, x + 19, y + 19, 0xFF000000);
    }

    // ----------- picker updates (use current rect) -----------

    private void updateSV(double mouseX, double mouseY) {
        float sx = (float) (mouseX - curSvX);
        float sy = (float) (mouseY - curSvY);

        sat = Mth.clamp(sx / (SV_W - 1f), 0f, 1f);
        val = Mth.clamp(1f - (sy / (curSvH - 1f)), 0f, 1f);

        selectedColor = hsvToRgb(hue, sat, val);
    }

    private void updateHue(double mouseX, double mouseY) {
        float sy = (float) (mouseY - curHueY);
        hue = Mth.clamp(1f - (sy / (curSvH - 1f)), 0f, 1f);

        selectedColor = hsvToRgb(hue, sat, val);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ----------- color conversions -----------

    private static int hsvToRgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        s = Mth.clamp(s, 0f, 1f);
        v = Mth.clamp(v, 0f, 1f);

        float r, g, b;

        if (s <= 0.00001f) {
            r = g = b = v;
        } else {
            float hh = h * 6f;
            int i = (int) Math.floor(hh);
            float f = hh - i;
            float p = v * (1f - s);
            float q = v * (1f - s * f);
            float t = v * (1f - s * (1f - f));

            switch (i % 6) {
                case 0 -> { r = v; g = t; b = p; }
                case 1 -> { r = q; g = v; b = p; }
                case 2 -> { r = p; g = v; b = t; }
                case 3 -> { r = p; g = q; b = v; }
                case 4 -> { r = t; g = p; b = v; }
                default -> { r = v; g = p; b = q; }
            }
        }

        int ri = Mth.clamp((int) (r * 255f + 0.5f), 0, 255);
        int gi = Mth.clamp((int) (g * 255f + 0.5f), 0, 255);
        int bi = Mth.clamp((int) (b * 255f + 0.5f), 0, 255);
        return (ri << 16) | (gi << 8) | bi;
    }

    private static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f;
        float g = ((rgb >> 8) & 255) / 255f;
        float b = (rgb & 255) / 255f;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;

        float h;
        if (d <= 0.00001f) {
            h = 0f;
        } else if (max == r) {
            h = ((g - b) / d) % 6f;
        } else if (max == g) {
            h = ((b - r) / d) + 2f;
        } else {
            h = ((r - g) / d) + 4f;
        }
        h /= 6f;
        if (h < 0f) h += 1f;

        float s = (max <= 0.00001f) ? 0f : (d / max);
        float v = max;

        return new float[]{h, s, v};
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}