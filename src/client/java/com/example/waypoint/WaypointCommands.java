package com.example.waypoint;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public final class WaypointCommands {
    private WaypointCommands() {}

    private static int parseRgb(String s) {
        String t = s.startsWith("#") ? s.substring(1) : s;
        if (t.length() != 6) throw new IllegalArgumentException("hex must be RRGGBB");
        return Integer.parseInt(t, 16) & 0xFFFFFF;
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralCommandNode<FabricClientCommandSource> root = dispatcher.register(literal("wp"));

        // /wp add <name>
        root.addChild(
            literal("add")
                .then(argument("name", StringArgumentType.word())
                    .executes(ctx -> {
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client.player == null || client.world == null) return 0;

                        String name = StringArgumentType.getString(ctx, "name");
                        WaypointStorage.addHere(client, name, null);

                        ctx.getSource().sendFeedback(Text.literal("Added waypoint: " + name));
                        return 1;
                    })
                ).build()
        );

        // /wp set <name> <x> <y> <z>
        root.addChild(
            literal("set")
                .then(argument("name", StringArgumentType.word())
                    .then(argument("x", IntegerArgumentType.integer())
                        .then(argument("y", IntegerArgumentType.integer())
                            .then(argument("z", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    MinecraftClient client = MinecraftClient.getInstance();
                                    if (client.world == null) return 0;

                                    String name = StringArgumentType.getString(ctx, "name");
                                    int x = IntegerArgumentType.getInteger(ctx, "x");
                                    int y = IntegerArgumentType.getInteger(ctx, "y");
                                    int z = IntegerArgumentType.getInteger(ctx, "z");

                                    WaypointStorage.set(client, name, x, y, z, null);

                                    ctx.getSource().sendFeedback(Text.literal("Set waypoint: " + name));
                                    return 1;
                                })
                            )
                        )
                    )
                ).build()
        );

        // /wp list
        root.addChild(
            literal("list").executes(ctx -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.world == null) return 0;

                String worldKey = WaypointWorldKey.get(client);
                String dim = WaypointStorage.currentDimId(client);

                var list = WaypointStorage.listForCurrent(client);
                if (list.isEmpty()) {
                    ctx.getSource().sendFeedback(Text.literal("No waypoints here."));
                    return 1;
                }

                ctx.getSource().sendFeedback(Text.literal("Waypoints (" + worldKey + ", " + dim + "):"));
                for (var wp : list) {
                    ctx.getSource().sendFeedback(
                        Text.literal("- " + wp.name + " @ " + wp.x + " " + wp.y + " " + wp.z +
                            " #" + String.format("%06X", wp.color))
                    );
                }
                return 1;
            }).build()
        );

        // /wp remove <name>
        root.addChild(
            literal("remove")
                .then(argument("name", StringArgumentType.word())
                    .executes(ctx -> {
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client.world == null) return 0;

                        String name = StringArgumentType.getString(ctx, "name");
                        boolean ok = WaypointStorage.remove(client, name);

                        ctx.getSource().sendFeedback(Text.literal(ok ? "Removed: " + name : "Not found: " + name));
                        return 1;
                    })
                ).build()
        );

        // /wp clear
        root.addChild(
            literal("clear")
                // /wp clear
                .executes(ctx -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.world == null) return 0;

                    WaypointStorage.clear(client);
                    ctx.getSource().sendFeedback(Text.literal("Cleared waypoints in this world+dimension."));
                    return 1;
                })
                // /wp clear death
                .then(literal("death")
                    .executes(ctx -> {
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client.world == null) return 0;

                        WaypointStorage.clearDeaths(client);
                        ctx.getSource().sendFeedback(Text.literal("Cleared death waypoints in this world+dimension."));
                        return 1;
                    })
                )
                .build()
        );


        // /wp toggle
        root.addChild(
            literal("toggle").executes(ctx -> {
                WaypointStorage.toggle();
                ctx.getSource().sendFeedback(Text.literal(
                    "Waypoints rendering: " + (WaypointStorage.isEnabled() ? "ON" : "OFF")
                ));
                return 1;
            }).build()
        );

        // /wp color <name> <hex>
        root.addChild(
            literal("color")
                .then(argument("name", StringArgumentType.word())
                    .then(argument("hex", StringArgumentType.word())
                        .executes(ctx -> {
                            MinecraftClient client = MinecraftClient.getInstance();
                            if (client.world == null) return 0;

                            String name = StringArgumentType.getString(ctx, "name");
                            String hex = StringArgumentType.getString(ctx, "hex");

                            int rgb;
                            try {
                                rgb = parseRgb(hex);
                            } catch (Exception e) {
                                ctx.getSource().sendFeedback(Text.literal("Bad hex. Use RRGGBB or #RRGGBB"));
                                return 0;
                            }

                            // storage у тебя хранит цвет при set/addHere; для цвета сделаем через set(...) заново:
                            // сохраняем координаты как есть, просто меняем цвет
                            var list = WaypointStorage.listForCurrent(client);
                            var found = list.stream().filter(w -> w.name.equalsIgnoreCase(name)).findFirst();
                            if (found.isEmpty()) {
                                ctx.getSource().sendFeedback(Text.literal("Not found: " + name));
                                return 0;
                            }
                            var wp = found.get();
                            WaypointStorage.set(client, wp.name, wp.x, wp.y, wp.z, rgb);

                            ctx.getSource().sendFeedback(Text.literal("Color set for " + name));
                            return 1;
                        })
                    )
                ).build()
        );

        // /wp deathlimit <0..50>
        root.addChild(
            literal("deathlimit")
                .then(argument("count", IntegerArgumentType.integer(0, 50))
                    .executes(ctx -> {
                        int n = IntegerArgumentType.getInteger(ctx, "count");
                        WaypointStorage.setDeathLimit(n);
                        ctx.getSource().sendFeedback(Text.literal("Death waypoints limit: " + WaypointStorage.getDeathLimit()));
                        return 1;
                    })
                ).build()
        );
    }
}
