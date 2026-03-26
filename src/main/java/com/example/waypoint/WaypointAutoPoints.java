package com.example.waypoint;

import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class WaypointAutoPoints {
    private WaypointAutoPoints() {}

    private static final int COLOR_FIRST_JOIN = 0x55FF55;
    private static final int COLOR_SPAWN = 0x55FFFF;
    private static final int COLOR_NETHER = 0xFF5555;
    private static final int COLOR_END = 0xAA55FF;

    private static String lastWorldId = null;
    private static String lastDimId = null;
    private static boolean didFirstJoinHere = false;
    private static boolean didSpawnPoint = false;

    private static final Map<Identifier, Spec> SPECS = new HashMap<>();
    private static final Map<String, Set<Identifier>> TRIGGERED = new HashMap<>();

    static {
        put("story/enter_the_nether", "EnterNether", 0xFFAA00);
        put("story/enter_the_end", "EnterEnd", 0xAA55FF);
        put("end/kill_dragon", "DragonKilled", 0xFF55FF);
        put("end/enter_end_gateway", "EndGateway", 0x55FFFF);
        put("story/follow_ender_eye", "Stronghold", 0xAA66FF);
        put("nether/find_fortress", "NetherFortress", 0xFF5555);
        put("nether/find_bastion", "Bastion", 0xFFAA00);
        put("nether/loot_bastion", "BastionLoot", 0xFFCC55);
        put("end/find_end_city", "EndCity", 0xFF55FF);
        put("end/elytra", "Elytra", 0x55FFDD);
        put("adventure/minecraft_trials_edition", "TrialChambers", 0x55FFFF);
        put("adventure/under_lock_and_key", "TrialVault", 0x66FFCC);
        put("adventure/revaulting", "OminousVault", 0x66CCFF);
        put("adventure/voluntary_exile", "PillagerCaptain", 0xBBBBBB);
        put("adventure/hero_of_the_village", "RaidWin", 0x55FF55);
        put("adventure/totem_of_undying", "Evoker", 0xFFFF55);
        put("adventure/avoid_vibration", "DeepDark", 0x5577FF);
        put("adventure/kill_mob_near_sculk_catalyst", "SculkCatalyst", 0x3355FF);
        put("adventure/use_lodestone", "Lodestone", 0x55FFFF);
    }

    private record Spec(String name, int color) {}

    private static void put(String advId, String wpName, int color) {
        SPECS.put(Identifier.parse(advId), new Spec(wpName, color));
    }

    public static void onClientLogin() {
        resetSession();
    }

    public static void onClientLogout() {
        resetSession();
        TRIGGERED.clear();
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        String world = WaypointStorage.currentWorldId(mc);
        String dim = WaypointStorage.currentDimId(mc);

        if (lastWorldId == null) {
            lastWorldId = world;
            lastDimId = dim;
            didFirstJoinHere = false;
            didSpawnPoint = false;
        }

        if (!WaypointStorage.isAutoPointsEnabled()) return;

        if (!didFirstJoinHere) {
            didFirstJoinHere = true;
            createOnce(mc, "FirstJoin", COLOR_FIRST_JOIN,
                    Mth.floor(mc.player.getX()),
                    Mth.floor(mc.player.getY()),
                    Mth.floor(mc.player.getZ()));
        }

        if (!didSpawnPoint) {
            didSpawnPoint = true;
            BlockPos sp = tryGetSpawnPos(mc);
            if (sp != null) createOnce(mc, "WorldSpawn", COLOR_SPAWN, sp.getX(), sp.getY(), sp.getZ());
        }

        if (lastDimId != null && !dim.equals(lastDimId)) {
            lastDimId = dim;
            String shortDim = shortDim(dim);
            int c = switch (shortDim) {
                case "the_nether" -> COLOR_NETHER;
                case "the_end" -> COLOR_END;
                default -> COLOR_SPAWN;
            };
            createOnce(mc, "Entered_" + shortDim, c,
                    Mth.floor(mc.player.getX()),
                    Mth.floor(mc.player.getY()),
                    Mth.floor(mc.player.getZ()));
        }
    }

    private static void resetSession() {
        lastWorldId = null;
        lastDimId = null;
        didFirstJoinHere = false;
        didSpawnPoint = false;
    }

    public static void onAdvancementProgressUpdate(Map<Identifier, AdvancementProgress> progressUpdates) {
        if (!WaypointStorage.isAutoPointsEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (progressUpdates == null || progressUpdates.isEmpty()) return;

        String worldKey = WaypointStorage.currentWorldId(mc);
        Set<Identifier> seen = TRIGGERED.computeIfAbsent(worldKey, k -> new HashSet<>());

        for (var e : progressUpdates.entrySet()) {
            Identifier id = e.getKey();
            AdvancementProgress prog = e.getValue();
            if (id == null || prog == null) continue;
            Spec spec = SPECS.get(id);
            if (spec == null) continue;
            if (seen.contains(id)) continue;
            if (!prog.isDone()) continue;

            int x = Mth.floor(mc.player.getX());
            int y = Mth.floor(mc.player.getY());
            int z = Mth.floor(mc.player.getZ());
            WaypointStorage.set(mc, spec.name, x, y, z, spec.color);
            seen.add(id);
        }
    }

    public static void clearTriggered() {
        TRIGGERED.clear();
    }

    private static void createOnce(Minecraft mc, String name, int color, int x, int y, int z) {
        if (!WaypointStorage.isAutoPointsEnabled()) return;
        for (var w : WaypointStorage.listForCurrentAll(mc)) {
            if (w.name != null && w.name.equalsIgnoreCase(name)) return;
        }
        WaypointStorage.set(mc, name, x, y, z, color);
    }

    private static String shortDim(String dimId) {
        String s = dimId.toLowerCase(Locale.ROOT);
        int idx = s.lastIndexOf("minecraft:");
        if (idx >= 0) s = s.substring(idx + "minecraft:".length());
        s = s.replace("]", "").replace("resourcekey[minecraft:dimension / ", "").trim();
        return s.isEmpty() ? "unknown" : s;
    }

    private static BlockPos tryGetSpawnPos(Minecraft mc) {
        if (mc == null || mc.level == null) return null;
        Object level = mc.level;

        BlockPos p = (BlockPos) invokeNoArgs(level, BlockPos.class,
                "getSharedSpawnPos", "getSharedSpawnPosition", "getSpawnPos", "getSpawnPosition");
        if (p != null) return p;

        Object levelData = invokeNoArgs(level, Object.class, "getLevelData", "getLevelDataUnsafe", "getWorldData");
        if (levelData != null) {
            p = (BlockPos) invokeNoArgs(levelData, BlockPos.class,
                    "getSharedSpawnPos", "getSharedSpawnPosition", "getSpawnPos", "getSpawnPosition");
            if (p != null) return p;

            Integer x = (Integer) invokeNoArgs(levelData, Integer.class, "getXSpawn", "getSpawnX");
            Integer y = (Integer) invokeNoArgs(levelData, Integer.class, "getYSpawn", "getSpawnY");
            Integer z = (Integer) invokeNoArgs(levelData, Integer.class, "getZSpawn", "getSpawnZ");
            if (x != null && y != null && z != null) return new BlockPos(x, y, z);
        }
        return null;
    }

    private static Object invokeNoArgs(Object target, Class<?> expected, String... names) {
        for (String n : names) {
            try {
                Method m = target.getClass().getMethod(n);
                Object r = m.invoke(target);
                if (r != null && expected.isInstance(r)) return r;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
