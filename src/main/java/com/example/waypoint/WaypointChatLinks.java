package com.example.waypoint;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WaypointChatLinks {
    private WaypointChatLinks() {}

    private static final Pattern WP = Pattern.compile("WP\\|([^|]+)\\|(-?\\d+)\\|(-?\\d+)\\|(-?\\d+)");

    public static boolean handle(Component message) {
        String s = message.getString();
        Matcher m = WP.matcher(s);
        if (!m.find()) return false;

        String name = m.group(1);
        int x = Integer.parseInt(m.group(2));
        int y = Integer.parseInt(m.group(3));
        int z = Integer.parseInt(m.group(4));

        String safeName = name.replaceAll("\\s+", "_");
        String cmd = "/wp set " + safeName + " " + x + " " + y + " " + z;

        MutableComponent add = Component.translatable("chat.waypointmod.add")
                .withStyle(st -> st.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent.RunCommand(cmd))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("chat.waypointmod.add_hover", safeName)
                        )));

        MutableComponent suggest = Component.translatable("chat.waypointmod.suggest")
                .withStyle(st -> st.withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent.SuggestCommand(cmd))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("chat.waypointmod.suggest_hover")
                        )));

        MutableComponent coords = Component.literal(" " + safeName + " @ " + x + " " + y + " " + z)
                .withStyle(ChatFormatting.GRAY);

        MutableComponent out = Component.translatable("chat.waypointmod.prefix")
                .withStyle(ChatFormatting.AQUA)
                .append(add)
                .append(Component.literal(" "))
                .append(suggest)
                .append(coords);

        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.getChat().addMessage(out);
        }
        return true;
    }
}
