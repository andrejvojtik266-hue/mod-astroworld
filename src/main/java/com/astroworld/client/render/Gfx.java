package com.astroworld.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Tiny immediate-mode helpers for emissive geometry (boxes, beams, rings, quads). */
public final class Gfx {
    private Gfx() {}

    public static int rgb(float r, float g, float b) {
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }
    private static int clamp(float v) { return Math.max(0, Math.min(255, (int) (v * 255f))); }

    /** HSV (h in 0..1) -> packed RGB. */
    public static int hsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float r = 0, g = 0, b = 0;
        int i = (int) (h * 6); float f = h * 6 - i;
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return rgb(r, g, b);
    }

    /** Start a batch of colored quads. additive=true gives light-like glow. */
    public static BufferBuilder begin(boolean additive) {
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        if (additive) RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
        else RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(!additive);
        BufferBuilder bb = Tessellator.getInstance().getBuffer();
        bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        return bb;
    }

    public static void end() {
        Tessellator.getInstance().draw();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
    }

    public static void v(BufferBuilder bb, Matrix4f m, double x, double y, double z, int rgb, float a) {
        bb.vertex(m, (float) x, (float) y, (float) z).color((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, a).next();
    }

    public static void quad(BufferBuilder bb, Matrix4f m, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int rgb, float alpha) {
        v(bb, m, a.x, a.y, a.z, rgb, alpha); v(bb, m, b.x, b.y, b.z, rgb, alpha);
        v(bb, m, c.x, c.y, c.z, rgb, alpha); v(bb, m, d.x, d.y, d.z, rgb, alpha);
    }

    public static void box(BufferBuilder bb, Matrix4f m, double x0, double y0, double z0, double x1, double y1, double z1, int rgb, float a) {
        // 6 faces
        quad(bb, m, new Vec3d(x0, y0, z0), new Vec3d(x1, y0, z0), new Vec3d(x1, y1, z0), new Vec3d(x0, y1, z0), rgb, a);
        quad(bb, m, new Vec3d(x0, y0, z1), new Vec3d(x1, y0, z1), new Vec3d(x1, y1, z1), new Vec3d(x0, y1, z1), rgb, a);
        quad(bb, m, new Vec3d(x0, y0, z0), new Vec3d(x0, y0, z1), new Vec3d(x0, y1, z1), new Vec3d(x0, y1, z0), rgb, a);
        quad(bb, m, new Vec3d(x1, y0, z0), new Vec3d(x1, y0, z1), new Vec3d(x1, y1, z1), new Vec3d(x1, y1, z0), rgb, a);
        quad(bb, m, new Vec3d(x0, y0, z0), new Vec3d(x1, y0, z0), new Vec3d(x1, y0, z1), new Vec3d(x0, y0, z1), rgb, a);
        quad(bb, m, new Vec3d(x0, y1, z0), new Vec3d(x1, y1, z0), new Vec3d(x1, y1, z1), new Vec3d(x0, y1, z1), rgb, a);
    }

    /** A glowing beam between two points: two crossed ribbons so it reads from any angle. */
    public static void beam(BufferBuilder bb, Matrix4f m, Vec3d from, Vec3d to, double width, int rgb, float a) {
        Vec3d dir = to.subtract(from);
        if (dir.lengthSquared() < 1e-6) return;
        Vec3d n = dir.normalize();
        Vec3d ref = Math.abs(n.y) > 0.95 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d s1 = n.crossProduct(ref).normalize().multiply(width * 0.5);
        Vec3d s2 = n.crossProduct(s1).normalize().multiply(width * 0.5);
        quad(bb, m, from.add(s1), from.subtract(s1), to.subtract(s1), to.add(s1), rgb, a);
        quad(bb, m, from.add(s2), from.subtract(s2), to.subtract(s2), to.add(s2), rgb, a);
    }

    /** Vertical band ring (a shock-wave wall) around (cx, y, cz). */
    public static void ringWall(BufferBuilder bb, Matrix4f m, double cx, double y, double cz, double radius, double height, double thick, int rgb, float a, int seg) {
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            double r0 = radius, r1 = radius + thick;
            Vec3d p00 = new Vec3d(cx + Math.cos(a0) * r0, y, cz + Math.sin(a0) * r0);
            Vec3d p01 = new Vec3d(cx + Math.cos(a1) * r0, y, cz + Math.sin(a1) * r0);
            Vec3d p10 = new Vec3d(cx + Math.cos(a0) * r1, y, cz + Math.sin(a0) * r1);
            Vec3d p11 = new Vec3d(cx + Math.cos(a1) * r1, y, cz + Math.sin(a1) * r1);
            Vec3d up = new Vec3d(0, height, 0);
            // inner wall + outer wall + top fading (cheap)
            quad(bb, m, p00, p01, p01.add(up), p00.add(up), rgb, a);
            quad(bb, m, p10, p11, p11.add(up), p10.add(up), rgb, a * 0.6f);
        }
    }
}
