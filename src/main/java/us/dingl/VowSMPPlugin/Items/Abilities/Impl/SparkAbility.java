package us.dingl.VowSMPPlugin.Items.Abilities.Impl;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import us.dingl.VowSMPPlugin.Items.Abilities.AbilitySetting;
import us.dingl.VowSMPPlugin.Items.Abilities.ItemAbility;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/// right clicking with the item runs commands, separated by ";". They run from the console but as and at
/// the holder ("execute as <them> at @s run ..."), so @s and ~ ~ ~ mean the holder without giving them op.
/// %player% is replaced with their name. If the item also has a Cooldown, it's only run off cooldown,
/// and starts it.
public class SparkAbility extends ItemAbility {

    private static final String COMMANDS = "commands";

    private final CooldownAbility cooldown;
    /// the tick each player last sparked, so one click that fires two interact events only runs once
    private final Map<UUID, Integer> lastTick = new HashMap<>();

    public SparkAbility(VowSMPPlugin plugin, CooldownAbility cooldown) {
        super(plugin);
        this.cooldown = cooldown;
    }

    @Override
    public String id() {
        return "spark";
    }

    @Override
    public String name() {
        return "Spark";
    }

    @Override
    public String description() {
        return "Runs commands as the holder when they right click with it. Separate commands with ; and use "
                + "@s or %player% for the holder.";
    }

    @Override
    public Material icon() {
        return Material.COMMAND_BLOCK;
    }

    @Override
    public List<AbilitySetting> settings() {
        return List.of(new AbilitySetting(COMMANDS, "Commands", AbilitySetting.Type.TEXT,
                "effect give @s haste 10 5 false"));
    }

    // after CooldownAbility's LOW handler, which denies the use if a cooldown hasn't been put back yet
    @EventHandler(priority = EventPriority.NORMAL)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.useItemInHand() == Event.Result.DENY) return;
        ItemStack item = event.getItem();
        Map<String, String> settings = settings(item);
        if (settings == null) return;
        String commands = settings.getOrDefault(COMMANDS, "");
        if (commands.isBlank()) return;

        Player player = event.getPlayer();
        int tick = Bukkit.getCurrentTick();
        Integer last = lastTick.get(player.getUniqueId());
        if (last != null && last == tick) return;
        if (!cooldown.tryUse(player, item)) return;
        lastTick.put(player.getUniqueId(), tick);

        for (String command : commands.split(";")) {
            String cmd = command.trim();
            if (cmd.startsWith("/")) cmd = cmd.substring(1).trim();
            if (cmd.isEmpty()) continue;
            cmd = cmd.replace("%player%", player.getName());
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "execute as " + player.getUniqueId() + " at @s run " + cmd);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Spark command failed: " + cmd, e);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastTick.remove(event.getPlayer().getUniqueId());
    }
}
