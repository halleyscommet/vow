package us.dingl.VowSMPPlugin.Vows.Impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Vow;

/// her max health grows and shrinks: +1 heart per player kill, -1 heart per death (-2 while she's above
/// 10 hearts). Starts at 6 hearts, never below 6 or above 20. Nobody else loses hearts - it isn't real lifesteal.
public class HazelVow extends Vow {

    private static final int START_HEARTS = 6;
    private static final int MIN_HEARTS = 6;
    private static final int MAX_HEARTS = 20;
    /// above this many hearts, a death costs 2 instead of 1
    private static final int DOUBLE_LOSS_ABOVE = 10;
    /// vanilla max health is 10 hearts - the modifier adds or removes the difference
    private static final int BASE_HEARTS = 10;

    /// her heart count, kept in her own data so it survives restarts
    private final NamespacedKey heartsKey;
    private final NamespacedKey healthKey;

    public HazelVow(VowSMPPlugin plugin) {
        super(plugin);
        heartsKey = new NamespacedKey(plugin, "hazel_hearts");
        healthKey = new NamespacedKey(plugin, "hazel_health");
    }

    @Override
    public String id() {
        return "hazel";
    }

    @Override
    public String name() {
        return "Hazel vow";
    }

    @Override
    public String description() {
        return "You start with " + START_HEARTS + " hearts. Every player kill gives you a heart and every death "
                + "costs one, or two while you're above " + DOUBLE_LOSS_ABOVE + " hearts. "
                + "Never fewer than " + MIN_HEARTS + " or more than " + MAX_HEARTS + ".";
    }

    @Override
    public Material icon() {
        return Material.GLISTERING_MELON_SLICE;
    }

    @Override
    public void onGain(Player player) {
        setHearts(player, START_HEARTS);
        player.sendMessage(Component.text("You start with " + START_HEARTS + " hearts. Kill players to gain more.",
                NamedTextColor.RED));
    }

    @Override
    public void onLose(Player player) {
        player.getPersistentDataContainer().remove(heartsKey);
        removeHealthModifier(player);
    }

    @Override
    public void tick(Player player, int lives) {
        // respawning can drop attribute modifiers, so keep putting it back
        applyHealth(player);
    }

    @Override
    public Component actionBarStatus(Player player) {
        return Component.text("Hearts: ", NamedTextColor.GRAY)
                .append(Component.text(getHearts(player) + "/" + MAX_HEARTS, NamedTextColor.RED));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        if (has(victim)) {
            int hearts = getHearts(victim);
            int lost = hearts > DOUBLE_LOSS_ABOVE ? 2 : 1;
            int updated = setHearts(victim, hearts - lost);
            if (updated != hearts) {
                victim.sendMessage(Component.text("You lost " + (hearts - updated) + (hearts - updated == 1 ? " heart" : " hearts")
                        + " (" + updated + "/" + MAX_HEARTS + ").", NamedTextColor.RED));
            }
        }

        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim) && has(killer)) {
            int hearts = getHearts(killer);
            int updated = setHearts(killer, hearts + 1);
            if (updated != hearts) {
                killer.sendMessage(Component.text("You gained a heart (" + updated + "/" + MAX_HEARTS + ").",
                        NamedTextColor.GREEN));
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // the modifier is saved with the player, so it's still there if she lost the vow while offline
        if (!has(event.getPlayer())) {
            removeHealthModifier(event.getPlayer());
        }
    }

    public int getHearts(Player player) {
        return player.getPersistentDataContainer().getOrDefault(heartsKey, PersistentDataType.INTEGER, START_HEARTS);
    }

    /// clamps to MIN-MAX, applies it, and returns what it ended up as
    private int setHearts(Player player, int hearts) {
        int clamped = Math.clamp(hearts, MIN_HEARTS, MAX_HEARTS);
        player.getPersistentDataContainer().set(heartsKey, PersistentDataType.INTEGER, clamped);
        applyHealth(player);
        plugin.getLivesActionBar().update(player.getUniqueId());
        return clamped;
    }

    private void applyHealth(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;

        double amount = (getHearts(player) - BASE_HEARTS) * 2.0;
        AttributeModifier current = maxHealth.getModifier(healthKey);
        if (current != null && current.getAmount() == amount) return;

        if (current != null) maxHealth.removeModifier(healthKey);
        maxHealth.addModifier(new AttributeModifier(healthKey, amount, AttributeModifier.Operation.ADD_NUMBER));
        // losing max health doesn't lower current health on its own until the next damage
        if (player.getHealth() > maxHealth.getValue()) {
            player.setHealth(maxHealth.getValue());
        }
    }

    private void removeHealthModifier(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.removeModifier(healthKey);
        }
    }
}
