package com.astroworld.client;

import com.astroworld.ShowPhase;
import com.astroworld.client.audio.MusicPlayer;
import com.astroworld.client.render.GiantModel;
import com.astroworld.client.render.StageRenderer;
import com.astroworld.config.AstroConfig;
import com.astroworld.net.ShowPackets;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class AstroworldClient implements ClientModInitializer {
    private static KeyBinding volUp, volDown;
    private static float shakeYaw, shakePitch;
    private static final java.util.Random RND = new java.util.Random();

    @Override
    public void onInitializeClient() {
        volUp = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.astroworld.vol_up", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_UP, "key.categories.astroworld"));
        volDown = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.astroworld.vol_down", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_DOWN, "key.categories.astroworld"));

        ClientPlayNetworking.registerGlobalReceiver(ShowPackets.STATE, (client, handler, buf, sender) -> {
            boolean on = buf.readBoolean();
            int tick = buf.readVarInt();
            Vec3d stage = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            float yaw = buf.readFloat();
            Identifier dim = buf.readIdentifier();
            client.execute(() -> {
                if (on && !ShowState.active) { MusicPlayer.resetSession(); MusicPlayer.scan(); ShowState.tick = tick; }
                if (on && Math.abs(ShowState.tick - tick) > 2) ShowState.tick = tick;   // resync only on real drift
                ShowState.active = on;
                ShowState.stage = stage; ShowState.yaw = yaw; ShowState.dimension = dim.toString();
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(AstroworldClient::clientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> { ShowState.active = false; ShowState.atmosphere = 0; MusicPlayer.stop(); });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> {
            if (!ShowState.visible()) return;
            MatrixStack ms = ctx.matrixStack();
            Vec3d cam = ctx.camera().getPos();
            ms.push();
            ms.translate(-cam.x, -cam.y, -cam.z);
            StageRenderer.render(ms, ctx.tickDelta());
            GiantModel.render(ms, ctx.tickDelta());
            ms.pop();
        });

        HudRenderCallback.EVENT.register(AstroworldClient::hud);

        ClientCommandRegistrationCallback.EVENT.register((d, reg) -> d.register(
                ClientCommandManager.literal("astrovol").then(
                        ClientCommandManager.argument("percent", IntegerArgumentType.integer(0, 100)).executes(c -> {
                            AstroConfig.INSTANCE.musicVolume = IntegerArgumentType.getInteger(c, "percent") / 100f;
                            AstroConfig.save();
                            c.getSource().sendFeedback(Text.literal("Astroworld music volume: " + (int) (AstroConfig.INSTANCE.musicVolume * 100) + "%"));
                            return 1;
                        }))));
    }

    // ------------------------------------------------------------------ per tick

    private static void clientTick(MinecraftClient mc) {
        ShowState.tick();
        AstroConfig cfg = AstroConfig.INSTANCE;
        while (volUp.wasPressed()) setVol(mc, cfg.musicVolume + 0.1f);
        while (volDown.wasPressed()) setVol(mc, cfg.musicVolume - 0.1f);

        boolean inShow = ShowState.active && ShowState.atmosphere > 0.5f;
        MusicPlayer.update(inShow ? ShowState.phase() : ShowPhase.IDLE, ShowState.atmosphere);

        if (mc.player == null || mc.world == null) return;
        // undo last frame's shake, apply new one (net drift = 0)
        mc.player.setYaw(mc.player.getYaw() - shakeYaw);
        mc.player.setPitch(mc.player.getPitch() - shakePitch);
        shakeYaw = shakePitch = 0;
        if (!ShowState.visible()) return;

        float pt = 0f;
        ShowPhase ph = ShowState.phase();
        float amp = 0;
        float pulse = ShowState.pulse(pt);
        float pr = ph.progress(ShowState.showTime(pt));
        switch (ph) {
            case PRE_SHOW -> amp = 0.05f + 0.5f * pr * pr;                 // rumbling ground builds up
            case ARRIVAL -> amp = 0.6f + 1.4f * (float) Math.sin(pr * Math.PI);
            case THE_SHOW -> amp = 0.08f + 0.3f * pulse;
            case ASTROWORLD_CHAOS -> amp = 0.5f + 1.3f * pulse;
            case FINALE -> amp = 0.5f * (1 - pr);
            default -> { }
        }
        amp *= cfg.cameraShake * ShowState.atmosphere;
        if (amp > 0.001f) {
            shakeYaw = (RND.nextFloat() - 0.5f) * amp;
            shakePitch = (RND.nextFloat() - 0.5f) * amp;
            mc.player.setYaw(mc.player.getYaw() + shakeYaw);
            mc.player.setPitch(mc.player.getPitch() + shakePitch);
        }
        spawnParticles(mc, ph, pr);
    }

    private static void setVol(MinecraftClient mc, float v) {
        AstroConfig.INSTANCE.musicVolume = MathHelper.clamp(Math.round(v * 10) / 10f, 0f, 1f);
        AstroConfig.save();
        if (mc.player != null) mc.player.sendMessage(Text.literal("Astroworld volume: " + (int) (AstroConfig.INSTANCE.musicVolume * 100) + "%"), true);
    }

    /** Local stage coordinates -> world position. */
    private static Vec3d toWorld(double lx, double ly, double lz) {
        double th = Math.toRadians(ShowState.yaw);
        double c = Math.cos(th), s = Math.sin(th);
        return new Vec3d(ShowState.stage.x + lx * c + lz * s, ShowState.stage.y + ly, ShowState.stage.z - lx * s + lz * c);
    }

    private static void spawnParticles(MinecraftClient mc, ShowPhase ph, float pr) {
        var w = mc.world;
        var r = w.random;
        float pulse = ShowState.pulse(0);
        int t = ShowState.tick;
        // keep particle spam bounded: never more than ~40 per tick
        switch (ph) {
            case PRE_SHOW -> {
                // smoke and embers seep out of the ground where the stage will appear
                for (int i = 0; i < 2 + (int) (8 * pr); i++) {
                    Vec3d p = toWorld((r.nextDouble() - .5) * 90, 0.3, (r.nextDouble() - .5) * 50);
                    w.addParticle(ParticleTypes.LARGE_SMOKE, true, p.x, p.y, p.z, 0, 0.05 + r.nextDouble() * 0.1, 0);
                    if (r.nextInt(3) == 0) w.addParticle(ParticleTypes.LAVA, true, p.x, p.y + 0.5, p.z, 0, 0, 0);
                }
            }
            case ARRIVAL -> {
                float since = (pr - 0.5f) * ShowPhase.ARRIVAL.durationTicks();
                int n = since > 0 && since < 40 ? 30 : 6 + (int) (14 * pr);
                for (int i = 0; i < n; i++) {
                    Vec3d p = toWorld((r.nextDouble() - .5) * 90, 0.3, (r.nextDouble() - .5) * 50);
                    double up = since > 0 && since < 40 ? 1.2 + r.nextDouble() * 1.5 : 0.2 + r.nextDouble() * 0.5;
                    w.addParticle(r.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE, true, p.x, p.y, p.z, (r.nextDouble() - .5) * 0.4, up, (r.nextDouble() - .5) * 0.4);
                }
                if (since > 0 && since < 3) for (int i = 0; i < 6; i++) {
                    Vec3d p = toWorld((r.nextDouble() - .5) * 60, 2, (r.nextDouble() - .5) * 30);
                    w.addParticle(ParticleTypes.EXPLOSION_EMITTER, true, p.x, p.y, p.z, 0, 0, 0);
                }
            }
            case THE_SHOW, ASTROWORLD_CHAOS, FINALE -> {
                boolean wild = ph != ShowPhase.THE_SHOW;
                // smoke machines along the front edge
                if (t % 2 == 0) for (int i = -3; i <= 3; i++) {
                    Vec3d p = toWorld(i * 14, 5.2, 26);
                    w.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, p.x, p.y, p.z, 0, 0.08, 0);
                }
                // sparks / glowing particles that burst on each beat
                if (pulse > 0.85f) for (int i = 0; i < (wild ? 16 : 6); i++) {
                    Vec3d p = toWorld((r.nextDouble() - .5) * 90, 6, 20 + r.nextDouble() * 6);
                    w.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.FIREWORK, true, p.x, p.y, p.z,
                            (r.nextDouble() - .5) * 0.5, 0.4 + r.nextDouble() * 0.6, (r.nextDouble() - .5) * 0.5);
                }
                if (wild && t % 3 == 0) {
                    Vec3d p = toWorld((r.nextDouble() - .5) * 90, 5.5, 27);
                    w.addParticle(ParticleTypes.FLAME, true, p.x, p.y, p.z, 0, 0.5, 0);
                    w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, true, p.x + 2, p.y, p.z, 0, 0.5, 0);
                }
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ HUD (flashes, tint, titles)

    /** Screen-flash intensity 0..1: arrival explosion, chaos start, finale blasts. */
    private static float flash(float pt) {
        float t = ShowState.showTime(pt);
        float f = 0;
        ShowPhase a = ShowPhase.ARRIVAL;
        float sinceArrival = (a.progress(t) - 0.5f) * a.durationTicks();
        if (ShowState.phase() == ShowPhase.ARRIVAL && sinceArrival >= 0) f = Math.max(f, 1f - sinceArrival / 45f);
        float sinceChaos = t - ShowPhase.ASTROWORLD_CHAOS.startTick();
        if (sinceChaos >= 0) f = Math.max(f, 0.85f - sinceChaos / 30f);
        float sinceFinale = t - ShowPhase.FINALE.startTick();
        if (sinceFinale >= 0) {
            f = Math.max(f, 0.9f - sinceFinale / 40f);
            float sinceVanish = sinceFinale - 0.85f * ShowPhase.FINALE.durationTicks();
            if (sinceVanish >= 0) f = Math.max(f, 1f - sinceVanish / 40f);
        }
        return MathHelper.clamp(f, 0f, 1f);
    }

    private static void hud(net.minecraft.client.gui.DrawContext ctx, float pt) {
        if (!ShowState.visible()) return;
        AstroConfig cfg = AstroConfig.INSTANCE;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        float atm = ShowState.atmosphere(pt), e = ShowState.energy(pt), pulse = ShowState.pulse(pt);
        // atmosphere tint: dark red/violet wash that breathes with the kick drum
        int a = (int) (255 * MathHelper.clamp(atm * (0.16f + 0.10f * e + 0.08f * pulse * e), 0, 0.5f));
        int r = 120 + (int) (100 * e), b = 60 + (int) (120 * (1 - e));
        ctx.fill(0, 0, w, h, (a << 24) | (r << 16) | (10 << 8) | b);
        if (cfg.flashes) {
            float f = flash(pt) * atm;
            if (ShowState.phase() == ShowPhase.ASTROWORLD_CHAOS && pulse > 0.8f) f = Math.max(f, 0.10f * atm);   // gentle strobe
            if (f > 0.01f) ctx.fill(0, 0, w, h, ((int) (255 * Math.min(1f, f)) << 24) | 0xFFF2E0);
        }
        // phase title for the first 3 seconds of each phase
        ShowPhase ph = ShowState.phase();
        float since = ShowState.showTime(pt) - ph.startTick();
        if (ph != ShowPhase.IDLE && since < 60) {
            float al = since < 40 ? 1f : 1f - (since - 40) / 20f;
            var mc = MinecraftClient.getInstance();
            String s = ph.name().replace('_', ' ');
            var ms = ctx.getMatrices();
            ms.push();
            ms.translate(w / 2f, h * 0.22f, 0);
            ms.scale(3f, 3f, 1f);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(s).styled(st -> st.withBold(true).withColor(0xFF5A2A)), 0, 0,
                    ((int) (255 * al) << 24) | 0xFFFFFF);
            ms.pop();
        }
    }
}
