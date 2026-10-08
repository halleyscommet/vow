package us.dingl.VowSMPPlugin.Vows.Impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Vow;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/// /res gives Resistance V for 5 seconds on a 3 minute cooldown, shown in the action bar.
/// (Her downsides are enforced by admins.)
public class KarmaVow extends Vow {

    private static final int DURATION_TICKS = 5 * 20;
    private static final int COOLDOWN_TICKS = 3 * 60 * 20;
    /// Resistance V - amplifiers start at 0
    private static final int AMPLIFIER = 4;
    /// how often the action bar counts down
    private static final long COOLDOWN_DISPLAY_TICKS = 2L;

    /// server tick she used it, for showing how long it has left
    private final Map<UUID, Integer> usedAt = new HashMap<>();
    /// server tick it's ready again
    private final Map<UUID, Integer> readyAt = new HashMap<>();

    public KarmaVow(VowSMPPlugin plugin) {
        super(plugin);

        // the lives bar only refreshes every 2 seconds, so count the cooldown down ourselves
        Bukkit.getScheduler().runTaskTimer(plugin, this::cooldownTick, COOLDOWN_DISPLAY_TICKS, COOLDOWN_DISPLAY_TICKS);
    }

    @Override
    public String id() {
        return "karma";
    }

    @Override
    public String name() {
        return "Karma vow";
    }

    @Override
    public String description() {
        return "Type /res to get Resistance V for 5 seconds (3 minute cooldown).";
    }

    @Override
    public Material icon() {
        return Material.SHIELD;
    }

    @Override
    public void onLose(Player player) {
        UUID id = player.getUniqueId();
        if (activeTicks(player) > 0) {
            player.removePotionEffect(PotionEffectType.RESISTANCE);
        }
        usedAt.remove(id);
        readyAt.remove(id);
    }

    @Override
    public Component actionBarStatus(Player player) {
        Component label = Component.text("Resistance: ", NamedTextColor.GRAY);
        int active = activeTicks(player);
        if (active > 0) {
            return label.append(Component.text(String.format(Locale.ROOT, "ACTIVE %.1fs", active / 20.0), NamedTextColor.AQUA));
        }
        int remaining = remainingCooldown(player);
        if (remaining <= 0) {
            return label.append(Component.text("READY", NamedTextColor.GREEN));
        }
        int seconds = (remaining + 19) / 20;
        return label.append(Component.text(String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60), NamedTextColor.RED));
    }

    /// returns false (and tells her) if it's still on cooldown
    public boolean activate(Player player) {
        int remaining = remainingCooldown(player);
        if (remaining > 0) {
            int seconds = (remaining + 19) / 20;
            player.sendMessage(Component.text(
                    String.format(Locale.ROOT, "Resistance is on cooldown for %d:%02d.", seconds / 60, seconds % 60),
                    NamedTextColor.RED));
            return false;
        }

        UUID id = player.getUniqueId();
        int now = Bukkit.getCurrentTick();
        usedAt.put(id, now);
        readyAt.put(id, now + COOLDOWN_TICKS);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, DURATION_TICKS, AMPLIFIER));
        plugin.getLivesActionBar().update(id);
        return true;
    }

    private int activeTicks(Player player) {
        Integer used = usedAt.get(player.getUniqueId());
        return used == null ? 0 : used + DURATION_TICKS - Bukkit.getCurrentTick();
    }

    private int remainingCooldown(Player player) {
        Integer ready = readyAt.get(player.getUniqueId());
        return ready == null ? 0 : ready - Bukkit.getCurrentTick();
    }

    private void cooldownTick() {
        int now = Bukkit.getCurrentTick();
        usedAt.values().removeIf(used -> used + DURATION_TICKS <= now);
        // keep each player in the map for one more refresh after it runs out, so the bar flips to READY right away
        readyAt.entrySet().removeIf(entry -> {
            plugin.getLivesActionBar().update(entry.getKey());
            return entry.getValue() <= now;
        });
    }
}
