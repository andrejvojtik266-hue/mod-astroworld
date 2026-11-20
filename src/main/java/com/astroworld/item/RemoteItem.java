package com.astroworld.item;

import com.astroworld.server.ConcertManager;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/** ASTROWORLD REMOTE: right-click to start, sneak + right-click to end the concert. */
public class RemoteItem extends Item {
    public RemoteItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient || !(player instanceof ServerPlayerEntity sp)) return TypedActionResult.success(stack);

        // Short item cooldown prevents double-click spam from ever racing the manager.
        player.getItemCooldownManager().set(this, 20);

        if (player.isSneaking()) {
            if (ConcertManager.isActive()) {
                ConcertManager.stop(true);
            } else {
                sp.sendMessage(Text.literal("No concert is running.").formatted(Formatting.GRAY), true);
            }
        } else if (ConcertManager.isActive()) {
            sp.sendMessage(Text.literal("A concert is already running. Sneak + use to end it.").formatted(Formatting.YELLOW), true);
        } else if (ConcertManager.start(sp)) {
            world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 2f, 0.6f);
        }
        return TypedActionResult.success(stack);
    }

    @Override
    public boolean hasGlint(ItemStack stack) { return true; }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext ctx) {
        tooltip.add(Text.literal("Use: start the concert").formatted(Formatting.GOLD));
        tooltip.add(Text.literal("Sneak + Use: end the concert").formatted(Formatting.RED));
    }
}
