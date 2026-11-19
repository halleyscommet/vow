package us.dingl.VowSMPPlugin.Items.Abilities.Impl;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import us.dingl.VowSMPPlugin.Items.Abilities.AbilitySetting;
import us.dingl.VowSMPPlugin.Items.Abilities.ItemAbility;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// right click to dash the way you're looking. If the item also has Cooldown, a dash starts it and
/// can't happen while it's running (see {@link CooldownAbility#tryUse}).
///
/// The distance is in blocks. Airborne players keep 91% of their horizontal speed each tick, so a launch
/// speed v travels about v / (1 - 0.91) blocks in total - speed = 0.09 * blocks. That's exact in open air
/// and a bit short when the dash ends on the ground (friction stops them sooner) or goes straight up
/// (gravity), which is close enough. Speed is capped at 3.9 blocks/tick, the most the client accepts,
/// so the very longest dashes come up short too.
public class DashAbility extends ItemAbility {

    private static final String DISTANCE = "distance";
    private static final int MIN_BLOCKS = 1;
    private static final int MAX_BLOCKS = 50;
    private static final double AIR_DRAG = 0.91;
    private static final double MAX_SPEED = 3.9;
    /// a little hop for flat dashes off the ground, so ground friction doesn't eat the dash
    private static final double GROUND_LIFT = 0.35;
    /// how long after a dash the next fall doesn't hurt
    private static final long NO_FALL_MILLIS = 5000;

    private final CooldownAbility cooldown;
    /// player -> when their dash's fall damage protection runs out (epoch millis)
    private final Map<UUID, Long> noFallUntil = new HashMap<>();
    /// player -> the tick they last dashed, so one click firing two interact events only dashes once
    private final Map<UUID, Integer> lastDashTick = new HashMap<>();

    public DashAbility(VowSMPPlugin plugin, CooldownAbility cooldown) {
        super(plugin);
        this.cooldown = cooldown;
    }

    @Override
    public String id() {
        return "dash";
    }

    @Override
    public String name() {
        return "Dash";
    }

    @Override
    public String description() {
        return "Right click to dash the way you're looking, " + MIN_BLOCKS + "-" + MAX_BLOCKS
                + " blocks. The landing doesn't hurt. Starts the item's Cooldown, if it has one.";
    }

    @Override
    public Material icon() {
        return Material.FEATHER;
    }

    @Override
    public List<AbilitySetting> settings() {
        return List.of(new AbilitySetting(DISTANCE, "Dash distance in blocks, " + MIN_BLOCKS + "-" + MAX_BLOCKS,
                AbilitySetting.Type.NUMBER, "8"));
    }

    /// out of range numbers are pulled into range
    @Override
    public void onApply(ItemStack item, Map<String, String> settings) {
        settings.put(DISTANCE, Integer.toString(blocks(settings)));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.useItemInHand() == Event.Result.DENY) return;
        Map<String, String> settings = settings(event.getItem());
        if (settings == null) return;

        Player player = event.getPlayer();
        // opening a door or chest with the item in hand isn't a dash
        Block block = event.getClickedBlock();
        if (block != null && block.getType().isInteractable() && !player.isSneaking()) return;
        int tick = Bukkit.getCurrentTick();
        Integer last = lastDashTick.get(player.getUniqueId());
        if (last != null && last == tick) return;
        if (!cooldown.tryUse(player, event.getItem())) return;
        lastDashTick.put(player.getUniqueId(), tick);

        dash(player, blocks(settings));
    }

    private void dash(Player player, int blocks) {
        double speed = Math.min(MAX_SPEED, blocks * (1 - AIR_DRAG));
        Vector velocity = player.getLocation().getDirection().multiply(speed);
        if (player.isOnGround() && velocity.getY() < GROUND_LIFT) velocity.setY(GROUND_LIFT);
        player.setVelocity(velocity);
        player.setFallDistance(0);
        noFallUntil.put(player.getUniqueId(), System.currentTimeMillis() + NO_FALL_MILLIS);

        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 0.5, 0), 12, 0.3, 0.2, 0.3, 0.05);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.8f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        Long until = noFallUntil.remove(player.getUniqueId());
        if (until != null && until > System.currentTimeMillis()) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        noFallUntil.remove(event.getPlayer().getUniqueId());
        lastDashTick.remove(event.getPlayer().getUniqueId());
    }

    private static int blocks(Map<String, String> settings) {
        try {
            long value = Long.parseLong(settings.getOrDefault(DISTANCE, "8"));
            return (int) Math.max(MIN_BLOCKS, Math.min(MAX_BLOCKS, value));
        } catch (NumberFormatException e) {
            return 8;
        }
    }
}
