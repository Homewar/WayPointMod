package com.example.waypoint;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class WaypointLocatorHud {

    // Vanilla bar width
    private static final int BAR_W = 182;

    // Place compass at top
    private static final int BAR_TOP_Y = 7;
    private static final int BAR_H = 18;

    // Show waypoints only in 120° like vanilla player indicators
    private static final float VIEW_DEG = 120.0f;
    private static final float HALF_VIEW_DEG = VIEW_DEG / 2.0f; // 60

    // Angular fade near edges of 120° window
    private static final float ANGLE_FADE_START = 52.0f;
    private static final float ANGLE_FADE_END = 60.0f;

    // Compass scale window across the bar (for orientation)
    private static final float COMPASS_VIEW_DEG = 180.0f;
    private static final float COMPASS_HALF_VIEW = COMPASS_VIEW_DEG / 2.0f;

    // Compass ticks/labels
    private static final int COMPASS_MINOR_EVERY = 5;
    private static final int COMPASS_MAJOR_EVERY = 15;
    private static final int COMPASS_LABEL_EVERY = 45;

    private static final int COMPASS_TICK_Y = 2;
    private static final int COMPASS_MINOR_H = 4;
    private static final int COMPASS_MAJOR_H = 7;

    private static final int COMPASS_LABEL_Y = -5;

    // Marker appearance (waypoints)
    private static final int MARKER_W = 3;
    private static final int MARKER_H = 8;

    // Marker position inside bar
    private static final int MARKER_INSET_BOTTOM = 4;

    // Group markers into X-bins
    private static final int BIN_W = 7;
    private static final int MAX_MARKERS = 64;

    // Center label
    private static final int CENTER_SNAP_PX = 6;
    private static final int CENTER_RELEASE_PX = 10;
    private static final int CENTER_LABEL_Y_GAP = 4;
    private static final int CENTER_NAME_MAX = 14;

    // Hover hitbox shrink
    private static final int HOVER_SHRINK_X = 1;
    private static final int HOVER_SHRINK_Y = 1;

    // Smooth movement of markers on bar
    private static final float SMOOTH = 0.35f;

    // Hysteresis for bin clustering
    private static final int BIN_HYST_PX = 3;
    private static final Map<String, Integer> STICKY_BIN = new HashMap<>();

    // Center label list limits
    private static final int CENTER_LIST_MAX_LINES = 5;
    private static final int CENTER_LIST_LINE_H = 10;

    // Scale of text
    private static final float CENTER_LABEL_SCALE = 0.75f; // 75% размера
    private static final float COMPASS_LABEL_SCALE = 0.7f; // 70% размера

    // + добавь константу (рядом с CENTER_*):
    private static final int CENTER_COLLAPSED_LINES = 1; // сколько строк показывать без Shift

    private static final Identifier HUD_ID = Objects.requireNonNull(
            Identifier.tryParse("waypointmod:waypoint_locator_hud"));

    private static final Map<String, Float> SMOOTH_X = new HashMap<>();
    private static String CENTER_LOCK_KEY = null;

    private WaypointLocatorHud() {
    }

    public static void init() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.INFO_BAR, HUD_ID, WaypointLocatorHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        if (!WaypointStorage.isHudEnabled())
            return;
        if (!WaypointStorage.isLocatorHudEnabled())
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
            return;
        if (mc.options.hideGui)
            return;
        if (!WaypointStorage.isEnabled())
            return;

        List<WaypointStorage.Waypoint> wps = WaypointStorage.listForCurrent(mc);

        int sw = mc.getWindow().getGuiScaledWidth();

        int barX = (sw - BAR_W) / 2;
        int barY = BAR_TOP_Y;

        int centerX = barX + BAR_W / 2;

        // compass scale (no bg)
        if (WaypointStorage.isLocatorUseFlatBar()) {
            // простая горизонтальная линия по центру бара
            // int barBottom = barY + BAR_H;
            int yLine = barY + 6;
            g.fill(barX + 1, yLine, barX + BAR_W - 1, yLine + 1, 0xFFFFFFFF);
        } else if (WaypointStorage.isLocatorShowTicks() || WaypointStorage.isLocatorShowDirections()) {
            drawCompassScale(g, mc, barX, barY, centerX);
        }

        // marker vertical placement within bar
        int barBottom = barY + BAR_H;
        int markerY2 = barBottom - MARKER_INSET_BOTTOM;

        int mouseX = scaledMouseX(mc);
        int mouseY = scaledMouseY(mc);

        int bins = Math.max(1, BAR_W / BIN_W + 2);
        Cluster[] clusters = new Cluster[bins];

        // Fill clusters
        int count = 0;
        for (WaypointStorage.Waypoint w : wps) {
            if (w == null)
                continue;
            if (w.hidden)
                continue;
            if (count++ >= MAX_MARKERS)
                break;

            double dx = (w.x + 0.5) - mc.player.getX();
            double dz = (w.z + 0.5) - mc.player.getZ();

            float toYaw = (dx == 0 && dz == 0)
                    ? mc.player.getYRot()
                    : (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;

            float playerYaw = mc.player.getYRot();
            float deltaYaw = Mth.wrapDegrees(toYaw - playerYaw); // [-180..180]

            // clamp to waypoint view window (±60)
            boolean clamped = false;
            float visYaw = deltaYaw;
            if (visYaw < -HALF_VIEW_DEG) {
                visYaw = -HALF_VIEW_DEG;
                clamped = true;
            }
            if (visYaw > HALF_VIEW_DEG) {
                visYaw = HALF_VIEW_DEG;
                clamped = true;
            }

            float t = visYaw / HALF_VIEW_DEG; // [-1..1]
            float rawX = centerX + (t * (BAR_W / 2f));

            // clamp to bar
            float minX = barX + 1;
            float maxX = barX + BAR_W - 2;
            if (rawX < minX)
                rawX = minX;
            if (rawX > maxX)
                rawX = maxX;

            float dist = (float) Math.sqrt(dx * dx + dz * dz);

            String wpKey = keyOf(mc, w);

            // сглаживание только для отрисовки
            float xSmooth = smoothX(wpKey, rawX);

            // целевой бин по rawX
            int targetBin = Mth.clamp((int) ((rawX - barX) / BIN_W), 0, bins - 1);

            // липкий бин с гистерезисом
            int bin = targetBin;
            Integer prev = STICKY_BIN.get(wpKey);
            if (prev != null && prev >= 0 && prev < bins) {
                float prevMin = (barX + prev * BIN_W) - BIN_HYST_PX;
                float prevMax = (barX + (prev + 1) * BIN_W) + BIN_HYST_PX;
                if (rawX >= prevMin && rawX < prevMax) {
                    bin = prev; // держим старый бин
                } else {
                    STICKY_BIN.put(wpKey, targetBin); // переключаемся только когда реально вышли
                }
            } else {
                STICKY_BIN.put(wpKey, targetBin);
            }

            if (clusters[bin] == null)
                clusters[bin] = new Cluster(bin, barX);
            clusters[bin].add(new Entry(w, wpKey, xSmooth, dist, clamped, Math.abs(visYaw)));
        }

        Cluster hovered = null;

        // center lock
        Cluster centerBest = null;
        int centerBestDx = Integer.MAX_VALUE;

        if (CENTER_LOCK_KEY != null) {
            Cluster locked = findClusterByKey(clusters, CENTER_LOCK_KEY);
            if (locked != null) {
                int dx = Math.abs(locked.drawX - centerX);
                if (dx <= CENTER_RELEASE_PX) {
                    centerBest = locked;
                    centerBestDx = dx;
                } else {
                    CENTER_LOCK_KEY = null;
                }
            } else {
                CENTER_LOCK_KEY = null;
            }
        }

        // render clusters
        if (WaypointStorage.isLocatorShowMarkers()) {
            for (Cluster c : clusters) {
                if (c == null)
                    continue;

                c.finalizeCluster();

                float alpha = 1.0f; // fadeByDistance(c.nearestDist);
                alpha *= fadeByAngleAbs(c.nearestAngleAbs);

                int rgb = c.clusterColorRgb();
                int argb = applyAlpha(0xFF000000 | rgb, alpha);

                int h = c.anyClamped ? (MARKER_H - 2) : MARKER_H;
                int y2 = markerY2;
                int y1 = y2 - h;

                int x1 = c.drawX - (MARKER_W / 2);
                int x2 = x1 + MARKER_W;

                g.fill(x1, y1, x2, y2, argb);

                int hx1 = x1 + HOVER_SHRINK_X;
                int hx2 = x2 - HOVER_SHRINK_X;
                int hy1 = y1 + HOVER_SHRINK_Y;
                int hy2 = y2 - HOVER_SHRINK_Y;

                if (mouseX >= hx1 && mouseX < hx2 && mouseY >= hy1 && mouseY < hy2) {
                    hovered = c;
                }

                if (CENTER_LOCK_KEY == null) {
                    int dx = Math.abs(c.drawX - centerX);
                    if (dx < centerBestDx || (dx == centerBestDx
                            && c.nearestDist < (centerBest == null ? Float.MAX_VALUE : centerBest.nearestDist))) {
                        centerBestDx = dx;
                        centerBest = c;
                    }
                }
            }
        } else {
            // если маркеры выключены — можно сбросить lock, чтобы не залипала подпись
            CENTER_LOCK_KEY = null;
        }

        if (hovered == null && centerBest != null && centerBestDx <= CENTER_SNAP_PX) {
            CENTER_LOCK_KEY = centerBest.key;
            drawCenterLabel(g, mc, centerBest, centerX, barY, sw);
        }
    }

    // ----------------- Cluster + Entry -----------------

    private static final class Entry {
        final WaypointStorage.Waypoint w;
        final String wpKey;
        final float x;
        final float dist;
        final boolean clamped;
        final float angleAbs;

        Entry(WaypointStorage.Waypoint w, String wpKey, float x, float dist, boolean clamped, float angleAbs) {
            this.w = w;
            this.wpKey = wpKey;
            this.x = x;
            this.dist = dist;
            this.clamped = clamped;
            this.angleAbs = angleAbs;
        }
    }

    private static final class Cluster {
        @SuppressWarnings("all")
        final int bin;
        final String key;

        final List<Entry> entries = new ArrayList<>();

        int drawX;
        float nearestDist = Float.MAX_VALUE;
        float nearestAngleAbs = 999f;
        boolean anyClamped = false;

        Cluster(int bin, int barX) {
            this.bin = bin;
            this.key = "bin:" + bin;
            this.drawX = barX + bin * BIN_W + BIN_W / 2;
        }

        void add(Entry e) {
            entries.add(e);
        }

        void finalizeCluster() {
            if (entries.isEmpty())
                return;

            Entry nearest = entries.get(0);
            for (Entry e : entries) {
                if (e.dist < nearest.dist)
                    nearest = e;
            }
            this.drawX = Math.round(nearest.x);

            for (Entry e : entries) {
                if (e.dist < nearestDist)
                    nearestDist = e.dist;
                if (e.angleAbs < nearestAngleAbs)
                    nearestAngleAbs = e.angleAbs;
                if (e.clamped)
                    anyClamped = true;
            }

            entries.sort((a, b) -> {
                boolean af = a.w.favorite;
                boolean bf = b.w.favorite;
                if (af != bf)
                    return af ? -1 : 1; // избранные выше
                return Float.compare(a.dist, b.dist); // затем по дистанции
            });
        }

        int clusterColorRgb() {
            if (entries.size() == 1)
                return entries.get(0).w.color & 0xFFFFFF;

            int take = Math.min(4, entries.size());
            long rr = 0, gg = 0, bb = 0;
            for (int i = 0; i < take; i++) {
                int c = entries.get(i).w.color & 0xFFFFFF;
                rr += (c >> 16) & 255;
                gg += (c >> 8) & 255;
                bb += c & 255;
            }
            int r = (int) (rr / take);
            int g = (int) (gg / take);
            int b = (int) (bb / take);
            return (r << 16) | (g << 8) | b;
        }
    }

    private static Cluster findClusterByKey(Cluster[] arr, String key) {
        for (Cluster c : arr) {
            if (c != null && c.key.equals(key))
                return c;
        }
        return null;
    }

    // ----------------- Compass -----------------

    private static void drawCompassScale(GuiGraphics g, Minecraft mc, int barX, int barY, int centerX) {
        float playerYaw = mc.player.getYRot();

        boolean showTicks = WaypointStorage.isLocatorShowTicks();
        boolean showDirs = WaypointStorage.isLocatorShowDirections();

        for (int deg = -180; deg <= 180; deg += COMPASS_MINOR_EVERY) {
            float delta = Mth.wrapDegrees(deg - playerYaw);

            if (delta < -COMPASS_HALF_VIEW || delta > COMPASS_HALF_VIEW)
                continue;

            float t = delta / COMPASS_HALF_VIEW;
            int x = Math.round(centerX + t * (BAR_W / 2f));
            if (x < barX + 1 || x > barX + BAR_W - 2)
                continue;

            // 1) риски (только если включены)
            if (showTicks) {
                boolean major = (deg % COMPASS_MAJOR_EVERY == 0);
                int h = major ? COMPASS_MAJOR_H : COMPASS_MINOR_H;

                int y1 = barY + COMPASS_TICK_Y;
                int y2 = y1 + h;

                g.fill(x, y1, x + 1, y2, 0xFFFFFFFF);
            }

            // 2) подписи сторон света (независимо от рисок)
            if (showDirs && (deg % COMPASS_LABEL_EVERY == 0)) {
                String label = compassLabel(wrap360(deg));
                int tw = mc.font.width(label);

                int tx = x - (tw / 2) + 1;
                int ty = barY + COMPASS_LABEL_Y;

                var pose = g.pose();
                pose.pushMatrix();
                pose.scale(COMPASS_LABEL_SCALE, COMPASS_LABEL_SCALE, pose);

                int sx = Math.round(tx / COMPASS_LABEL_SCALE);
                int sy = Math.round(ty / COMPASS_LABEL_SCALE);

                g.drawString(mc.font, label, sx, sy, 0xFFFFFFFF, true);

                pose.popMatrix();
            }
        }
    }

    private static void drawCenterLabel(GuiGraphics g, Minecraft mc, Cluster c, int centerX, int barY, int sw) {
        final float scale = CENTER_LABEL_SCALE;
        final int baseY = barY + BAR_H + CENTER_LABEL_Y_GAP;

        // Shift = расширенный режим (держишь Shift -> раскрыто)
        final boolean expanded = mc.options.keyShift.isDown();

        // ---------- 1 waypoint: одна строка ----------
        if (c.entries.size() == 1) {
            Entry e = c.entries.get(0);

            String name = safeName(e.w);
            if (name.length() > CENTER_NAME_MAX)
                name = name.substring(0, CENTER_NAME_MAX) + "…";
            int dist = (int) Math.round(e.dist);

            String s = name + " [" + dist + "m]";

            int tw = mc.font.width(s);
            int twScaled = Math.round(tw * scale);
            int tx = Mth.clamp(centerX - twScaled / 2, 4, sw - twScaled - 4);

            int sx = Math.round(tx / scale);
            int sy = Math.round(baseY / scale);

            int col = 0xFF000000 | (e.w.color & 0xFFFFFF);

            var pose = g.pose();
            if (scale != 1.0f) {
                pose.pushMatrix();
                pose.scale(scale, scale, pose);
            }

            g.drawString(mc.font, s, sx, sy, col, false);

            if (scale != 1.0f) {
                pose.popMatrix();
            }
            return;
        }

        // ---------- cluster: список строк ----------
        int limit = expanded ? CENTER_LIST_MAX_LINES : CENTER_COLLAPSED_LINES;

        int shown = Math.min(limit, c.entries.size());
        int remaining = c.entries.size() - shown;

        // если expanded, но всё равно обрезали по CENTER_LIST_MAX_LINES — тоже покажем
        // “… +N”
        boolean showMoreLine = remaining > 0;

        int totalLines = shown + (showMoreLine ? 1 : 0);
        String[] lines = new String[totalLines];
        int[] colors = new int[totalLines];

        int maxW = 0;

        for (int i = 0; i < shown; i++) {
            Entry e = c.entries.get(i);

            String name = shrinkName(safeName(e.w), CENTER_NAME_MAX);
            int dist = (int) Math.round(e.dist);

            String s = name + " [" + dist + "m]";
            lines[i] = s;
            colors[i] = 0xFF000000 | (e.w.color & 0xFFFFFF);

            maxW = Math.max(maxW, mc.font.width(s));
        }

        if (showMoreLine) {
            String more = "… +" + remaining + (expanded ? "" : " (Shift)");
            lines[totalLines - 1] = more;
            colors[totalLines - 1] = 0xFFFFFFFF;
            maxW = Math.max(maxW, mc.font.width(more));
        }

        int maxWScaled = Math.round(maxW * scale);
        int tx = Mth.clamp(centerX - maxWScaled / 2, 4, sw - maxWScaled - 4);

        int sx = Math.round(tx / scale);
        int sy0 = Math.round(baseY / scale);

        int lineStep = Math.round(CENTER_LIST_LINE_H / scale);

        var pose = g.pose();
        if (scale != 1.0f) {
            pose.pushMatrix();
            pose.scale(scale, scale, pose);
        }

        int y = sy0;
        for (int i = 0; i < totalLines; i++) {
            g.drawString(mc.font, lines[i], sx, y, colors[i], false);
            y += lineStep;
        }

        if (scale != 1.0f) {
            pose.popMatrix();
        }
    }

    private static String shrinkName(String s, int max) {
        if (s == null)
            return "(unnamed)";
        if (s.length() <= max)
            return s;
        return s.substring(0, Math.max(1, max - 1)) + "…";
    }

    // ----------------- Fades / math -----------------

    // private static float fadeByDistance(float dist) {
    // if (dist <= FADE_START)
    // return 1.0f;
    // if (dist >= FADE_END)
    // return 0.25f;
    // float t = (dist - FADE_START) / (FADE_END - FADE_START);
    // return Mth.lerp(t, 1.0f, 0.25f);
    // }

    private static float fadeByAngleAbs(float angleAbs) {
        if (angleAbs <= ANGLE_FADE_START)
            return 1.0f;
        if (angleAbs >= ANGLE_FADE_END)
            return 0.0f;
        float t = (angleAbs - ANGLE_FADE_START) / (ANGLE_FADE_END - ANGLE_FADE_START);
        return 1.0f - t;
    }

    private static int applyAlpha(int argb, float alpha01) {
        int a = (int) (Mth.clamp(alpha01, 0f, 1f) * 255f) & 0xFF;
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    // ----------------- Keys / smoothing -----------------

    private static String keyOf(Minecraft mc, WaypointStorage.Waypoint w) {
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

    // ----------------- Mouse scale helpers -----------------

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

    // ----------------- Compass labels -----------------

    private static float wrap360(float deg) {
        float d = deg % 360f;
        if (d < 0)
            d += 360f;
        return d;
    }

    private static String compassLabel(float deg0to360) {
        int d = Math.round(deg0to360 / 45f) * 45;
        d %= 360;
        return switch (d) {
            case 0 -> "N";
            case 45 -> "NE";
            case 90 -> "E";
            case 135 -> "SE";
            case 180 -> "S";
            case 225 -> "SW";
            case 270 -> "W";
            default -> "NW";
        };
    }

    private static String safeName(WaypointStorage.Waypoint w) {
        if (w == null)
            return "(unnamed)";
        String n = w.name;
        return (n == null || n.isBlank()) ? "(unnamed)" : n;
    }
}
