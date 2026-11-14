package us.dingl.VowSMPPlugin.Items.Abilities.Impl;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import us.dingl.VowSMPPlugin.Items.Abilities.AbilitySetting;
import us.dingl.VowSMPPlugin.Items.Abilities.ItemAbility;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// whoever holds the item (main hand or off hand) gets its potion effects. They're topped up every second
/// like the vows' passive effects, and taken away as soon as the item is put away.
public class HeldEffectsAbility extends ItemAbility {

    private static final String EFFECTS = "effects";
    private static final long CHECK_TICKS = 10L;
    private static final int EFFECT_TICKS = 40;
    /// night vision starts flashing under 10 seconds, so it gets longer - it's taken off when put away anyway
    private static final int NIGHT_VISION_TICKS = 15 * 20;

    /// what each player got from held items last check, so it can be taken off once they stop holding it
    private final Map<UUID, Set<PotionEffectType>> given = new HashMap<>();

    public HeldEffectsAbility(VowSMPPlugin plugin) {
        super(plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::check, CHECK_TICKS, CHECK_TICKS);
    }

    @Override
    public String id() {
        return "held_effects";
    }

    @Override
    public String name() {
        return "Held Effects";
    }

    @Override
    public String description() {
        return "Gives potion effects while it's held in the main hand or off hand.";
    }

    @Override
    public Material icon() {
        return Material.POTION;
    }

    @Override
    public List<AbilitySetting> settings() {
        return List.of(new AbilitySetting(EFFECTS, "Effects", AbilitySetting.Type.EFFECTS, "speed:1"));
    }

    private void check() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<PotionEffectType, Integer> wanted = new HashMap<>();
            PlayerInventory inventory = player.getInventory();
            addEffects(inventory.getItemInMainHand(), wanted);
            addEffects(inventory.getItemInOffHand(), wanted);

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

    /// two held items with the same effect: the higher level wins
    private void addEffects(ItemStack item, Map<PotionEffectType, Integer> wanted) {
        Map<String, String> settings = settings(item);
        if (settings == null) return;
        AbilitySetting.Type.parseEffects(settings.get(EFFECTS)).forEach((type, level) -> wanted.merge(type, level, Math::max));
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
