package com.example.waypoint;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class WaypointRenderPipeline implements AutoCloseable {
    private static final WaypointRenderPipeline INSTANCE = new WaypointRenderPipeline();
    private WaypointRenderPipeline() {}

    public static WaypointRenderPipeline getInstance() {
        return INSTANCE;
    }

    // Filled “through walls” (depth test off)
    private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(WaypointMod.MOD_ID, "pipeline/waypoint_filled_through_walls"))
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build()
    );

    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Matrix4f IDENTITY_TEX = new Matrix4f(); // identity

    private BufferBuilder buffer;
    private MappableRingBuffer vertexBuffer;

    public static void init() {
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(INSTANCE::extractAndDraw);
    }

    private void extractAndDraw(WorldRenderContext context) {
        renderWaypoints(context);

        if (buffer != null) {
            drawFilledThroughWalls(Minecraft.getInstance(), FILLED_THROUGH_WALLS);
        }
    }

    private void renderWaypoints(WorldRenderContext context) {
        if (!WaypointStorage.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        var waypoints = WaypointStorage.listForCurrent(mc);
        if (waypoints.isEmpty()) return;

        PoseStack poseStack = context.matrices();
        if (poseStack == null) return;

        Vec3 cam = context.worldState().cameraRenderState.pos;

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        if (buffer == null) {
            buffer = new BufferBuilder(
                    ALLOCATOR,
                    FILLED_THROUGH_WALLS.getVertexFormatMode(),
                    FILLED_THROUGH_WALLS.getVertexFormat()
            );
        }

        Matrix4f mat = poseStack.last().pose();

        int minY = mc.level.getMinY();
        int maxY = mc.level.getMaxY();

        for (var wp : waypoints) {
            double wx = wp.x + 0.5;
            double wy = wp.y;
            double wz = wp.z + 0.5;

            int r = (wp.color >> 16) & 255;
            int g = (wp.color >> 8) & 255;
            int b = (wp.color) & 255;

            // marker
            addFilledBoxDoubleSided(
                    buffer, mat,
                    wx - 0.30, wy - 0.30, wz - 0.30,
                    wx + 0.30, wy + 0.30, wz + 0.30,
                    r, g, b, (int) (0.45f * 255f)
            );

            // beam
            double half = 0.06;
            addFilledBoxDoubleSided(
                    buffer, mat,
                    wx - half, (double) minY, wz - half,
                    wx + half, (double) maxY, wz + half,
                    r, g, b, (int) (0.18f * 255f)
            );
        }

        poseStack.popPose();
    }

    // ---------- geometry helpers ----------

    private static void addFilledBoxDoubleSided(
            BufferBuilder buf, Matrix4f mat,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            int r, int g, int b, int a
    ) {
        double minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        double minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        double minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);

        // 6 faces, each emitted twice (both windings) so it shows even if cull is enabled somewhere.

        // -Z
        quadBoth(buf, mat,
                minX, minY, minZ,
                maxX, minY, minZ,
                maxX, maxY, minZ,
                minX, maxY, minZ,
                r, g, b, a);

        // +Z
        quadBoth(buf, mat,
                minX, minY, maxZ,
                minX, maxY, maxZ,
                maxX, maxY, maxZ,
                maxX, minY, maxZ,
                r, g, b, a);

        // -X
        quadBoth(buf, mat,
                minX, minY, minZ,
                minX, maxY, minZ,
                minX, maxY, maxZ,
                minX, minY, maxZ,
                r, g, b, a);

        // +X
        quadBoth(buf, mat,
                maxX, minY, minZ,
                maxX, minY, maxZ,
                maxX, maxY, maxZ,
                maxX, maxY, minZ,
                r, g, b, a);

        // -Y
        quadBoth(buf, mat,
                minX, minY, minZ,
                minX, minY, maxZ,
                maxX, minY, maxZ,
                maxX, minY, minZ,
                r, g, b, a);

        // +Y
        quadBoth(buf, mat,
                minX, maxY, minZ,
                maxX, maxY, minZ,
                maxX, maxY, maxZ,
                minX, maxY, maxZ,
                r, g, b, a);
    }

    private static void quadBoth(
            BufferBuilder buf, Matrix4f mat,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            double x3, double y3, double z3,
            double x4, double y4, double z4,
            int r, int g, int b, int a
    ) {
        // forward
        v(buf, mat, x1, y1, z1, r, g, b, a);
        v(buf, mat, x2, y2, z2, r, g, b, a);
        v(buf, mat, x3, y3, z3, r, g, b, a);
        v(buf, mat, x4, y4, z4, r, g, b, a);

        // reverse
        v(buf, mat, x4, y4, z4, r, g, b, a);
        v(buf, mat, x3, y3, z3, r, g, b, a);
        v(buf, mat, x2, y2, z2, r, g, b, a);
        v(buf, mat, x1, y1, z1, r, g, b, a);
    }

    private static void v(BufferBuilder buf, Matrix4f mat, double x, double y, double z, int r, int g, int b, int a) {
        buf.addVertex(mat, (float) x, (float) y, (float) z).setColor(r, g, b, a);
    }

    // ---------- upload + draw ----------

    private void drawFilledThroughWalls(Minecraft client, RenderPipeline pipeline) {
        MeshData mesh = buffer.buildOrThrow();
        MeshData.DrawState drawState = mesh.drawState();
        VertexFormat format = drawState.format();

        GpuBuffer vertices = upload(drawState, format, mesh);
        draw(client, pipeline, mesh, drawState, vertices, format);

        vertexBuffer.rotate();
        buffer = null;
    }

    private GpuBuffer upload(MeshData.DrawState drawState, VertexFormat format, MeshData mesh) {
        int bytes = drawState.vertexCount() * format.getVertexSize();

        if (vertexBuffer == null || vertexBuffer.size() < bytes) {
            vertexBuffer = new MappableRingBuffer(
                    () -> WaypointMod.MOD_ID + " waypoint pipeline",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE,
                    bytes
            );
        }

        var encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView mapped = encoder.mapBuffer(
                vertexBuffer.currentBuffer().slice(0, mesh.vertexBuffer().remaining()),
                false,
                true
        )) {
            MemoryUtil.memCopy(mesh.vertexBuffer(), mapped.data());
        }

        return vertexBuffer.currentBuffer();
    }

    private static void draw(
            Minecraft client,
            RenderPipeline pipeline,
            MeshData mesh,
            MeshData.DrawState drawState,
            GpuBuffer vertices,
            VertexFormat format
    ) {
        GpuBuffer indices;
        VertexFormat.IndexType indexType;

        if (pipeline.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
            mesh.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
            indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(mesh.indexBuffer());
            indexType = drawState.indexType();
        } else {
            RenderSystem.AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
            indices = seq.getBuffer(drawState.indexCount());
            indexType = seq.type();
        }

        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, new Vector3f(), IDENTITY_TEX);

        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> WaypointMod.MOD_ID + " waypoint render",
                        client.getMainRenderTarget().getColorTextureView(),
                        OptionalInt.empty(),
                        client.getMainRenderTarget().getDepthTextureView(),
                        OptionalDouble.empty()
                )) {

            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);

            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, indexType);
            pass.drawIndexed(0, 0, drawState.indexCount(), 1);
        }

        mesh.close();
    }

    @Override
    public void close() {
        ALLOCATOR.close();
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }
}
