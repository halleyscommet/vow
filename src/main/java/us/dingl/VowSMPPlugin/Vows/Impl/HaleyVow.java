package us.dingl.VowSMPPlugin.Vows.Impl;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.SoulboundItemVow;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/// takes 2.5 hearts every second she has open sky above her (after a short grace period), no matter her armor,
/// effects, the weather or the time of day.
/// In exchange, she gets a soulbound unbreakable Fortune IV diamond pickaxe that mines a 3x3 area while sneaking,
/// which she can toggle with /vow 3x3.
public class HaleyVow extends SoulboundItemVow {

    private static final double EXPOSURE_DAMAGE = 5.0;
    /// how long she can be under open sky before the first hit
    private static final int GRACE_TICKS = 10;
    private static final int DAMAGE_INTERVAL_TICKS = 20;

    /// vow:exposure from the bundled datapack - ignores armor, protection, resistance and fire resistance
    private static final NamespacedKey EXPOSURE_KEY = new NamespacedKey("vow", "exposure");

    private final NamespacedKey areaMiningKey;
    private final DamageType exposure;
    private final Map<UUID, Integer> exposedTicks = new HashMap<>();
    /// true while we deal exposure damage, so a death in the middle of it gets our death message
    private boolean dealingExposure;
    /// true while we break the extra blocks, so those breaks don't trigger another 3x3
    private boolean areaBreaking;

    public HaleyVow(VowSMPPlugin plugin) {
        super(plugin, "haley_pickaxe");
        areaMiningKey = new NamespacedKey(plugin, "haley_area_mining");

        DamageType type = RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE).get(EXPOSURE_KEY);
        if (type == null) {
            // datapack didn't load. generic_kill also ignores armor and effects, but gets past totems too
            plugin.getLogger().warning("Damage type " + EXPOSURE_KEY + " is missing, Haley vow exposure damage will use generic_kill");
            type = DamageType.GENERIC_KILL;
        }
        exposure = type;

        // the vow tick is once a second, but the grace period needs tick precision
        Bukkit.getScheduler().runTaskTimer(plugin, this::exposureTick, 1L, 1L);
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
        return "You take 2.5 hearts every second you're under open sky, but get a soulbound Fortune IV pickaxe "
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
        exposedTicks.remove(player.getUniqueId());
    }

    @Override
    public Component actionBarStatus(Player player) {
        boolean enabled = isAreaMiningEnabled(player);
        return Component.text("3x3: ", NamedTextColor.GRAY)
                .append(Component.text(enabled ? "ON" : "OFF", enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    // exposure

    private void exposureTick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!has(player)) continue;

            UUID id = player.getUniqueId();
            if (!isExposed(player)) {
                // getting under cover resets the grace period
                exposedTicks.remove(id);
                continue;
            }

            int ticks = exposedTicks.merge(id, 1, Integer::sum);
            if (ticks >= GRACE_TICKS && (ticks - GRACE_TICKS) % DAMAGE_INTERVAL_TICKS == 0) {
                // a recent hit would otherwise make this deal only the difference
                player.setNoDamageTicks(0);
                dealingExposure = true;
                try {
                    player.damage(EXPOSURE_DAMAGE, DamageSource.builder(exposure).build());
                } finally {
                    dealingExposure = false;
                }
            }
        }
    }

    /// true if nothing but air and water is above her head - any time, any weather, any dimension
    private boolean isExposed(Player player) {
        if (player.isDead()) return false;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return false;

        Block head = player.getEyeLocation().getBlock();
        World world = player.getWorld();
        // the heightmap is the highest non-air block, so above it is all sky. Below it, water still counts
        // as open (she isn't safe just by swimming), so walk up and look for anything that isn't a fluid.
        int top = world.getHighestBlockYAt(head.getX(), head.getZ(), HeightMap.WORLD_SURFACE);
        for (int y = head.getY() + 1; y <= top; y++) {
            Block block = world.getBlockAt(head.getX(), y, head.getZ());
            if (!block.isEmpty() && !block.isLiquid() && block.getType() != Material.BUBBLE_COLUMN) {
                return false;
            }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onExposureDeath(PlayerDeathEvent event) {
        if (!dealingExposure || !has(event.getPlayer())) return;
        event.deathMessage(event.getPlayer().displayName()
                .append(Component.text(" was claimed by the open sky")));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        exposedTicks.remove(event.getPlayer().getUniqueId());
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
