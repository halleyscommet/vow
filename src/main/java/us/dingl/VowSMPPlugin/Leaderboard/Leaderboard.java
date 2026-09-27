package us.dingl.VowSMPPlugin.Leaderboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Leaderboard {

    private static final String DISPLAY_ID = "vow_leaderboard";

    private final VowSMPPlugin plugin;
    private final NamespacedKey idKey;

    public Leaderboard(VowSMPPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "display_id");
    }

    /// spawns the display, or updates the existing one in place. does nothing if disabled.
    public void refresh() {
        if (!plugin.getLeaderboardEnabled()) return;

        Location block = plugin.getLeaderboardLocation();
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
                entity.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, DISPLAY_ID);
            });
        } else if (!display.getLocation().equals(spawnAt)) {
            display.teleport(spawnAt);
        }

        display.text(buildText());
    }

    /// removes every leaderboard display
    public void hide() {
        Location block = plugin.getLeaderboardLocation();
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
                if (DISPLAY_ID.equals(id)) {
                    result.add(display);
                }
            }
        }
        return result;
    }

    private Component buildText() {
        List<String> alive = names(plugin.getPlayersNotYetKilled());
        List<String> dead = names(plugin.getKilledPlayers());

        Component text = Component.text("The Immortals\n\n", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text(String.join("\n", alive), NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, false));

        if (!dead.isEmpty()) {
            text = text.append(Component.text("\n\n"))
                    .append(Component.text(String.join("\n", dead), NamedTextColor.RED)
                            .decoration(TextDecoration.BOLD, false));
        }
        return text;
    }

    private List<String> names(List<UUID> ids) {
        return ids.stream().map(plugin::getPlayerName).toList();
    }
}