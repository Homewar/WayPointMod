package com.example.waypoint;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class WaypointCommands {
    private WaypointCommands() {}

    private static int parseRgb(String s) {
        String t = s.startsWith("#") ? s.substring(1) : s;
        if (t.length() != 6) throw new IllegalArgumentException("hex must be RRGGBB");
        return Integer.parseInt(t, 16) & 0xFFFFFF;
    }

    private static void feedback(CommandSourceStack source, Component component) {
        source.sendSuccess(() -> component, false);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("wp"));

        root.addChild(
                Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player == null || mc.level == null) return 0;

                                    String name = StringArgumentType.getString(ctx, "name");
                                    WaypointStorage.addHere(mc, name, null);

                                    feedback(ctx.getSource(), Component.translatable("commands.waypointmod.added", name));
                                    return 1;
                                })
                        ).build()
        );

        root.addChild(
                Commands.literal("set")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .executes(ctx -> {
                                                            Minecraft mc = Minecraft.getInstance();
                                                            if (mc.level == null) return 0;

                                                            String name = StringArgumentType.getString(ctx, "name");
                                                            int x = IntegerArgumentType.getInteger(ctx, "x");
                                                            int y = IntegerArgumentType.getInteger(ctx, "y");
                                                            int z = IntegerArgumentType.getInteger(ctx, "z");

                                                            WaypointStorage.set(mc, name, x, y, z, null);

                                                            feedback(ctx.getSource(), Component.translatable("commands.waypointmod.set", name));
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        ).build()
        );

        root.addChild(
                Commands.literal("list").executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level == null) return 0;

                    String worldKey = WaypointWorldKey.get(mc);
                    String dim = WaypointStorage.currentDimId(mc);

                    var list = WaypointStorage.listForCurrent(mc);
                    if (list.isEmpty()) {
                        feedback(ctx.getSource(), Component.translatable("commands.waypointmod.none_here"));
                        return 1;
                    }

                    feedback(ctx.getSource(), Component.translatable("commands.waypointmod.list_header", worldKey, dim));
                    for (var wp : list) {
                        feedback(ctx.getSource(), Component.translatable("commands.waypointmod.list_item", wp.name, wp.x, wp.y, wp.z, String.format("%06X", wp.color)));
                    }
                    return 1;
                }).build()
        );

        root.addChild(
                Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.level == null) return 0;

                                    String name = StringArgumentType.getString(ctx, "name");
                                    boolean ok = WaypointStorage.remove(mc, name);

                                    feedback(ctx.getSource(), Component.translatable(ok ? "commands.waypointmod.removed" : "commands.waypointmod.not_found", name));
                                    return 1;
                                })
                        ).build()
        );

        root.addChild(
                Commands.literal("clear")
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.level == null) return 0;

                            WaypointStorage.clear(mc);
                            feedback(ctx.getSource(), Component.translatable("commands.waypointmod.cleared"));
                            return 1;
                        })
                        .then(Commands.literal("death")
                                .executes(ctx -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.level == null) return 0;

                                    WaypointStorage.clearDeaths(mc);
                                    feedback(ctx.getSource(), Component.translatable("commands.waypointmod.cleared_death"));
                                    return 1;
                                })
                        )
                        .build()
        );

        root.addChild(
                Commands.literal("toggle").executes(ctx -> {
                    WaypointStorage.toggle();
                    feedback(ctx.getSource(), Component.translatable("commands.waypointmod.toggle", Component.translatable(WaypointStorage.isEnabled() ? "screen.waypointmod.on" : "screen.waypointmod.off")));
                    return 1;
                }).build()
        );

        root.addChild(
                Commands.literal("color")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("hex", StringArgumentType.word())
                                        .executes(ctx -> {
                                            Minecraft mc = Minecraft.getInstance();
                                            if (mc.level == null) return 0;

                                            String name = StringArgumentType.getString(ctx, "name");
                                            String hex = StringArgumentType.getString(ctx, "hex");

                                            int rgb;
                                            try {
                                                rgb = parseRgb(hex);
                                            } catch (Exception e) {
                                                feedback(ctx.getSource(), Component.translatable("commands.waypointmod.bad_hex"));
                                                return 0;
                                            }

                                            var list = WaypointStorage.listForCurrent(mc);
                                            var found = list.stream().filter(w -> w.name.equalsIgnoreCase(name)).findFirst();
                                            if (found.isEmpty()) {
                                                feedback(ctx.getSource(), Component.translatable("commands.waypointmod.not_found", name));
                                                return 0;
                                            }

                                            var wp = found.get();
                                            WaypointStorage.set(mc, wp.name, wp.x, wp.y, wp.z, rgb);

                                            feedback(ctx.getSource(), Component.translatable("commands.waypointmod.color_set", name));
                                            return 1;
                                        })
                                )
                        ).build()
        );

        root.addChild(
                Commands.literal("deathlimit")
                        .then(Commands.argument("count", IntegerArgumentType.integer(0, 50))
                                .executes(ctx -> {
                                    int n = IntegerArgumentType.getInteger(ctx, "count");
                                    WaypointStorage.setDeathLimit(n);
                                    feedback(ctx.getSource(), Component.translatable("commands.waypointmod.deathlimit", WaypointStorage.getDeathLimit()));
                                    return 1;
                                })
                        ).build()
        );
    }
}
