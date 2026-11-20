package com.astroworld.client;

import com.astroworld.ShowPhase;
import com.astroworld.config.AstroConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Client-side mirror of the server's concert state, extrapolated every client tick. */
public final class ShowState {
    public static boolean active;
    public static int tick;                 // show tick (synced, then extrapolated)
    public static Vec3d stage = Vec3d.ZERO;
    public static float yaw;
    public static String dimension = "minecraft:overworld";
    /** 0..1 how strongly the world is transformed; fades in at start and out after the end. */
    public static float atmosphere;
    private static float prevAtmosphere;
    public static float time;               // ever-increasing client clock in ticks (for ambient animation)

    private ShowState() {}

    public static void tick() {
        time++;
        prevAtmosphere = atmosphere;
        var mc = MinecraftClient.getInstance();
        boolean inRange = false;
        if (mc.world != null && mc.player != null && active
                && mc.world.getRegistryKey().getValue().toString().equals(dimension)) {
            double r = AstroConfig.INSTANCE.effectRadius + AstroConfig.INSTANCE.stageDistance;
            inRange = mc.player.getPos().squaredDistanceTo(stage) < r * r;
        }
        if (active) tick++;
        float target = inRange ? 1f : 0f;
        // slow fade in (6s), slow fade out (10s) -> "world gradually returns to normal"
        float speed = target > atmosphere ? 1f / 120f : 1f / 200f;
        atmosphere = MathHelper.stepTowards(atmosphere, target, speed);
        if (!active && atmosphere <= 0f) { tick = 0; }
    }

    public static float atmosphere(float pt) { return MathHelper.lerp(pt, prevAtmosphere, atmosphere); }
    public static float showTime(float pt) { return tick + (active ? pt : 0f); }
    public static boolean visible() { return atmosphere > 0.001f; }

    public static ShowPhase phase() { return active ? ShowPhase.at(tick) : ShowPhase.IDLE; }

    /** Beat position in beats since show start (fractional). */
    public static float beat(float pt) {
        return (float) (showTime(pt) / 20.0 * AstroConfig.INSTANCE.bpm / 60.0);
    }
    /** 1 on the beat, decaying to 0 – the kick-drum pulse used by everything. */
    public static float pulse(float pt) {
        float b = beat(pt);
        float f = b - (float) Math.floor(b);
        return (float) Math.pow(1.0 - f, 3.0);
    }
    /** Overall energy 0..1 per phase – drives how wild the effects are. */
    public static float energy(float pt) {
        float t = showTime(pt);
        ShowPhase p = ShowPhase.at((int) t);
        return switch (p) {
            case PRE_SHOW -> 0.15f + 0.35f * p.progress(t);
            case ARRIVAL -> 0.5f + 0.4f * p.progress(t);
            case THE_SHOW -> 0.6f + 0.15f * (float) Math.sin(t * 0.01);
            case ASTROWORLD_CHAOS -> 1f;
            case FINALE -> 1f - 0.2f * p.progress(t);
            default -> 0f;
        };
    }
    /** 0..1: how "revealed" the giant is. Silhouette during pre-show, full at the arrival flash. */
    public static float reveal(float pt) {
        float t = showTime(pt);
        ShowPhase p = ShowPhase.at((int) t);
        if (!active) return 0f;
        return switch (p) {
            case PRE_SHOW -> 0.25f * p.progress(t);
            case ARRIVAL -> 0.25f + 0.75f * MathHelper.clamp((p.progress(t) - 0.35f) / 0.4f, 0f, 1f);
            case FINALE -> 1f - MathHelper.clamp((p.progress(t) - 0.7f) / 0.3f, 0f, 1f);
            default -> 1f;
        };
    }
    /** 0 = black silhouette, 1 = full colour (switches at the arrival flash). */
    public static float unveil(float pt) {
        float t = showTime(pt);
        ShowPhase p = ShowPhase.at((int) t);
        if (!active) return 0f;
        return switch (p) {
            case PRE_SHOW -> 0f;
            case ARRIVAL -> MathHelper.clamp((p.progress(t) - 0.5f) / 0.08f, 0f, 1f);
            default -> 1f;
        };
    }
    /** Fraction of the stage deck / screens that have risen out of the ground. */
    public static float rise(float pt) {
        float t = showTime(pt);
        ShowPhase p = ShowPhase.at((int) t);
        return switch (p) {
            case PRE_SHOW -> 0f;
            case ARRIVAL -> MathHelper.clamp((p.progress(t) - 0.35f) / 0.5f, 0f, 1f);
            case FINALE -> 1f - 0.0f;
            default -> 1f;
        };
    }
}
