package com.example.waypoint;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
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

    // Формат: WP|name|x|y|z
    private static final Pattern WP = Pattern.compile("WP\\|([^|]+)\\|(-?\\d+)\\|(-?\\d+)\\|(-?\\d+)");

    public static void register() {
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, ts) -> {
            String s = message.getString();
            Matcher m = WP.matcher(s);
            if (!m.find()) return true;

            String name = m.group(1);
            int x = Integer.parseInt(m.group(2));
            int y = Integer.parseInt(m.group(3));
            int z = Integer.parseInt(m.group(4));

            String safeName = name.replaceAll("\\s+", "_");

            // ВАЖНО: команда должна начинаться с "/" (ограничение клиента)
            String cmd = "/wp set " + safeName + " " + x + " " + y + " " + z;

            MutableComponent add = Component.literal("[ADD]")
                    .withStyle(st -> st.withColor(ChatFormatting.GREEN)
                            .withClickEvent(new ClickEvent.RunCommand(cmd))
                            .withHoverEvent(new HoverEvent.ShowText(
                                    Component.literal("Добавить точку себе: " + safeName)
                            )));

            MutableComponent suggest = Component.literal("[SUGGEST]")
                    .withStyle(st -> st.withColor(ChatFormatting.YELLOW)
                            .withClickEvent(new ClickEvent.SuggestCommand(cmd))
                            .withHoverEvent(new HoverEvent.ShowText(
                                    Component.literal("Подставить команду в чат")
                            )));

            MutableComponent coords = Component.literal(" " + safeName + " @ " + x + " " + y + " " + z)
                    .withStyle(ChatFormatting.GRAY);

            MutableComponent out = Component.literal("[WP] ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(add)
                    .append(Component.literal(" "))
                    .append(suggest)
                    .append(coords);

            Minecraft mc = Minecraft.getInstance();
            if (mc.gui != null) {
                mc.gui.getChat().addMessage(out);
            }

            // Оригинал не показываем (чтобы не было дубля)
            return false;
        });
    }
}
