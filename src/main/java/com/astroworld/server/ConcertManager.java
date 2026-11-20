package com.astroworld.server;

import com.astroworld.ShowPhase;
import com.astroworld.config.AstroConfig;
import com.astroworld.net.ShowPackets;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

/**
 * Server-side authority for the single active concert. Only ONE concert can run at a time.
 * All visuals are rendered client-side from the synced state, so the world's blocks are never
 * modified and nothing needs to be "restored" afterwards.
 */
public final class ConcertManager {
    private static boolean active = false;
    private static int showTick = 0;
    private static ServerWorld world;
    private static Vec3d stage = Vec3d.ZERO;
    private static float stageYaw = 0f;       // degrees; model local +Z rotated by this about Y faces the audience
    private static int cooldown = 0;
    private static ShowPhase lastPhase = ShowPhase.IDLE;

    private ConcertManager() {}

    public static boolean isActive() { return active; }

    /** @return true if a new concert started, false if one is already running / cooling down. */
    public static boolean start(ServerPlayerEntity starter) {
        if (active) return false;
        if (cooldown > 0) {
            starter.sendMessage(Text.literal("The stage is still cooling down...").formatted(Formatting.GRAY), true);
            return false;
        }
        AstroConfig cfg = AstroConfig.INSTANCE;
        world = starter.getServerWorld();
        Vec3d look = starter.getRotationVec(1f);
        Vec3d dir = new Vec3d(look.x, 0, look.z);
        if (dir.lengthSquared() < 1e-4) dir = new Vec3d(0, 0, 1);
        dir = dir.normalize();
        double sx = starter.getX() + dir.x * cfg.stageDistance;
        double sz = starter.getZ() + dir.z * cfg.stageDistance;
        int gy = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) sx, (int) sz);
        double sy = Math.max(world.getBottomY() + 1, gy);
        stage = new Vec3d(sx, sy, sz);
        // face the audience: yaw such that the front looks toward the starter
        stageYaw = (float) Math.toDegrees(Math.atan2(starter.getX() - sx, starter.getZ() - sz));
        showTick = 0;
        active = true;
        lastPhase = ShowPhase.IDLE;
        broadcast(true);
        broadcastChat(Text.literal("★ ASTROWORLD ★ — " + starter.getName().getString() + " activated the remote.")
                .formatted(Formatting.GOLD, Formatting.BOLD));
        return true;
    }

    public static void stop(boolean aborted) {
        if (!active) return;
        active = false;
        cooldown = 20 * AstroConfig.INSTANCE.cooldownSeconds;
        broadcast(false);
        broadcastChat(Text.literal(aborted ? "The concert was ended." : "Thank you, Astroworld! See you next time.")
                .formatted(Formatting.LIGHT_PURPLE));
        world = null;
        lastPhase = ShowPhase.IDLE;
    }

    public static void tick(MinecraftServer server) {
        if (cooldown > 0) cooldown--;
        if (!active || world == null) return;
        showTick++;
        ShowPhase phase = ShowPhase.at(showTick);
        if (phase == ShowPhase.IDLE) { stop(false); return; }
        if (phase != lastPhase) {
            lastPhase = phase;
            broadcast(true);
            broadcastChat(Text.literal("— " + phase.name().replace('_', ' ') + " —").formatted(Formatting.RED));
        } else if (showTick % 20 == 0) {
            broadcast(true);   // periodic resync so clients never drift
        }
        serverEffects(phase);
    }

    /** Only a few real, cosmetic world effects: lightning (no fire/damage) and fireworks. */
    private static void serverEffects(ShowPhase phase) {
        var rnd = world.random;
        double r = 60;
        if ((phase == ShowPhase.PRE_SHOW || phase == ShowPhase.ASTROWORLD_CHAOS) && showTick % (phase == ShowPhase.PRE_SHOW ? 50 : 14) == 0) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 40 + rnd.nextDouble() * r;
            BlockPos p = BlockPos.ofFloored(stage.x + Math.cos(a) * d, stage.y, stage.z + Math.sin(a) * d);
            LightningEntity l = EntityType.LIGHTNING_BOLT.create(world);
            if (l != null) {
                l.refreshPositionAfterTeleport(Vec3d.ofBottomCenter(world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, p)));
                l.setCosmetic(true);
                world.spawnEntity(l);
            }
        }
        boolean fw = phase == ShowPhase.FINALE ? showTick % 8 == 0 : phase == ShowPhase.ASTROWORLD_CHAOS && showTick % 30 == 0;
        if (fw) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 15 + rnd.nextDouble() * 30;
            ItemStack s = new ItemStack(Items.FIREWORK_ROCKET);
            NbtCompound fwTag = s.getOrCreateSubNbt("Fireworks");
            fwTag.putByte("Flight", (byte) (1 + rnd.nextInt(2)));
            NbtList ex = new NbtList();
            NbtCompound e = new NbtCompound();
            e.putByte("Type", (byte) FireworkRocketItem.Type.LARGE_BALL.getId());
            e.putIntArray("Colors", new int[]{0xFF2A00, 0xFF9A00, 0x9B30FF});
            e.putBoolean("Flicker", true);
            ex.add(e);
            fwTag.put("Explosions", ex);
            world.spawnEntity(new FireworkRocketEntity(world, stage.x + Math.cos(a) * d, stage.y + 2, stage.z + Math.sin(a) * d, s));
        }
    }

    public static void sendToJoiningPlayer(ServerPlayerEntity p) {
        if (active) ServerPlayNetworking.send(p, ShowPackets.STATE, buf(true));
    }

    private static PacketByteBuf buf(boolean on) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeBoolean(on);
        b.writeVarInt(showTick);
        b.writeDouble(stage.x); b.writeDouble(stage.y); b.writeDouble(stage.z);
        b.writeFloat(stageYaw);
        b.writeIdentifier(world != null ? world.getRegistryKey().getValue() : new net.minecraft.util.Identifier("overworld"));
        return b;
    }

    private static void broadcast(boolean on) {
        if (world == null && on) return;
        var server = world != null ? world.getServer() : null;
        if (server == null) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, ShowPackets.STATE, buf(on));
        }
    }

    private static void broadcastChat(Text t) {
        if (world == null) return;
        for (ServerPlayerEntity p : world.getServer().getPlayerManager().getPlayerList()) p.sendMessage(t, false);
    }

    public static void reset() { active = false; world = null; cooldown = 0; showTick = 0; }
}
