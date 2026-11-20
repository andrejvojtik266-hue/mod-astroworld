package com.astroworld.client.render;

import com.astroworld.ShowPhase;
import com.astroworld.client.ShowState;
import com.astroworld.config.AstroConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Stage deck, LED screens, truss, lasers, light pylons, halo ring, fire columns and ground shock-waves. */
public final class StageRenderer {
    private StageRenderer() {}

    private static final int DECK_W = 90, DECK_D = 50, DECK_H = 5;

    /** 0..1: the stage deck / screens rising from the ground. */
    public static float deckRise(float pt) {
        if (!ShowState.active) return 0f;
        float t = ShowState.showTime(pt);
        ShowPhase p = ShowPhase.at((int) t);
        float k = switch (p) {
            case PRE_SHOW -> 0f;
            case ARRIVAL -> MathHelper.clamp(p.progress(t) / 0.45f, 0f, 1f);
            case FINALE -> 1f - MathHelper.clamp((p.progress(t) - 0.7f) / 0.3f, 0f, 1f);
            default -> 1f;
        };
        return k * k * (3 - 2 * k);
    }

    public static void render(MatrixStack ms, float pt) {
        AstroConfig cfg = AstroConfig.INSTANCE;
        float t = ShowState.showTime(pt);
        ShowPhase ph = ShowState.phase();
        float e = ShowState.energy(pt);
        float pulse = ShowState.pulse(pt);
        float beat = ShowState.beat(pt);
        float rise = deckRise(pt);
        float atm = ShowState.atmosphere(pt);
        Vec3d st = ShowState.stage;

        // ---- world-aligned effects (pylons around the horizon, shock-waves, arrival column) -------------
        ms.push();
        ms.translate(st.x, st.y, st.z);
        Matrix4f w = ms.peek().getPositionMatrix();
        BufferBuilder add = Gfx.begin(true);
        pylons(add, w, t, e, pulse, ph, atm);
        shockwaves(add, w, ph, t, beat, pt);
        arrivalColumn(add, w, ph, t);
        Gfx.end();
        ms.pop();

        if (ph == ShowPhase.IDLE && atm < 0.05f) return;

        // ---- stage-local (rotated) ---------------------------------------------------------------------
        ms.push();
        ms.translate(st.x, st.y, st.z);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ShowState.yaw));
        Matrix4f m = ms.peek().getPositionMatrix();

        if (rise > 0.001f) {
            // opaque parts first
            BufferBuilder solid = Gfx.begin(false);
            deckAndTruss(solid, m, rise, t, pulse);
            screens(solid, m, rise, t, pulse, e, ph, beat);
            Gfx.end();
        }
        BufferBuilder glow = Gfx.begin(true);
        if (rise > 0.001f) {
            trussGlow(glow, m, rise, t, pulse, e);
            if (cfg.lasers) lasers(glow, m, t, e, pulse, rise, ph);
            skySpots(glow, m, t, e, pulse, rise);
            fire(glow, m, t, beat, ph, rise);
        }
        halo(glow, m, t, e, pulse, ShowState.reveal(pt));
        Gfx.end();
        ms.pop();
    }

    // ------------------------------------------------------------------ opaque

    private static void deckAndTruss(BufferBuilder bb, Matrix4f m, float rise, float t, float pulse) {
        float y = -DECK_H * (1 - rise);
        Gfx.box(bb, m, -DECK_W / 2f, y - 3, -DECK_D / 2f, DECK_W / 2f, y + DECK_H, DECK_D / 2f, 0x16161C, 1f);
        // ramp / catwalk to the audience
        Gfx.box(bb, m, -6, y - 3, DECK_D / 2f, 6, y + DECK_H - 1.5f, DECK_D / 2f + 24, 0x1E1E26, 1f);
        // truss columns
        float h = 62 * rise;
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
            double cx = sx * 50, cz = sz * 22;
            Gfx.box(bb, m, cx - 1.5, DECK_H * rise - 1, cz - 1.5, cx + 1.5, DECK_H * rise + h, cz + 1.5, 0x2A2A33, 1f);
        }
        // light rig bar across the top
        Gfx.box(bb, m, -52, DECK_H * rise + h - 3, -24, 52, DECK_H * rise + h, -20, 0x23232B, 1f);
    }

    private static void screens(BufferBuilder bb, Matrix4f m, float rise, float t, float pulse, float e, ShowPhase ph, float beat) {
        float lift = DECK_H * rise;
        float sink = -(1 - rise) * 50;
        // back wall 80 x 36 and two tall side towers 14 x 44
        ledPanel(bb, m, -40, lift + sink + 6, -24.5f, 80, 36, 20, 9, 0, t, pulse, e, ph, beat);
        ledPanel(bb, m, -66, lift + sink + 2, -10, 14, 44, 4, 11, 1, t, pulse, e, ph, beat);
        ledPanel(bb, m, 52, lift + sink + 2, -10, 14, 44, 4, 11, 2, t, pulse, e, ph, beat);
    }

    /** Animated LED panel on the xy-plane at depth z. Each cell is its own colour -> plasma/stripes/strobe. */
    private static void ledPanel(BufferBuilder bb, Matrix4f m, float x0, float y0, float z, float w, float h,
                                 int cols, int rows, int id, float t, float pulse, float e, ShowPhase ph, float beat) {
        float cw = w / cols, ch = h / rows;
        for (int i = 0; i < cols; i++) for (int j = 0; j < rows; j++) {
            float u = i / (float) cols, v = j / (float) rows;
            float plasma = MathHelper.sin(u * 7 + t * 0.06f + id) + MathHelper.sin(v * 5 - t * 0.05f) + MathHelper.sin((u + v) * 6 + t * 0.03f);
            float hue = 0.02f + 0.12f * (plasma * 0.5f + 0.5f) + (e > 0.9f ? 0.55f * (0.5f + 0.5f * MathHelper.sin(t * 0.04f + u * 4)) : 0f);
            float bar = ((int) (beat * 2) + i + id) % 4 == 0 ? 1f : 0.45f;           // beat-chased stripes
            float val = MathHelper.clamp((0.25f + 0.55f * pulse + 0.25f * bar) * (0.35f + e), 0.05f, 1f);
            if (ph == ShowPhase.PRE_SHOW) { hue = 0.0f; val = 0.15f + 0.1f * MathHelper.sin(t * 0.2f + i); }
            int c = Gfx.hsv(hue, 0.9f, val);
            Gfx.box(bb, m, x0 + i * cw + 0.08f, y0 + j * ch + 0.08f, z - 0.6f, x0 + (i + 1) * cw - 0.08f, y0 + (j + 1) * ch - 0.08f, z, c, 1f);
        }
        // dark bezel
        Gfx.box(bb, m, x0 - 0.6f, y0 - 0.6f, z - 1.2f, x0 + w + 0.6f, y0 + h + 0.6f, z - 0.7f, 0x0B0B0F, 1f);
    }

    // ------------------------------------------------------------------ additive

    private static void trussGlow(BufferBuilder bb, Matrix4f m, float rise, float t, float pulse, float e) {
        float h = 62 * rise;
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
            double cx = sx * 50, cz = sz * 22;
            for (int k = 0; k < 8; k++) {
                double yy = DECK_H * rise + 4 + k * 7.5;
                if (yy > DECK_H * rise + h) break;
                int c = Gfx.hsv(0.02f + 0.1f * ((k + (int) (t / 6)) % 3), 0.9f, 1f);
                Gfx.box(bb, m, cx - 1.9, yy, cz - 1.9, cx + 1.9, yy + 0.6, cz + 1.9, c, 0.35f + 0.6f * pulse);
            }
        }
        int c = Gfx.hsv(0.78f + 0.1f * MathHelper.sin(t * 0.03f), 0.8f, 1f);
        Gfx.box(bb, m, -52, DECK_H * rise + h - 0.2f, -20.2f, 52, DECK_H * rise + h + 0.4f, -19.8f, c, 0.5f + 0.5f * pulse);
        // deck edge strip
        Gfx.box(bb, m, -DECK_W / 2f, DECK_H * rise - 0.3f, DECK_D / 2f - 0.1f, DECK_W / 2f, DECK_H * rise + 0.3f, DECK_D / 2f + 0.3f,
                Gfx.hsv(t * 0.005f, 0.9f, 1f), 0.5f + 0.5f * pulse);
    }

    /** Moving-head laser fans from the rig bar. Count/intensity scale with show energy. */
    private static void lasers(BufferBuilder bb, Matrix4f m, float t, float e, float pulse, float rise, ShowPhase ph) {
        if (ph == ShowPhase.PRE_SHOW) return;
        int n = 4 + (int) (14 * e);
        float y = DECK_H * rise + 62 * rise - 2;
        for (int i = 0; i < n; i++) {
            double x = -48 + 96.0 * i / Math.max(1, n - 1);
            float ph0 = i * 0.7f;
            float az = MathHelper.sin(t * 0.045f * (1 + e) + ph0) * 0.9f;       // sweep left-right
            float el = -0.25f + 0.55f * (0.5f + 0.5f * MathHelper.sin(t * 0.03f + ph0 * 1.3f));
            Vec3d dir = new Vec3d(Math.sin(az) * Math.cos(el), Math.sin(el), Math.cos(az) * Math.cos(el)).normalize();
            Vec3d from = new Vec3d(x, y, -21);
            Vec3d to = from.add(dir.multiply(170));
            int c = Gfx.hsv((i % 3 == 0 ? 0.0f : i % 3 == 1 ? 0.08f : 0.78f) + (e > 0.95f ? t * 0.004f : 0), 1f, 1f);
            Gfx.beam(bb, m, from, to, 0.35 + 0.4 * pulse, c, 0.85f);
            Gfx.beam(bb, m, from, to, 1.6, c, 0.12f + 0.1f * pulse);          // halo
        }
    }

    /** Fat searchlight columns shooting up from the deck. */
    private static void skySpots(BufferBuilder bb, Matrix4f m, float t, float e, float pulse, float rise) {
        int n = 5 + (int) (6 * e);
        for (int i = 0; i < n; i++) {
            double x = -42 + 84.0 * i / Math.max(1, n - 1);
            float sway = MathHelper.sin(t * 0.02f + i) * 0.35f;
            Vec3d from = new Vec3d(x, DECK_H * rise, 12);
            Vec3d to = from.add(new Vec3d(sway * 60, 240, -30 + MathHelper.cos(t * 0.017f + i) * 50));
            int c = Gfx.hsv(0.02f + 0.76f * ((i + (int) (t / 40)) % 2), 0.85f, 1f);
            Gfx.beam(bb, m, from, to, 2.2 + 2.5 * pulse, c, 0.18f + 0.2f * pulse * e);
        }
    }

    private static void fire(BufferBuilder bb, Matrix4f m, float t, float beat, ShowPhase ph, float rise) {
        boolean on = ph == ShowPhase.ASTROWORLD_CHAOS || ph == ShowPhase.FINALE
                || (ph == ShowPhase.THE_SHOW && ((int) beat) % 8 == 0 && (beat - Math.floor(beat)) < 0.9f);
        if (!on) return;
        float k = ph == ShowPhase.THE_SHOW ? 0.6f : 1f;
        for (int i = 0; i < 7; i++) {
            double x = -42 + i * 14;
            float flick = 0.75f + 0.25f * MathHelper.sin(t * 0.9f + i * 2.1f);
            double hgt = (18 + 10 * (float) Math.abs(Math.sin(beat * Math.PI + i))) * k * flick;
            Vec3d base = new Vec3d(x, DECK_H * rise, 27);
            Gfx.beam(bb, m, base, base.add(0, hgt, 0), 5.0, 0xFF3A00, 0.55f);
            Gfx.beam(bb, m, base, base.add(0, hgt * 0.7, 0), 3.0, 0xFFB000, 0.8f);
            Gfx.beam(bb, m, base, base.add(0, hgt * 0.4, 0), 1.6, 0xFFF2B0, 0.9f);
        }
    }

    /** Huge rotating ring behind the giant — a light "portal" visible from far away. */
    private static void halo(BufferBuilder bb, Matrix4f m, float t, float e, float pulse, float reveal) {
        if (reveal < 0.2f) return;
        int seg = 56;
        double R = 36 + 4 * pulse, cy = 44, cz = -30;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg + t * 0.01, a1 = (i + 1) * Math.PI * 2 / seg + t * 0.01;
            Vec3d p0 = new Vec3d(Math.cos(a0) * R, cy + Math.sin(a0) * R, cz);
            Vec3d p1 = new Vec3d(Math.cos(a1) * R, cy + Math.sin(a1) * R, cz);
            int c = Gfx.hsv(0.0f + 0.8f * (i % 14) / 14f * (e > 0.95f ? 1 : 0.15f) + 0.02f, 0.9f, 1f);
            float a = ((i + (int) (t / 2)) % 4 < 2 ? 0.95f : 0.35f) * Math.min(1f, reveal);
            Gfx.beam(bb, m, p0, p1, 1.4 + 1.6 * pulse, c, a);
        }
    }

    // ------------------------------------------------------------------ world-aligned

    /** Ring of colossal light pylons on the horizon, growing through PRE_SHOW and pulsing with the beat. */
    private static void pylons(BufferBuilder bb, Matrix4f m, float t, float e, float pulse, ShowPhase ph, float atm) {
        if (ph == ShowPhase.IDLE && atm < 0.05f) return;
        int n = 14;
        double R = 150;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + t * 0.0015;
            double x = Math.cos(a) * R, z = Math.sin(a) * R;
            double hgt = (50 + 190 * e) * (0.7 + 0.3 * Math.sin(t * 0.03 + i * 1.7));
            int c = Gfx.hsv(i % 2 == 0 ? 0.02f : 0.78f, 0.9f, 1f);
            Vec3d b0 = new Vec3d(x, -10, z);
            Gfx.beam(bb, m, b0, b0.add(0, hgt, 0), 4 + 8 * pulse * e, c, 0.14f + 0.2f * e);
            Gfx.beam(bb, m, b0, b0.add(0, hgt, 0), 1.2, 0xFFFFFF, 0.25f + 0.35f * e);
        }
        // pre-show: a single thin red beam straight up at the stage that thickens with tension
        if (ph == ShowPhase.PRE_SHOW) {
            float pr = ph.progress(t);
            Gfx.beam(bb, m, new Vec3d(0, -5, 0), new Vec3d(0, 300, 0), 1 + 7 * pr, 0xFF2200, 0.25f + 0.4f * pr);
        }
    }

    /** Expanding ground shock-waves on strong beats (every 4 beats in the show, every 2 in chaos/finale). */
    private static void shockwaves(BufferBuilder bb, Matrix4f m, ShowPhase ph, float t, float beat, float pt) {
        if (ph != ShowPhase.THE_SHOW && ph != ShowPhase.ASTROWORLD_CHAOS && ph != ShowPhase.FINALE && ph != ShowPhase.ARRIVAL) return;
        int every = ph == ShowPhase.THE_SHOW ? 4 : 2;
        int last = (int) Math.floor(beat / every);
        for (int k = 0; k < 3; k++) {
            int idx = last - k;
            if (idx < 0) break;
            float ageBeats = beat - idx * every;
            float ageTicks = ageBeats * 60f / (float) AstroConfig.INSTANCE.bpm * 20f;
            float r = 6 + ageTicks * 3.2f;
            float fade = 1f - MathHelper.clamp(ageTicks / 90f, 0, 1);
            if (fade <= 0) continue;
            int c = Gfx.hsv(0.02f + 0.08f * ((idx + k) % 3) + (ph == ShowPhase.ASTROWORLD_CHAOS ? 0.5f * (idx % 2) : 0), 0.9f, 1f);
            Gfx.ringWall(bb, m, 0, 0.2, 0, r, 2.5 + 4 * fade, 2.2, c, 0.7f * fade, 72);
        }
    }

    /** The "explosion of light" at the moment of arrival: a white column plus a fast massive wave. */
    private static void arrivalColumn(BufferBuilder bb, Matrix4f m, ShowPhase ph, float t) {
        if (ph != ShowPhase.ARRIVAL) return;
        float since = (ph.progress(t) - 0.5f) * ph.durationTicks();      // ticks since the flash
        if (since < 0 || since > 80) return;
        float f = 1f - since / 80f;
        Gfx.beam(bb, m, new Vec3d(0, -5, 0), new Vec3d(0, 400, 0), 14 * f + 2, 0xFFFFFF, 0.8f * f);
        Gfx.beam(bb, m, new Vec3d(0, -5, 0), new Vec3d(0, 400, 0), 30 * f + 4, 0xFF6A00, 0.3f * f);
        Gfx.ringWall(bb, m, 0, 0.2, 0, since * 5.5f, 10 * f, 5, 0xFFFFFF, 0.8f * f, 96);
    }
}
