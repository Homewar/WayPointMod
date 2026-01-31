package com.example.waypoint;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class WaypointLocatorHud {
    // Vanilla bar width
    private static final int BAR_W = 182;

    // Marker appearance
    private static final int MARKER_TOP_OFFSET = 12;
    private static final int MARKER_W = 3;
    private static final int MARKER_H = 8;

    // Group markers into X-bins (reduces overlap/jitter at long distances)
    private static final int BIN_W = 5; // px per bin (4..6 works well)
    private static final int MAX_MARKERS = 24;

    // Center label
    private static final int CENTER_SNAP_PX = 6;         // considered "centered" if |x-center| <=
    private static final int CENTER_RELEASE_PX = 10;     // hysteresis: release lock when beyond this
    private static final int CENTER_TEXT_Y_OFFSET = 22;

    private static final int HOVER_SHRINK_X = 1; // с каждой стороны
    private static final int HOVER_SHRINK_Y = 1;


    // Smooth movement of markers on bar
    private static final float SMOOTH = 0.35f; // 0..1; higher = snappier, lower = smoother

    // Distance fade (alpha)
    private static final float FADE_START = 250f;
    private static final float FADE_END   = 2000f;

    private static final Identifier HUD_ID = Objects.requireNonNull(
            Identifier.tryParse("waypointmod:waypoint_locator_hud")
    );

    // Per-waypoint smoothed X to reduce micro-jitter
    private static final Map<String, Float> SMOOTH_X = new HashMap<>();

    // Center lock to avoid jumping between nearby candidates
    private static String CENTER_LOCK_KEY = null;

    private WaypointLocatorHud() {}

    public static void init() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.INFO_BAR, HUD_ID, WaypointLocatorHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.options.hideGui) return;
        if (!WaypointStorage.isEnabled()) return;

        List<WaypointStorage.Waypoint> wps = WaypointStorage.listForCurrent(mc);
        if (wps.isEmpty()) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        int barX = (sw - BAR_W) / 2;
        int barY = sh - 32;

        int centerX = barX + BAR_W / 2;
        int markerTop = barY - MARKER_TOP_OFFSET;

        int mouseX = scaledMouseX(mc);
        int mouseY = scaledMouseY(mc);

        // Bin best candidates: binIndex -> best candidate (closest distance)
        int bins = Math.max(1, BAR_W / BIN_W + 2);
        Candidate[] bestByBin = new Candidate[bins];

        // Also track hovered (after rendering we’ll test)
        Candidate hovered = null;

        // Fill bins
        int count = 0;
        for (WaypointStorage.Waypoint w : wps) {
            if (w == null) continue;
            if (count++ >= MAX_MARKERS) break;

            double dx = (w.x + 0.5) - mc.player.getX();
            double dz = (w.z + 0.5) - mc.player.getZ();

            float toYaw = (dx == 0 && dz == 0)
                    ? mc.player.getYRot()
                    : (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;

            float playerYaw = mc.player.getYRot();
            float deltaYaw = Mth.wrapDegrees(toYaw - playerYaw); // [-180..180]

            float t = deltaYaw / 180.0f; // [-1..1]
            float rawX = centerX + (t * (BAR_W / 2f));

            // clamp into bar
            boolean clamped = false;
            float minX = barX + 1;
            float maxX = barX + BAR_W - 2;
            if (rawX < minX) { rawX = minX; clamped = true; }
            if (rawX > maxX) { rawX = maxX; clamped = true; }

            float dist = (float) Math.sqrt(dx * dx + dz * dz);

            // Smooth X per waypoint
            String key = keyOf(mc, w);
            float x = smoothX(key, rawX);

            int bin = Mth.clamp((int) ((x - barX) / BIN_W), 0, bins - 1);
            Candidate c = new Candidate(w, key, x, dist, clamped);

            Candidate prev = bestByBin[bin];
            if (prev == null || c.dist < prev.dist) {
                bestByBin[bin] = c;
            }
        }

        // Choose center candidate with hysteresis
        Candidate centerBest = null;
        int centerBestDx = Integer.MAX_VALUE;

        if (CENTER_LOCK_KEY != null) {
            // try to keep locked candidate if it still exists and is near center
            Candidate locked = findCandidate(bestByBin, CENTER_LOCK_KEY);
            if (locked != null) {
                int dx = Math.abs(Math.round(locked.x) - centerX);
                if (dx <= CENTER_RELEASE_PX) {
                    centerBest = locked;
                    centerBestDx = dx;
                } else {
                    CENTER_LOCK_KEY = null; // release lock
                }
            } else {
                CENTER_LOCK_KEY = null;
            }
        }

        // Render markers + compute hover + compute centerBest if not locked
        for (Candidate c : bestByBin) {
            if (c == null) continue;

            float alpha = fade(c.dist);
            int argb = applyAlpha(0xFF000000 | (c.w.color & 0xFFFFFF), alpha);

            int h = c.clamped ? (MARKER_H - 2) : MARKER_H;
            int y1 = markerTop;
            int y2 = markerTop + h;

            int xi = Math.round(c.x);
            int x1 = xi - (MARKER_W / 2);
            int x2 = x1 + MARKER_W;

            g.fill(x1, y1, x2, y2, argb);

            int hx1 = x1 + HOVER_SHRINK_X;
            int hx2 = x2 - HOVER_SHRINK_X;
            int hy1 = y1 + HOVER_SHRINK_Y;
            int hy2 = y2 - HOVER_SHRINK_Y;

            if (mouseX >= hx1 && mouseX < hx2 && mouseY >= hy1 && mouseY < hy2) {
                hovered = c;
            }

            // center candidate pick (if not locked)
            if (CENTER_LOCK_KEY == null) {
                int dx = Math.abs(xi - centerX);
                if (dx < centerBestDx || (dx == centerBestDx && (centerBest == null || c.dist < centerBest.dist))) {
                    centerBestDx = dx;
                    centerBest = c;
                }
            }
        }

        // If we have a centerBest and it is within snap range -> lock it and draw label
        if (hovered == null && centerBest != null && centerBestDx <= CENTER_SNAP_PX) {
            CENTER_LOCK_KEY = centerBest.key;
            drawCenterLabelSmall(g, mc, centerBest, centerX, barY, sw);
        }

        // Hover tooltip (name + coords)
        if (hovered != null) {
            WaypointStorage.Waypoint w = hovered.w;
            String name = (w.name == null || w.name.isBlank()) ? "(unnamed)" : w.name;
            Component l1 = Component.literal(name);
            Component l2 = Component.literal(w.x + " " + w.y + " " + w.z);
            drawTooltip(g, mc, List.of(l1, l2), mouseX, mouseY);
        }
    }

    // ---- Small center label (scaled) ----
    private static void drawCenterLabelSmall(GuiGraphics g, Minecraft mc, Candidate c, int centerX, int barY, int sw) {
        WaypointStorage.Waypoint w = c.w;

        String name = (w.name == null || w.name.isBlank()) ? "(unnamed)" : w.name;

        // более агрессивное сокращение, чтобы "мелко" выглядело и не прыгало по ширине
        int maxLen = 14;
        if (name.length() > maxLen) name = name.substring(0, maxLen) + "…";

        int distInt = (int) Math.round(c.dist);

        // компактный формат
        String s = name + " · " + distInt + "m";

        // меряем и центрируем
        int textW = mc.font.width(s);
        int tx = centerX - (textW / 2);
        int ty = barY - CENTER_TEXT_Y_OFFSET;

        tx = Mth.clamp(tx, 4, sw - textW - 4);

        // компактный фон (тоньше)
        int padX = 3;
        int padY = 2;

        int bx1 = tx - padX;
        int by1 = ty - padY;
        int bx2 = tx + textW + padX;
        int by2 = ty + 9 + padY; // 9 вместо 10

        g.fill(bx1, by1, bx2, by2, 0x90000000);

        // "визуально меньше": без тени, чуть серее
        g.drawString(mc.font, s, tx, ty, 0xFFE0E0E0, false);
    }


    // ---- Tooltip (manual) ----
    private static void drawTooltip(GuiGraphics g, Minecraft mc, List<Component> lines, int mouseX, int mouseY) {
        if (lines == null || lines.isEmpty()) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        int pad = 4;
        int lineH = 10;

        int maxW = 0;
        for (Component c : lines) maxW = Math.max(maxW, mc.font.width(c));

        int boxW = maxW + pad * 2;
        int boxH = lines.size() * lineH + pad * 2;

        int x = mouseX + 10;
        int y = mouseY + 10;

        if (x + boxW > sw) x = mouseX - 10 - boxW;
        if (y + boxH > sh) y = mouseY - 10 - boxH;

        int bg = 0xF0100010;
        int border = 0xFFFFFFFF;

        g.fill(x, y, x + boxW, y + boxH, bg);
        g.fill(x, y, x + boxW, y + 1, border);
        g.fill(x, y + boxH - 1, x + boxW, y + boxH, border);
        g.fill(x, y, x + 1, y + boxH, border);
        g.fill(x + boxW - 1, y, x + boxW, y + boxH, border);

        int ty = y + pad;
        for (Component c : lines) {
            g.drawString(mc.font, c, x + pad, ty, 0xFFFFFFFF, true);
            ty += lineH;
        }
    }

    // ---- Candidate / helpers ----
    private record Candidate(WaypointStorage.Waypoint w, String key, float x, float dist, boolean clamped) {}

    private static Candidate findCandidate(Candidate[] arr, String key) {
        for (Candidate c : arr) {
            if (c != null && c.key.equals(key)) return c;
        }
        return null;
    }

    private static String keyOf(Minecraft mc, WaypointStorage.Waypoint w) {
        // stable per-world + dimension + name + coords (на случай одинаковых имён)
        String world = WaypointStorage.currentWorldId(mc);
        String dim = WaypointStorage.currentDimId(mc);
        String name = (w.name == null) ? "" : w.name;
        return world + "|" + dim + "|" + name + "|" + w.x + "," + w.y + "," + w.z;
    }

    private static float smoothX(String key, float rawX) {
        Float prev = SMOOTH_X.get(key);
        float out = (prev == null) ? rawX : Mth.lerp(SMOOTH, prev, rawX);
        SMOOTH_X.put(key, out);
        return out;
    }

    private static float fade(float dist) {
        if (dist <= FADE_START) return 1.0f;
        if (dist >= FADE_END) return 0.25f;
        float t = (dist - FADE_START) / (FADE_END - FADE_START);
        return Mth.lerp(t, 1.0f, 0.25f);
    }

    private static int applyAlpha(int argb, float alpha01) {
        int a = (int) (Mth.clamp(alpha01, 0f, 1f) * 255f) & 0xFF;
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int scaledMouseX(Minecraft mc) {
        double raw = mc.mouseHandler.xpos();
        int sw = mc.getWindow().getGuiScaledWidth();
        int rw = mc.getWindow().getScreenWidth();
        return (int) Math.floor(raw * (double) sw / (double) rw);
    }

    private static int scaledMouseY(Minecraft mc) {
        double raw = mc.mouseHandler.ypos();
        int sh = mc.getWindow().getGuiScaledHeight();
        int rh = mc.getWindow().getScreenHeight();
        return (int) Math.floor(raw * (double) sh / (double) rh);
    }
}
