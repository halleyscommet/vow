package us.dingl.VowSMPPlugin.Items.Abilities.Impl;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.UseCooldown;
import io.papermc.paper.event.player.PlayerItemGroupCooldownEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import us.dingl.VowSMPPlugin.ConfigKey;
import us.dingl.VowSMPPlugin.Items.Abilities.AbilitySetting;
import us.dingl.VowSMPPlugin.Items.Abilities.ItemAbility;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// the item can only be used once every so often. Uses vanilla's use_cooldown component, so the client
/// shows the cooldown sweep over the item, with a cooldown group of its own so other items of the same
/// type aren't affected.
///
/// Vanilla forgets cooldowns when a player logs out, and they're per player. So every time one starts,
/// it's written down here (and to config) by the item's group, and anyone holding the item gets the rest
/// of it put back - after relogging, a restart, or handing the item to someone else.
public class CooldownAbility extends ItemAbility {

    private static final String LENGTH = "length";
    /// the item's own cooldown group id, made on apply
    private static final String GROUP = "group";
    private static final String GROUP_PREFIX = "cooldown_";

    /// cooldown group key -> when it ends (epoch millis)
    private final Map<String, Long> endsAt = new HashMap<>();
    private final String namespace;

    public CooldownAbility(VowSMPPlugin plugin) {
        super(plugin);
        this.namespace = new NamespacedKey(plugin, "x").getNamespace();
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::restoreCooldowns, 20L, 20L);
    }

    @Override
    public String id() {
        return "cooldown";
    }

    @Override
    public String name() {
        return "Cooldown";
    }

    @Override
    public String description() {
        return "Can only be used once every so often. Sticks to the item through relogs, restarts and trades.";
    }

    @Override
    public Material icon() {
        return Material.CLOCK;
    }

    @Override
    public List<AbilitySetting> settings() {
        return List.of(new AbilitySetting(LENGTH, "Cooldown length", AbilitySetting.Type.DURATION, "600"));
    }

    @Override
    public void onApply(ItemStack item, Map<String, String> settings) {
        settings.computeIfAbsent(GROUP, k -> UUID.randomUUID().toString());
        long seconds = Long.parseLong(settings.get(LENGTH));
        item.setData(DataComponentTypes.USE_COOLDOWN, UseCooldown.useCooldown(seconds)
                .cooldownGroup(groupKey(settings))
                .build());
    }

    @Override
    public void onRemove(ItemStack item) {
        item.resetData(DataComponentTypes.USE_COOLDOWN);
    }

    /// vanilla starting the cooldown - note when it ends
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCooldown(PlayerItemGroupCooldownEvent event) {
        NamespacedKey group = event.getCooldownGroup();
        if (!isOurs(group)) return;
        record(group.getKey(), event.getCooldown());
    }

    /// the cooldown hasn't been put back on this player yet (they just joined, or just got the item)
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (stillCooling(event.getPlayer(), event.getItem())) {
            event.setCancelled(true);
        }
    }

    /// vanilla should start the cooldown itself once it's eaten - this makes sure, in case it didn't
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsumed(PlayerItemConsumeEvent event) {
        Map<String, String> settings = settings(event.getItem());
        if (settings == null || !settings.containsKey(GROUP)) return;

        Player player = event.getPlayer();
        NamespacedKey group = groupKey(settings);
        int ticks = (int) Math.min(Integer.MAX_VALUE, Long.parseLong(settings.get(LENGTH)) * 20);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.getCooldown(group) > 0) return;
            player.setCooldown(group, ticks);
            record(group.getKey(), ticks);
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (stillCooling(event.getPlayer(), event.getItem())) {
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    /// true (and shows the cooldown) if this item is one of ours and hasn't come off cooldown yet
    private boolean stillCooling(Player player, ItemStack item) {
        Map<String, String> settings = settings(item);
        if (settings == null || !settings.containsKey(GROUP)) return false;

        NamespacedKey group = groupKey(settings);
        int remaining = remainingTicks(group.getKey());
        if (remaining <= 0) return false;
        player.setCooldown(group, remaining);
        return true;
    }

    /// once a second: anyone holding an item that's on cooldown gets the cooldown, if they don't have it
    private void restoreCooldowns() {
        if (endsAt.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (endsAt.values().removeIf(end -> end <= now)) save();

        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : player.getInventory().getContents()) {
                Map<String, String> settings = settings(item);
                if (settings == null || !settings.containsKey(GROUP)) continue;

                NamespacedKey group = groupKey(settings);
                int remaining = remainingTicks(group.getKey());
                // a second of slack so this doesn't keep resetting the client's sweep animation
                if (remaining > 20 && player.getCooldown(group) < remaining - 20) {
                    player.setCooldown(group, remaining);
                }
            }
        }
    }

    private int remainingTicks(String group) {
        Long end = endsAt.get(group);
        if (end == null) return 0;
        return (int) Math.max(0, (end - System.currentTimeMillis()) / 50);
    }

    private void record(String group, int ticks) {
        if (ticks <= 0) {
            endsAt.remove(group);
        } else {
            endsAt.put(group, System.currentTimeMillis() + ticks * 50L);
        }
        save();
    }

    private NamespacedKey groupKey(Map<String, String> settings) {
        return new NamespacedKey(plugin, GROUP_PREFIX + settings.get(GROUP));
    }

    private boolean isOurs(NamespacedKey group) {
        return group.getNamespace().equals(namespace) && group.getKey().startsWith(GROUP_PREFIX);
    }

    // config: item-cooldowns.<group key> = epoch millis it ends

    private void load() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(ConfigKey.ITEM_COOLDOWNS.getPath());
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            endsAt.put(key, section.getLong(key));
        }
    }

    private void save() {
        plugin.getConfig().set(ConfigKey.ITEM_COOLDOWNS.getPath(), null);
        endsAt.forEach((group, end) -> plugin.getConfig().set(ConfigKey.ITEM_COOLDOWNS.getPath() + "." + group, end));
        plugin.saveConfig();
    }
}
