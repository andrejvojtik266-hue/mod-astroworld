package com.astroworld;

import com.astroworld.config.AstroConfig;
import com.astroworld.item.RemoteItem;
import com.astroworld.server.ConcertManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import static net.minecraft.server.command.CommandManager.literal;

public class AstroworldMod implements ModInitializer {
    public static final String MOD_ID = "astroworld";
    public static Item REMOTE;

    @Override
    public void onInitialize() {
        AstroConfig.load();
        REMOTE = Registry.register(Registries.ITEM, new Identifier(MOD_ID, "astroworld_remote"),
                new RemoteItem(new FabricItemSettings().maxCount(1).rarity(Rarity.EPIC)));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> e.add(REMOTE));

        ServerTickEvents.END_SERVER_TICK.register(ConcertManager::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> ConcertManager.reset());
        ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> ConcertManager.sendToJoiningPlayer(h.player));

        CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(
                literal("astroworld").requires(s -> s.hasPermissionLevel(2))
                        .then(literal("start").executes(c -> {
                            var p = c.getSource().getPlayer();
                            if (p == null || !ConcertManager.start(p)) {
                                c.getSource().sendError(Text.literal("Cannot start (already running, cooling down, or not a player)."));
                                return 0;
                            }
                            return 1;
                        }))
                        .then(literal("stop").executes(c -> { ConcertManager.stop(true); return 1; }))
                        .then(literal("give").executes(c -> {
                            var p = c.getSource().getPlayer();
                            if (p != null) p.giveItemStack(new net.minecraft.item.ItemStack(REMOTE));
                            return 1;
                        }))));
    }
}
