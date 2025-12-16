package com.example.waypoint;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

public final class WaypointRenderer {
    private WaypointRenderer() {}

    public static void render(WorldRenderContext ctx) {
        if (!WaypointStorage.isEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        MatrixStack matrices = ctx.matrixStack();
        VertexConsumerProvider consumers = ctx.consumers();
        if (matrices == null || consumers == null) return;

        // ВАЖНО: делаем копию, чтобы спокойно рендерить даже если список изменится
        List<WaypointStorage.Waypoint> waypoints =
        new ArrayList<>(WaypointStorage.listForCurrent(client));
        if (waypoints.isEmpty()) return;

        Vec3d cam = ctx.camera().getPos();
        Quaternionf camRot = ctx.camera().getRotation();

        // границы мира по высоте (луч "от низа до верха")
        int minY = client.world.getDimension().minY();
        int maxY = minY + client.world.getDimension().height(); // верхняя граница (обычно как "потолок мира")

        // ---------- PASS 1: геометрия (маркер + луч) ----------
        for (var wp : waypoints) {
            double wx = wp.x + 0.5;
            double wy = wp.y;
            double wz = wp.z + 0.5;

            double x = wx - cam.x;
            double y = wy - cam.y;
            double z = wz - cam.z;

            float r = ((wp.color >> 16) & 255) / 255f;
            float g = ((wp.color >> 8) & 255) / 255f;
            float b = (wp.color & 255) / 255f;

            // 1) Маркер-куб (контур)
            VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
            VertexRendering.drawBox(
                    matrices, lines,
                    x - 0.30, y - 0.30, z - 0.30,
                    x + 0.30, y + 0.30, z + 0.30,
                    r, g, b, 1.0f
            );

            // 2) Луч (тонкий полупрозрачный filled box) от низа мира до верха мира
            double beamHalf = 0.06; // толщина луча (увеличь если хочется)
            double beamBottom = minY - cam.y;
            double beamTop = maxY - cam.y;

            VertexConsumer beam = consumers.getBuffer(RenderLayer.getDebugFilledBox());
            VertexRendering.drawFilledBox(
                    matrices, beam,
                    x - beamHalf, beamBottom, z - beamHalf,
                    x + beamHalf, beamTop,    z + beamHalf,
                    r, g, b, 0.18f
            );
        }

        // ---------- PASS 2: подписи (текст) ----------
        for (var wp : waypoints) {
            double wx = wp.x + 0.5;
            double wy = wp.y;
            double wz = wp.z + 0.5;

            double x = wx - cam.x;
            double y = wy - cam.y;
            double z = wz - cam.z;

            // Подпись чуть выше точки (а не на самом потолке)
            drawLabel(client, matrices, consumers, camRot, x, y + 1.8, z, wp.name, wp.color);
        }
    }

    private static void drawLabel(
            MinecraftClient client,
            MatrixStack matrices,
            VertexConsumerProvider consumers,
            Quaternionf cameraRotation,
            double x, double y, double z,
            String name,
            int rgb
    ) {
        TextRenderer tr = client.textRenderer;
        Text text = Text.literal(name);

        float scale = 0.03f;
        matrices.push();
        matrices.translate(x, y, z);
        matrices.multiply(cameraRotation);
        matrices.scale(-scale, -scale, scale);

        float w = tr.getWidth(text);
        int bg = 0x55000000;
        int argb = 0xFF000000 | (rgb & 0xFFFFFF);

        tr.draw(
                text,
                -w / 2.0f,
                0.0f,
                argb,
                false,
                matrices.peek().getPositionMatrix(),
                consumers,
                TextRenderer.TextLayerType.SEE_THROUGH,
                bg,
                0x00F000F0
        );

        matrices.pop();
    }
}
