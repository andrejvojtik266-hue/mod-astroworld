package com.astroworld.mixin;

import com.astroworld.client.ShowState;
import com.astroworld.config.AstroConfig;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Re-colours the sky and clouds while the concert is running (client only, fully reversible). */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void astroworld$sky(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        if (!AstroConfig.INSTANCE.skyEffects || !ShowState.visible()) return;
        float a = ShowState.atmosphere(tickDelta);
        Vec3d base = cir.getReturnValue();
        Vec3d show = skyFor(tickDelta);
        cir.setReturnValue(base.multiply(1 - a).add(show.multiply(a)));
    }

    @Inject(method = "getCloudsColor", at = @At("RETURN"), cancellable = true)
    private void astroworld$clouds(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        if (!AstroConfig.INSTANCE.skyEffects || !ShowState.visible()) return;
        float a = ShowState.atmosphere(tickDelta);
        float t = ShowState.time * 0.01f;
        // clouds glow through red -> orange -> violet
        Vec3d c = new Vec3d(
                0.75 + 0.25 * Math.sin(t),
                0.20 + 0.20 * (0.5 + 0.5 * Math.sin(t + 2.0)),
                0.55 + 0.45 * (0.5 + 0.5 * Math.sin(t + 4.0)));
        cir.setReturnValue(cir.getReturnValue().multiply(1 - a).add(c.multiply(a)));
    }

    private static Vec3d skyFor(float pt) {
        float e = ShowState.energy(pt);
        float pulse = ShowState.pulse(pt);
        float t = ShowState.showTime(pt) * 0.02f;
        // base dark red/purple, mixed toward orange/violet with energy
        double r = 0.10 + 0.35 * e + 0.12 * pulse * e + 0.08 * Math.sin(t);
        double g = 0.01 + 0.07 * e * (0.5 + 0.5 * Math.sin(t * 1.3));
        double b = 0.06 + 0.28 * e * (0.5 + 0.5 * Math.sin(t * 0.7 + 1.5)) + 0.06 * pulse * e;
        return new Vec3d(MathHelper.clamp(r, 0, 1), MathHelper.clamp(g, 0, 1), MathHelper.clamp(b, 0, 1));
    }
}
