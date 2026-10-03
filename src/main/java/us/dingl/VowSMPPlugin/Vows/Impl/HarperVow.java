package us.dingl.VowSMPPlugin.Vows.Impl;

import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Vow;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/// double jump (double tap space in the air) on a 5 second cooldown, shown in the action bar.
/// In exchange, she has 8 hearts instead of 10, can't use bows or crossbows, and can never be invisible.
/// (Traps are also banned, but admins enforce that.)
public class HarperVow extends Vow {

    private static final double JUMP_UP = 0.65;
    private static final double JUMP_FORWARD = 0.7;
    private static final int COOLDOWN_TICKS = 5 * 20;
    /// both presses have to land this close together, same as vanilla's double tap to fly
    private static final int DOUBLE_TAP_TICKS = 7;
    /// how often the cooldown in the action bar counts down
    private static final long COOLDOWN_DISPLAY_TICKS = 2L;

    private static final double HEALTH_PENALTY = -4.0;

    private final NamespacedKey healthKey;
    /// server tick of her last jump press, to spot the second tap
    private final Map<UUID, Integer> lastJumpPress = new HashMap<>();
    /// server tick her double jump is ready again
    private final Map<UUID, Integer> readyAt = new HashMap<>();

    public HarperVow(VowSMPPlugin plugin) {
        super(plugin);
        healthKey = new NamespacedKey(plugin, "harper_health");

        // the lives bar only refreshes every 2 seconds, so count the cooldown down ourselves
        Bukkit.getScheduler().runTaskTimer(plugin, this::cooldownTick, COOLDOWN_DISPLAY_TICKS, COOLDOWN_DISPLAY_TICKS);
    }

    @Override
    public String id() {
        return "harper";
    }

    @Override
    public String name() {
        return "Harper vow";
    }

    @Override
    public String description() {
        return "Double tap space to double jump (5 second cooldown), but you only have 8 hearts, "
                + "can't use bows or crossbows, and can never use traps or invisibility.";
    }

    @Override
    public Material icon() {
        return Material.FEATHER;
    }

    @Override
    public void onGain(Player player) {
        applyHealthPenalty(player);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    @Override
    public void onLose(Player player) {
        removeHealthPenalty(player);
        lastJumpPress.remove(player.getUniqueId());
        readyAt.remove(player.getUniqueId());
    }

    @Override
    public void tick(Player player, int lives) {
        // something else (like /attribute) could have stripped it
        applyHealthPenalty(player);
    }

    @Override
    public Component actionBarStatus(Player player) {
        int remaining = remainingCooldown(player);
        if (remaining <= 0) {
            return Component.text("Double jump: ", NamedTextColor.GRAY)
                    .append(Component.text("READY", NamedTextColor.GREEN));
        }
        return Component.text("Double jump: ", NamedTextColor.GRAY)
                .append(Component.text(String.format(Locale.ROOT, "%.1fs", remaining / 20.0), NamedTextColor.RED));
    }

    // double jump

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        // only fresh presses of space - this fires for every input change, including letting go
        if (!event.getInput().isJump() || player.getCurrentInput().isJump() || !has(player)) return;

        UUID id = player.getUniqueId();
        int now = Bukkit.getCurrentTick();
        Integer last = lastJumpPress.put(id, now);
        if (last == null || now - last > DOUBLE_TAP_TICKS) return;
        if (!canDoubleJump(player) || remainingCooldown(player) > 0) return;

        // so a third tap doesn't count as another double tap
        lastJumpPress.remove(id);
        readyAt.put(id, now + COOLDOWN_TICKS);

        Vector forward = player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() > 0) {
            forward.normalize().multiply(JUMP_FORWARD);
        }
        player.setVelocity(forward.setY(JUMP_UP));
        // start the fall over from the top of the new jump
        player.setFallDistance(0);
        plugin.getLivesActionBar().update(id);
    }

    /// only in mid-air, and not while flying, gliding, swimming, climbing or riding
    private boolean canDoubleJump(Player player) {
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return false;
        if (player.isDead() || player.isOnGround() || player.isFlying() || player.isGliding()) return false;
        if (player.isInWater() || player.isInLava() || player.isClimbing() || player.isInsideVehicle()) return false;
        return true;
    }

    private int remainingCooldown(Player player) {
        Integer ready = readyAt.get(player.getUniqueId());
        return ready == null ? 0 : ready - Bukkit.getCurrentTick();
    }

    private void cooldownTick() {
        int now = Bukkit.getCurrentTick();
        // keep each player in the map for one more refresh after it runs out, so the bar flips to READY right away
        readyAt.entrySet().removeIf(entry -> {
            plugin.getLivesActionBar().update(entry.getKey());
            return entry.getValue() <= now;
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastJumpPress.remove(event.getPlayer().getUniqueId());
    }

    // 8 hearts

    private void applyHealthPenalty(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null || maxHealth.getModifier(healthKey) != null) return;
        maxHealth.addModifier(new AttributeModifier(healthKey, HEALTH_PENALTY, AttributeModifier.Operation.ADD_NUMBER));
    }

    private void removeHealthPenalty(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.removeModifier(healthKey);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // the modifier is saved with the player, so it's still there if she lost the vow while offline
        if (!has(event.getPlayer())) {
            removeHealthPenalty(event.getPlayer());
        }
    }

    // no bows or crossbows

    @EventHandler(priority = EventPriority.HIGH)
    public void onUseBow(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isBow(event.getItem()) || !has(event.getPlayer())) return;

        // stops drawing a bow, loading a crossbow and firing one that's already loaded
        event.setUseItemInHand(Event.Result.DENY);
        event.getPlayer().sendActionBar(Component.text("You can't use bows or crossbows", NamedTextColor.RED));
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && has(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLoadCrossbow(EntityLoadCrossbowEvent event) {
        if (event.getEntity() instanceof Player player && has(player)) {
            event.setCancelled(true);
        }
    }

    private boolean isBow(ItemStack item) {
        return item != null && (item.getType() == Material.BOW || item.getType() == Material.CROSSBOW);
    }

    // no invisibility

    @EventHandler(ignoreCancelled = true)
    public void onDrinkInvisibility(PlayerItemConsumeEvent event) {
        if (!givesInvisibility(event.getItem()) || !has(event.getPlayer())) return;

        // she keeps the potion instead of wasting it
        event.setCancelled(true);
        event.getPlayer().sendActionBar(Component.text("You can't use invisibility", NamedTextColor.RED));
    }

    /// catches every other way to get it too (splash and lingering potions, commands...)
    @EventHandler(ignoreCancelled = true)
    public void onInvisibility(EntityPotionEffectEvent event) {
        PotionEffect effect = event.getNewEffect();
        if (effect == null || !effect.getType().equals(PotionEffectType.INVISIBILITY)) return;
        if (event.getEntity() instanceof Player player && has(player)) {
            event.setCancelled(true);
        }
    }

    private boolean givesInvisibility(ItemStack item) {
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return false;
        if (meta.hasCustomEffect(PotionEffectType.INVISIBILITY)) return true;
        PotionType base = meta.getBasePotionType();
        return base != null && base.getPotionEffects().stream()
                .anyMatch(effect -> effect.getType().equals(PotionEffectType.INVISIBILITY));
    }
}
