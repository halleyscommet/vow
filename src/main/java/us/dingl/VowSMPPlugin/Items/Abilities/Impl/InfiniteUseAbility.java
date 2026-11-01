package us.dingl.VowSMPPlugin.Items.Abilities.Impl;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import us.dingl.VowSMPPlugin.Items.Abilities.ItemAbility;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

/// the item is never used up: food and potions come back after eating/drinking them, and thrown
/// things (ender pearls, snowballs, wind charges, splash potions...) stay in hand. Pair it with
/// a cooldown, or it can be spammed.
public class InfiniteUseAbility extends ItemAbility {

    public InfiniteUseAbility(VowSMPPlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "infinite_use";
    }

    @Override
    public String name() {
        return "Infinite Use";
    }

    @Override
    public String description() {
        return "Never used up. Food and potions come back after using them, and thrown items stay in hand.";
    }

    @Override
    public Material icon() {
        return Material.ENCHANTED_GOLDEN_APPLE;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!on(item)) return;

        // the eaten one gets swapped for an identical copy (same mythic tag and all)
        ItemStack copy = item.clone();
        copy.setAmount(1);
        event.setReplacement(copy);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onThrow(PlayerLaunchProjectileEvent event) {
        if (on(event.getItemStack())) {
            event.setShouldConsume(false);
        }
    }
}
