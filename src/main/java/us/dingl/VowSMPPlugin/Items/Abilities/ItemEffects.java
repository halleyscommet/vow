package us.dingl.VowSMPPlugin.Items.Abilities;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/// gives players the potion effects their items ask for (Held Effects, Inventory Effects). They're topped
/// up every half second like the vows' passive effects, and taken away as soon as no item gives them.
///
/// Every effect ability adds itself as a source here instead of running its own timer, so one ability
/// never takes off an effect another one is still giving.
public final class ItemEffects implements Listener {

    private static final long CHECK_TICKS = 10L;
    private static final int EFFECT_TICKS = 40;
    /// night vision starts flashing under 10 seconds, so it gets longer - it's taken off when it stops anyway
    private static final int NIGHT_VISION_TICKS = 15 * 20;

    /// each one adds the effects (type -> level) its items give this player
    private final List<BiConsumer<Player, Map<PotionEffectType, Integer>>> sources = new ArrayList<>();
    /// what each player got from items last check, so it can be taken off once nothing gives it
    private final Map<UUID, Set<PotionEffectType>> given = new HashMap<>();

    public ItemEffects(VowSMPPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::check, CHECK_TICKS, CHECK_TICKS);
    }

    /// two sources (or items) with the same effect: merge with Math::max so the higher level wins
    public void addSource(BiConsumer<Player, Map<PotionEffectType, Integer>> source) {
        sources.add(source);
    }

    private void check() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<PotionEffectType, Integer> wanted = new HashMap<>();
            sources.forEach(source -> source.accept(player, wanted));

            Set<PotionEffectType> before = given.getOrDefault(player.getUniqueId(), Set.of());
            for (PotionEffectType type : before) {
                if (!wanted.containsKey(type)) removeOurs(player, type);
            }

            if (wanted.isEmpty()) {
                given.remove(player.getUniqueId());
                continue;
            }
            wanted.forEach((type, level) -> {
                int ticks = type.equals(PotionEffectType.NIGHT_VISION) ? NIGHT_VISION_TICKS : EFFECT_TICKS;
                // never replaces a stronger or longer effect they already have (like a potion they drank)
                player.addPotionEffect(new PotionEffect(type, ticks, level - 1, true, false, true));
            });
            given.put(player.getUniqueId(), new HashSet<>(wanted.keySet()));
        }
    }

    /// only takes it off if it looks like ours - potions, beacons and the like are left alone
    private static void removeOurs(Player player, PotionEffectType type) {
        PotionEffect current = player.getPotionEffect(type);
        if (current == null || !current.isAmbient() || current.hasParticles()) return;
        int max = type.equals(PotionEffectType.NIGHT_VISION) ? NIGHT_VISION_TICKS : EFFECT_TICKS;
        if (current.getDuration() <= max) player.removePotionEffect(type);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        given.remove(event.getPlayer().getUniqueId());
    }
}
