package com.example.waypoint;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class WaypointStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String UNASSIGNED_WORLD = "__unassigned__";
    private static final Path FILE = FabricPaths.configDir().resolve("waypointmod-waypoints.json");

    private static boolean enabled = true;
    private static int deathLimit = 3;

    private static final List<Waypoint> all = new ArrayList<>();

    private WaypointStorage() {}

    public static final class Waypoint {
        public String name;
        public String world;
        public String dimension;
        public int x, y, z;
        public int color;        // 0xRRGGBB
        public String kind;      // "normal" | "death"
        public long createdAt;   // millis

        public Waypoint(String name, String world, String dimension, int x, int y, int z, int color) {
            this.name = name;
            this.world = world;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
        }
    }

    public static void toggle() { enabled = !enabled; save(); }
    public static boolean isEnabled() { return enabled; }

    public static int getDeathLimit() { return deathLimit; }

    public static void setDeathLimit(int limit) {
        deathLimit = Math.max(0, Math.min(50, limit));
        save();
    }

    public static void addHere(Minecraft mc, String name, Integer maybeColor) {
        ensureAssigned(mc);
        if (mc.player == null || mc.level == null) return;

        String world = currentWorldId(mc);
        String dim = currentDimId(mc);

        int x = (int) Math.floor(mc.player.getX());
        int y = (int) Math.floor(mc.player.getY());
        int z = (int) Math.floor(mc.player.getZ());

        int color = (maybeColor != null) ? maybeColor : stableColor(name);

        all.removeIf(w -> world.equals(w.world) && dim.equals(w.dimension) && w.name.equalsIgnoreCase(name));

        Waypoint w = new Waypoint(name, world, dim, x, y, z, color);
        w.kind = "normal";
        w.createdAt = System.currentTimeMillis();
        all.add(w);

        save();
    }

    public static void set(Minecraft mc, String name, int x, int y, int z, Integer maybeColor) {
        ensureAssigned(mc);
        if (mc.level == null) return;

        String world = currentWorldId(mc);
        String dim = currentDimId(mc);

        int color = (maybeColor != null) ? maybeColor : stableColor(name);

        all.removeIf(w -> world.equals(w.world) && dim.equals(w.dimension) && w.name.equalsIgnoreCase(name));

        Waypoint w = new Waypoint(name, world, dim, x, y, z, color);
        w.kind = "normal";
        w.createdAt = System.currentTimeMillis();
        all.add(w);

        save();
    }

    public static boolean remove(Minecraft mc, String name) {
        ensureAssigned(mc);
        if (mc.level == null) return false;

        String world = currentWorldId(mc);
        String dim = currentDimId(mc);

        boolean ok = all.removeIf(w -> world.equals(w.world) && dim.equals(w.dimension) && w.name.equalsIgnoreCase(name));
        if (ok) save();
        return ok;
    }

    public static void clear(Minecraft mc) {
        ensureAssigned(mc);
        if (mc.level == null) return;

        String world = currentWorldId(mc);
        String dim = currentDimId(mc);

        all.removeIf(w -> world.equals(w.world) && dim.equals(w.dimension));
        save();
    }

    public static List<Waypoint> listForCurrent(Minecraft mc) {
        ensureAssigned(mc);
        if (mc.level == null) return List.of();

        String world = currentWorldId(mc);
        String dim = currentDimId(mc);

        ArrayList<Waypoint> out = new ArrayList<>();
        for (var w : all) {
            if (world.equals(w.world) && dim.equals(w.dimension)) out.add(w);
        }
        return out;
    }

    public static void addDeath(Minecraft mc) {
        if (deathLimit <= 0) return;
        ensureAssigned(mc);
        if (mc.player == null || mc.level == null) return;

        String world = currentWorldId(mc);
        String dim = currentDimId(mc);

        int x = (int) Math.floor(mc.player.getX());
        int y = (int) Math.floor(mc.player.getY());
        int z = (int) Math.floor(mc.player.getZ());

        int color = 0xFF5555;

        Waypoint w = new Waypoint("LastDeath", world, dim, x, y, z, color);
        w.kind = "death";
        w.createdAt = System.currentTimeMillis();
        all.add(w);

        normalizeDeaths(world, dim);
        save();
    }

    private static void normalizeDeaths(String world, String dim) {
        var deaths = new ArrayList<Waypoint>();
        for (var w : all) {
            if (world.equals(w.world) && dim.equals(w.dimension) && "death".equals(w.kind)) {
                deaths.add(w);
            }
        }

        deaths.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));

        for (int i = deathLimit; i < deaths.size(); i++) {
            all.remove(deaths.get(i));
        }

        int keep = Math.min(deathLimit, deaths.size());
        for (int i = 0; i < keep; i++) {
            Waypoint w = deaths.get(i);
            w.name = (i == 0) ? "LastDeath" : ("Death" + (i + 1));
        }
    }

    public static String currentDimId(Minecraft mc) {
    if (mc.level == null) return "unknown";
    return mc.level.dimension().toString();
    }

    public static String currentWorldId(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            Path root = server.getWorldPath(LevelResource.ROOT);
            return "sp:" + root.toAbsolutePath().normalize();
        }

        ServerData data = mc.getCurrentServer();
        if (data != null && data.ip != null && !data.ip.isBlank()) {
            return "mp:" + data.ip.trim().toLowerCase(Locale.ROOT);
        }

        return "unknown";
    }

    public static void load() {
        all.clear();
        enabled = true;
        deathLimit = 3;

        if (!Files.exists(FILE)) return;

        try {
            String json = Files.readString(FILE, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            enabled = root.has("enabled") && root.get("enabled").getAsBoolean();
            deathLimit = root.has("deathLimit") ? root.get("deathLimit").getAsInt() : 3;

            if (root.has("waypoints") && root.get("waypoints").isJsonArray()) {
                for (JsonElement e : root.getAsJsonArray("waypoints")) {
                    if (!e.isJsonObject()) continue;
                    JsonObject o = e.getAsJsonObject();

                    String name = optString(o, "name", "wp");
                    String world = optString(o, "world", UNASSIGNED_WORLD);
                    String dim = optString(o, "dimension", "unknown");

                    int x = optInt(o, "x", 0);
                    int y = optInt(o, "y", 64);
                    int z = optInt(o, "z", 0);
                    int color = optInt(o, "color", stableColor(name));

                    String kind = optString(o, "kind", "normal");
                    long createdAt = o.has("createdAt") ? o.get("createdAt").getAsLong() : System.currentTimeMillis();

                    Waypoint w = new Waypoint(name, world, dim, x, y, z, color);
                    w.kind = kind;
                    w.createdAt = createdAt;
                    all.add(w);
                }
            }
        } catch (Exception ignored) {
            all.clear();
            enabled = true;
            deathLimit = 3;
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());

            JsonObject root = new JsonObject();
            root.addProperty("enabled", enabled);
            root.addProperty("deathLimit", deathLimit);

            JsonArray arr = new JsonArray();
            for (var w : all) {
                JsonObject o = new JsonObject();
                o.addProperty("name", w.name);
                o.addProperty("world", w.world);
                o.addProperty("dimension", w.dimension);
                o.addProperty("x", w.x);
                o.addProperty("y", w.y);
                o.addProperty("z", w.z);
                o.addProperty("color", w.color);
                o.addProperty("kind", w.kind == null ? "normal" : w.kind);
                o.addProperty("createdAt", w.createdAt == 0 ? System.currentTimeMillis() : w.createdAt);
                arr.add(o);
            }
            root.add("waypoints", arr);

            Files.writeString(
                    FILE,
                    GSON.toJson(root),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException ignored) {}
    }

    public static void clearDeaths(Minecraft mc) {
        ensureAssigned(mc);
        if (mc.level == null) return;

        String world = currentWorldId(mc);
        String dim = currentDimId(mc);

        all.removeIf(w -> world.equals(w.world) && dim.equals(w.dimension) && "death".equals(w.kind));
        save();
    }

    private static void ensureAssigned(Minecraft mc) {
        String world = currentWorldId(mc);
        if (world.equals("unknown")) return;

        boolean changed = false;
        for (var w : all) {
            if (UNASSIGNED_WORLD.equals(w.world)) {
                w.world = world;
                changed = true;
            }
        }
        if (changed) save();
    }

    private static String optString(JsonObject o, String k, String def) {
        return o.has(k) ? o.get(k).getAsString() : def;
    }

    private static int optInt(JsonObject o, String k, int def) {
        return o.has(k) ? o.get(k).getAsInt() : def;
    }

    private static int stableColor(String name) {
        int h = name == null ? 0 : name.toLowerCase(Locale.ROOT).hashCode();
        int r = 64 + (Math.abs(h) % 192);
        int g = 64 + (Math.abs(h / 31) % 192);
        int b = 64 + (Math.abs(h / 997) % 192);
        return (r << 16) | (g << 8) | b;
    }

    private static final class FabricPaths {
        static Path configDir() {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
        }
    }
}
