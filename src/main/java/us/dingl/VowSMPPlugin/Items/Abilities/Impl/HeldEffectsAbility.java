package us.dingl.VowSMPPlugin.Items.Abilities.Impl;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;
import us.dingl.VowSMPPlugin.Items.Abilities.AbilitySetting;
import us.dingl.VowSMPPlugin.Items.Abilities.ItemAbility;
import us.dingl.VowSMPPlugin.Items.Abilities.ItemEffects;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.List;
import java.util.Map;

/// whoever holds the item (main hand or off hand) gets its potion effects, until it's put away.
/// {@link ItemEffects} does the giving and taking away.
public class HeldEffectsAbility extends ItemAbility {

    static final String EFFECTS = "effects";

    public HeldEffectsAbility(VowSMPPlugin plugin, ItemEffects effects) {
        super(plugin);
        effects.addSource(this::addEffectsFor);
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

    /// the items this ability looks at for this player
    void addEffectsFor(Player player, Map<PotionEffectType, Integer> wanted) {
        PlayerInventory inventory = player.getInventory();
        addEffects(inventory.getItemInMainHand(), wanted);
        addEffects(inventory.getItemInOffHand(), wanted);
    }

    /// two items with the same effect: the higher level wins
    void addEffects(ItemStack item, Map<PotionEffectType, Integer> wanted) {
        Map<String, String> settings = settings(item);
        if (settings == null) return;
        AbilitySetting.Type.parseEffects(settings.get(EFFECTS)).forEach((type, level) -> wanted.merge(type, level, Math::max));
    }
}
