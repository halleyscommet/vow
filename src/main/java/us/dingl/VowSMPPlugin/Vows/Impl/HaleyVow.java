package us.dingl.VowSMPPlugin.Vows.Impl;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.SoulboundItemVow;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/// burns for 2.5 hearts every second in direct sunlight (after a short grace period), no matter her armor or effects.
/// In exchange, she gets a soulbound unbreakable Fortune IV diamond pickaxe that mines a 3x3 area while sneaking,
/// which she can toggle with /vow 3x3.
public class HaleyVow extends SoulboundItemVow {

    private static final double SUN_DAMAGE = 5.0;
    /// how long she can be in the sun before the first burn
    private static final int GRACE_TICKS = 10;
    private static final int BURN_INTERVAL_TICKS = 20;

    /// vow:sunburn from the bundled datapack - ignores armor, protection, resistance and fire resistance
    private static final NamespacedKey SUNBURN_KEY = new NamespacedKey("vow", "sunburn");

    private final NamespacedKey areaMiningKey;
    private final DamageType sunburn;
    private final Map<UUID, Integer> sunTicks = new HashMap<>();
    /// true while we break the extra blocks, so those breaks don't trigger another 3x3
    private boolean areaBreaking;

    public HaleyVow(VowSMPPlugin plugin) {
        super(plugin, "haley_pickaxe");
        areaMiningKey = new NamespacedKey(plugin, "haley_area_mining");

        DamageType type = RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE).get(SUNBURN_KEY);
        if (type == null) {
            // datapack didn't load. generic_kill also ignores armor and effects, but gets past totems too
            plugin.getLogger().warning("Damage type " + SUNBURN_KEY + " is missing, Haley vow sun damage will use generic_kill");
            type = DamageType.GENERIC_KILL;
        }
        sunburn = type;

        // the vow tick is once a second, but the grace period needs tick precision
        Bukkit.getScheduler().runTaskTimer(plugin, this::sunTick, 1L, 1L);
    }

    @Override
    public String id() {
        return "haley";
    }

    @Override
    public String name() {
        return "Haley vow";
    }

    @Override
    public String description() {
        return "You take 2.5 hearts every second in the sun, but get a soulbound Fortune IV pickaxe "
                + "that mines 3x3 while sneaking (toggle with /vow 3x3).";
    }

    @Override
    public Material icon() {
        return Material.DIAMOND_PICKAXE;
    }

    @Override
    protected ItemStack buildItem() {
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        pickaxe.editMeta(meta -> {
            // unsafe because fortune normally caps at III
            meta.addEnchant(Enchantment.FORTUNE, 4, true);
            meta.setUnbreakable(true);
        });
        return pickaxe;
    }

    @Override
    public void onLose(Player player) {
        super.onLose(player);
        sunTicks.remove(player.getUniqueId());
    }

    @Override
    public Component actionBarStatus(Player player) {
        boolean enabled = isAreaMiningEnabled(player);
        return Component.text("3x3: ", NamedTextColor.GRAY)
                .append(Component.text(enabled ? "ON" : "OFF", enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    // sun

    private void sunTick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!has(player)) continue;

            UUID id = player.getUniqueId();
            if (!isInSun(player)) {
                // stepping into shade resets the grace period
                sunTicks.remove(id);
                continue;
            }

            int ticks = sunTicks.merge(id, 1, Integer::sum);
            if (ticks >= GRACE_TICKS && (ticks - GRACE_TICKS) % BURN_INTERVAL_TICKS == 0) {
                // a recent hit would otherwise make the burn deal only the difference
                player.setNoDamageTicks(0);
                player.damage(SUN_DAMAGE, DamageSource.builder(sunburn).build());
            }
        }
    }

    /// same conditions that make zombies burn: daytime, open sky, not in water or rain
    private boolean isInSun(Player player) {
        if (player.isDead()) return false;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return false;

        World world = player.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL || !world.isDayTime()) return false;
        if (player.isInWater() || player.isInRain()) return false;

        return player.getEyeLocation().getBlock().getLightFromSky() == 15;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sunTicks.remove(event.getPlayer().getUniqueId());
    }

    // 3x3 mining

    public boolean isAreaMiningEnabled(Player player) {
        return player.getPersistentDataContainer().getOrDefault(areaMiningKey, PersistentDataType.BOOLEAN, true);
    }

    public void setAreaMiningEnabled(Player player, boolean enabled) {
        player.getPersistentDataContainer().set(areaMiningKey, PersistentDataType.BOOLEAN, enabled);
        plugin.getLivesActionBar().update(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (areaBreaking) return;

        Player player = event.getPlayer();
        if (!player.isSneaking() || !has(player) || !isAreaMiningEnabled(player)) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        Block center = event.getBlock();
        if (!isSoulbound(tool) || !center.isPreferredTool(tool)) return;

        // the face she's looking at decides the plane: a wall face mines the wall, a floor face mines the floor
        BlockFace face = lookedAtFace(player, center);
        int nx = Math.abs(face.getModX());
        int ny = Math.abs(face.getModY());
        int nz = Math.abs(face.getModZ());
        float centerHardness = center.getType().getHardness();

        areaBreaking = true;
        try {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        // stay in the plane: no offset along the face's axis, and skip the center
                        if (dx * nx != 0 || dy * ny != 0 || dz * nz != 0) continue;
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        Block block = center.getRelative(dx, dy, dz);
                        if (canAreaMine(block, tool, centerHardness)) {
                            // fires its own BlockBreakEvent (so claims still apply) and uses the pickaxe's fortune
                            player.breakBlock(block);
                        }
                    }
                }
            }
        } finally {
            areaBreaking = false;
        }
    }

    /// only blocks the pickaxe is meant for (so they drop), and nothing harder than the block she mined,
    /// so mining stone doesn't also pull out obsidian or bedrock next to it
    private boolean canAreaMine(Block block, ItemStack tool, float centerHardness) {
        if (block.isEmpty() || block.isLiquid()) return false;
        float hardness = block.getType().getHardness();
        return hardness >= 0 && hardness <= centerHardness && block.isPreferredTool(tool);
    }

    private BlockFace lookedAtFace(Player player, Block center) {
        AttributeInstance range = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        RayTraceResult hit = player.rayTraceBlocks(range != null ? range.getValue() : 4.5);
        if (hit != null && center.equals(hit.getHitBlock()) && hit.getHitBlockFace() != null) {
            return hit.getHitBlockFace();
        }

        // ray missed (lag, or the block changed): fall back to where she's looking
        float pitch = player.getLocation().getPitch();
        if (pitch > 45) return BlockFace.UP;
        if (pitch < -45) return BlockFace.DOWN;
        return player.getFacing().getOppositeFace();
    }
}
