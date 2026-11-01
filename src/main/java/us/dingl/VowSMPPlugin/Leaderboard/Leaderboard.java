package us.dingl.VowSMPPlugin.Leaderboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import us.dingl.VowSMPPlugin.ConfigKey;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/// a floating text display in the world. Each board has its own on/off switch and position in config,
/// and is off by default - whatever it tracks should only be counted while it's on.
public final class Leaderboard {

    private final VowSMPPlugin plugin;
    private final NamespacedKey idKey;
    private final String displayId;
    private final ConfigKey enabledKey;
    private final ConfigKey xKey;
    private final ConfigKey yKey;
    private final ConfigKey zKey;
    private final Function<VowSMPPlugin, Component> text;

    private Leaderboard(VowSMPPlugin plugin, String displayId, ConfigKey enabledKey,
                        ConfigKey xKey, ConfigKey yKey, ConfigKey zKey, Function<VowSMPPlugin, Component> text) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "display_id");
        this.displayId = displayId;
        this.enabledKey = enabledKey;
        this.xKey = xKey;
        this.yKey = yKey;
        this.zKey = zKey;
        this.text = text;
    }

    /// players who haven't died yet, then (in red) the ones who have
    public static Leaderboard immortals(VowSMPPlugin plugin) {
        return new Leaderboard(plugin, "vow_leaderboard", ConfigKey.LEADERBOARD_ENABLED,
                ConfigKey.LEADERBOARD_X, ConfigKey.LEADERBOARD_Y, ConfigKey.LEADERBOARD_Z, Leaderboard::immortalsText);
    }

    /// players with at least one kill, most kills first
    public static Leaderboard kills(VowSMPPlugin plugin) {
        return new Leaderboard(plugin, "vow_kills_leaderboard", ConfigKey.KILLS_LEADERBOARD_ENABLED,
                ConfigKey.KILLS_LEADERBOARD_X, ConfigKey.KILLS_LEADERBOARD_Y, ConfigKey.KILLS_LEADERBOARD_Z, Leaderboard::killsText);
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean(enabledKey.getPath(), false);
    }

    /// flips the board on/off and spawns/removes the display. Returns the new state.
    public boolean toggle() {
        boolean enabled = !isEnabled();
        plugin.getConfig().set(enabledKey.getPath(), enabled);
        plugin.saveConfig();
        if (enabled) {
            refresh();
        } else {
            hide();
        }
        return enabled;
    }

    /// the block position stored in config, or world spawn if none was ever set.
    /// null only if no worlds are loaded.
    public Location getLocation() {
        if (Bukkit.getWorlds().isEmpty()) {
            return null;
        }
        World world = Bukkit.getWorlds().getFirst();
        FileConfiguration config = plugin.getConfig();

        if (!config.isSet(xKey.getPath()) || !config.isSet(yKey.getPath()) || !config.isSet(zKey.getPath())) {
            return world.getSpawnLocation();
        }

        int x = config.getInt(xKey.getPath());
        int y = Math.clamp(config.getInt(yKey.getPath()), world.getMinHeight(), world.getMaxHeight());
        int z = config.getInt(zKey.getPath());
        return new Location(world, x, y, z);
    }

    /// saves the new position (one disk write) and moves the display if it's enabled.
    public void setPosition(int x, int y, int z) {
        if (isEnabled()) {
            hide(); // config still points at the OLD spot, so its chunk gets loaded and cleaned
        }

        plugin.getConfig().set(xKey.getPath(), x);
        plugin.getConfig().set(yKey.getPath(), y);
        plugin.getConfig().set(zKey.getPath(), z);
        plugin.saveConfig();

        refresh();
    }

    /// spawns the display, or updates the existing one in place. does nothing if disabled.
    public void refresh() {
        if (!isEnabled()) return;

        Location block = getLocation();
        if (block == null) return;

        World world = block.getWorld();
        Location spawnAt = block.clone().add(0.5, 0.5, 0.5);
        world.getChunkAt(spawnAt); // make sure the chunk (and any old display in it) is loaded

        TextDisplay display = null;
        for (TextDisplay existing : findDisplays()) {
            if (display == null && existing.getWorld().equals(world)) {
                display = existing;
            } else {
                existing.remove(); // duplicates
            }
        }

        if (display == null) {
            display = world.spawn(spawnAt, TextDisplay.class, entity -> {
                entity.setBillboard(Display.Billboard.VERTICAL);
                entity.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, displayId);
            });
        } else if (!display.getLocation().equals(spawnAt)) {
            display.teleport(spawnAt);
        }

        display.text(text.apply(plugin));
    }

    /// removes every display of this board
    public void hide() {
        Location block = getLocation();
        if (block != null) {
            block.getWorld().getChunkAt(block); // unloaded chunks aren't searched
        }
        for (TextDisplay display : findDisplays()) {
            display.remove();
        }
    }

    private List<TextDisplay> findDisplays() {
        List<TextDisplay> result = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                String id = display.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
                if (displayId.equals(id)) {
                    result.add(display);
                }
            }
        }
        return result;
    }

    private static Component immortalsText(VowSMPPlugin plugin) {
        List<String> alive = plugin.getPlayersNotYetKilled().stream().map(plugin::getPlayerName).toList();
        List<String> dead = plugin.getKilledPlayers().stream().map(plugin::getPlayerName).toList();

        Component text = title("The Immortals").append(body(alive, NamedTextColor.WHITE));
        if (!dead.isEmpty()) {
            text = text.append(Component.text("\n\n")).append(body(dead, NamedTextColor.RED));
        }
        return text;
    }

    private static Component killsText(VowSMPPlugin plugin) {
        List<String> lines = plugin.getPlayerKills().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed()
                        .thenComparing(entry -> plugin.getPlayerName(entry.getKey()).toLowerCase()))
                .map(entry -> plugin.getPlayerName(entry.getKey()) + ": " + entry.getValue())
                .toList();
        return title("Most Kills").append(body(lines, NamedTextColor.WHITE));
    }

    private static Component title(String title) {
        return Component.text(title + "\n\n", NamedTextColor.GREEN, TextDecoration.BOLD);
    }

    private static Component body(List<String> lines, NamedTextColor color) {
        return Component.text(String.join("\n", lines), color).decoration(TextDecoration.BOLD, false);
    }
}
