package com.example.waypoint;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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
    private WaypointAutoPoints() {
    }

    // ==== Settings / names ====
    private static final int COLOR_FIRST_JOIN = 0x55FF55;
    private static final int COLOR_SPAWN = 0x55FFFF;
    private static final int COLOR_NETHER = 0xFF5555;
    private static final int COLOR_END = 0xAA55FF;

    // ==== Session tracking for transitions ====
    private static String lastWorldId = null;
    private static String lastDimId = null;
    private static boolean didFirstJoinHere = false;
    private static boolean didSpawnPoint = false;

    // ==== Advancement auto-waypoints ====
    private static final Map<Identifier, Spec> SPECS = new HashMap<>();
    private static final Map<String, Set<Identifier>> TRIGGERED = new HashMap<>();

    static {
        // ====== измерения / ключевые прогресс-события ======
        put("story/enter_the_nether", "EnterNether", 0xFFAA00);
        put("story/enter_the_end", "EnterEnd", 0xAA55FF);
        put("end/kill_dragon", "DragonKilled", 0xFF55FF);
        put("end/enter_end_gateway", "EndGateway", 0x55FFFF);

        // ====== Stronghold / путь к Энду ======
        put("story/follow_ender_eye", "Stronghold", 0xAA66FF);

        // ====== Nether structures ======
        put("nether/find_fortress", "NetherFortress", 0xFF5555);

        // Bastion (есть два варианта — “нашёл” и “залутал”)
        put("nether/find_bastion", "Bastion", 0xFFAA00);
        put("nether/loot_bastion", "BastionLoot", 0xFFCC55);

        // ====== End structures ======
        put("end/find_end_city", "EndCity", 0xFF55FF);
        put("end/elytra", "Elytra", 0x55FFDD);

        // ====== Trial Chambers (1.21+) ======
        // Если в твоей версии какой-то id отличается — включи лог и подставь
        // фактический id.
        put("adventure/minecraft_trials_edition", "TrialChambers", 0x55FFFF);
        put("adventure/under_lock_and_key", "TrialVault", 0x66FFCC);
        put("adventure/revaulting", "OminousVault", 0x66CCFF);

        // ====== Полезные “прокси” (не строго структура, но часто привязано к месту)
        // ======
        put("adventure/voluntary_exile", "PillagerCaptain", 0xBBBBBB); // часто возле аванпоста/патруля
        put("adventure/hero_of_the_village", "RaidWin", 0x55FF55); // деревня
        put("adventure/totem_of_undying", "Evoker", 0xFFFF55); // рейд/особняк

        // Deep Dark “прокси”
        put("adventure/avoid_vibration", "DeepDark", 0x5577FF);
        put("adventure/kill_mob_near_sculk_catalyst", "SculkCatalyst", 0x3355FF);

        // Навигация / база
        put("adventure/use_lodestone", "Lodestone", 0x55FFFF);
    }

    private record Spec(String name, int color) {
    }

    private static void put(String advId, String wpName, int color) {
        SPECS.put(Identifier.parse(advId), new Spec(wpName, color));
    }

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetSession());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            resetSession();
            TRIGGERED.clear();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null)
                return;

            String world = WaypointStorage.currentWorldId(mc);
            String dim = WaypointStorage.currentDimId(mc);

            if (lastWorldId == null) {
                lastWorldId = world;
                lastDimId = dim;
                didFirstJoinHere = false;
                didSpawnPoint = false;
            }

            // 1) First join marker (current position)
            if (!didFirstJoinHere) {
                didFirstJoinHere = true;
                createOnce(mc, "FirstJoin", COLOR_FIRST_JOIN,
                        Mth.floor(mc.player.getX()),
                        Mth.floor(mc.player.getY()),
                        Mth.floor(mc.player.getZ()));
            }

            // 2) World spawn marker (shared spawn) — once per session
            if (!didSpawnPoint) {
                didSpawnPoint = true;
                BlockPos sp = tryGetSpawnPos(mc);
                if (sp != null) {
                    createOnce(mc, "WorldSpawn", COLOR_SPAWN, sp.getX(), sp.getY(), sp.getZ());
                }
            }

            // 3) Dimension change marker
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
        });
    }

    private static void resetSession() {
        lastWorldId = null;
        lastDimId = null;
        didFirstJoinHere = false;
        didSpawnPoint = false;
    }

    /**
     * Вызывается миксином ClientAdvancementsMixin после packet update.
     * progressUpdates: только обновлённые ачивки.
     */
    public static void onAdvancementProgressUpdate(Map<Identifier, AdvancementProgress> progressUpdates) {
        if (!WaypointStorage.isAutoPointsEnabled())
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
            return;
        if (progressUpdates == null || progressUpdates.isEmpty())
            return;

        String worldKey = WaypointStorage.currentWorldId(mc);
        Set<Identifier> seen = TRIGGERED.computeIfAbsent(worldKey, k -> new HashSet<>());

        for (var e : progressUpdates.entrySet()) {
            Identifier id = e.getKey();
            AdvancementProgress prog = e.getValue();
            if (id == null || prog == null)
                continue;

            Spec spec = SPECS.get(id);
            if (spec == null)
                continue;
            if (seen.contains(id))
                continue;
            if (!prog.isDone())
                continue;

            int x = Mth.floor(mc.player.getX());
            int y = Mth.floor(mc.player.getY());
            int z = Mth.floor(mc.player.getZ());

            WaypointStorage.set(mc, spec.name, x, y, z, spec.color);
            seen.add(id);
        }
    }

    /** Если хочешь очищать кэш на дисконнекте — вызови это из твоего client init */
    public static void clearTriggered() {
        TRIGGERED.clear();
    }

    private static void createOnce(Minecraft mc, String name, int color, int x, int y, int z) {
        for (var w : WaypointStorage.listForCurrentAll(mc)) {
            if (w.name != null && w.name.equalsIgnoreCase(name))
                return;
        }
        WaypointStorage.set(mc, name, x, y, z, color);
    }

    private static String shortDim(String dimId) {
        String s = dimId.toLowerCase(Locale.ROOT);
        int idx = s.lastIndexOf("minecraft:");
        if (idx >= 0)
            s = s.substring(idx + "minecraft:".length());
        s = s.replace("]", "")
                .replace("resourcekey[minecraft:dimension / ", "")
                .trim();
        if (s.isEmpty())
            s = "unknown";
        return s;
    }

    private static BlockPos tryGetSpawnPos(Minecraft mc) {
        if (mc == null || mc.level == null)
            return null;

        Object level = mc.level;

        // 1) Пробуем методы уровня (ClientLevel/Level)
        BlockPos p = (BlockPos) invokeNoArgs(level, BlockPos.class,
                "getSharedSpawnPos",
                "getSharedSpawnPosition",
                "getSpawnPos",
                "getSpawnPosition");
        if (p != null)
            return p;

        // 2) Пробуем LevelData / world data
        Object levelData = invokeNoArgs(level, Object.class,
                "getLevelData",
                "getLevelDataUnsafe",
                "getWorldData");

        if (levelData != null) {
            p = (BlockPos) invokeNoArgs(levelData, BlockPos.class,
                    "getSpawnPos",
                    "getSpawnPosition");
            if (p != null)
                return p;

            // 3) Пробуем координаты спавна по отдельности
            Integer x = (Integer) invokeNoArgs(levelData, Integer.class, "getXSpawn", "getSpawnX");
            Integer y = (Integer) invokeNoArgs(levelData, Integer.class, "getYSpawn", "getSpawnY");
            Integer z = (Integer) invokeNoArgs(levelData, Integer.class, "getZSpawn", "getSpawnZ");

            if (x != null && y != null && z != null)
                return new BlockPos(x, y, z);
        }

        return null;
    }

    private static Object invokeNoArgs(Object target, Class<?> expected, String... names) {
        for (String n : names) {
            try {
                Method m = target.getClass().getMethod(n);
                Object r = m.invoke(target);
                if (r != null && expected.isInstance(r))
                    return r;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
