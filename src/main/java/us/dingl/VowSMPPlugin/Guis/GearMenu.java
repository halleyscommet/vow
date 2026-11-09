package us.dingl.VowSMPPlugin.Guis;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.help.HelpTopic;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/// a chest of gear that never runs out - clicking a slot hands you a copy and the slot stays full.
/// Nothing can be put into it. Only reachable through GearCommand.
public final class GearMenu implements Listener {

    private static final Set<String> ALLOWED = Set.of("collera", "_wolfhaley");
    /// Paper lists every plugin command in /help to everyone, ignoring requires(). Its help topics are tied
    /// to this instead - off for everyone (ops included) and handed only to the players above.
    private static final String HELP_PERMISSION = "vow.gear";
    private static final List<String> HELP_TOPICS = List.of("/gear", "/vowsmpplugin:gear");

    private final Plugin plugin;

    public GearMenu(Plugin plugin) {
        this.plugin = plugin;
        PluginManager pm = plugin.getServer().getPluginManager();
        if (pm.getPermission(HELP_PERMISSION) == null) {
            pm.addPermission(new Permission(HELP_PERMISSION, PermissionDefault.FALSE));
        }
        Bukkit.getOnlinePlayers().forEach(this::grantHelp);
        // help topics are built once the server finishes starting, so wait a tick
        Bukkit.getScheduler().runTask(plugin, () -> HELP_TOPICS.forEach(name -> {
            HelpTopic topic = Bukkit.getHelpMap().getHelpTopic(name);
            if (topic != null) topic.amendCanSee(HELP_PERMISSION);
        }));
    }

    private void grantHelp(Player player) {
        if (isAllowed(player)) player.addAttachment(plugin, HELP_PERMISSION, true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        grantHelp(event.getPlayer());
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public static boolean isAllowed(Player player) {
        return ALLOWED.contains(player.getName().toLowerCase(Locale.ROOT));
    }

    public static void open(Player player) {
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, 36, Component.text("Gear"));
        holder.inventory = inventory;

        ItemStack[] items = {
                // row 1: armor down the left, then weapons and tools
                armor(Material.DIAMOND_HELMET, Enchantment.RESPIRATION, 3, Enchantment.AQUA_AFFINITY, 1),
                gear(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 3, Enchantment.LOOTING, 3, Enchantment.SWEEPING_EDGE, 3),
                gear(Material.DIAMOND_AXE, Enchantment.SHARPNESS, 3, Enchantment.EFFICIENCY, 5),
                gear(Material.NETHERITE_PICKAXE, Enchantment.EFFICIENCY, 5, Enchantment.FORTUNE, 3),
                gear(Material.NETHERITE_PICKAXE, Enchantment.EFFICIENCY, 5, Enchantment.SILK_TOUCH, 1),
                gear(Material.SHIELD),
                enchanted(Material.BOW, Enchantment.POWER, 5, Enchantment.UNBREAKING, 3, Enchantment.INFINITY, 1),
                gear(Material.CROSSBOW, Enchantment.QUICK_CHARGE, 3, Enchantment.MULTISHOT, 1),
                gear(Material.TRIDENT, Enchantment.LOYALTY, 3, Enchantment.IMPALING, 5),

                // row 2: consumables
                armor(Material.DIAMOND_CHESTPLATE),
                new ItemStack(Material.GOLDEN_APPLE, 64),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
                new ItemStack(Material.COBWEB, 64),
                new ItemStack(Material.WATER_BUCKET),
                new ItemStack(Material.WIND_CHARGE, 64),
                new ItemStack(Material.ARROW, 64),
                new ItemStack(Material.GOLDEN_CARROT, 64),
                new ItemStack(Material.ENDER_CHEST),

                // row 3: potions - Strength II and Speed I at most, no weaving
                armor(Material.DIAMOND_LEGGINGS, Enchantment.SWIFT_SNEAK, 3),
                potion(Material.SPLASH_POTION, PotionType.STRONG_STRENGTH),
                potion(Material.SPLASH_POTION, PotionType.LONG_SWIFTNESS),
                potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING),
                potion(Material.SPLASH_POTION, PotionType.LONG_FIRE_RESISTANCE),
                potion(Material.SPLASH_POTION, PotionType.LONG_SLOW_FALLING),
                potion(Material.POTION, PotionType.STRONG_STRENGTH),
                potion(Material.POTION, PotionType.LONG_SWIFTNESS),
                potion(Material.POTION, PotionType.LONG_FIRE_RESISTANCE),

                // row 4: blocks
                armor(Material.DIAMOND_BOOTS, Enchantment.FEATHER_FALLING, 4, Enchantment.DEPTH_STRIDER, 3),
                new ItemStack(Material.OAK_PLANKS, 64),
                new ItemStack(Material.COBBLED_DEEPSLATE, 64),
                new ItemStack(Material.OBSIDIAN, 64),
                new ItemStack(Material.OAK_LOG, 64),
                new ItemStack(Material.SCAFFOLDING, 64),
                new ItemStack(Material.LADDER, 64),
                gear(Material.NETHERITE_SHOVEL, Enchantment.EFFICIENCY, 5),
                new ItemStack(Material.SHULKER_BOX),
        };
        inventory.setContents(items);
        player.openInventory(inventory);
    }

    /// Protection III plus Unbreaking III and Mending, and whatever else is passed
    private static ItemStack armor(Material type, Object... extra) {
        ItemStack item = gear(type, extra);
        item.addEnchantment(Enchantment.PROTECTION, 3);
        return item;
    }

    /// Unbreaking III and Mending, plus the given enchantment/level pairs
    private static ItemStack gear(Material type, Object... extra) {
        ItemStack item = enchanted(type, extra);
        item.addEnchantment(Enchantment.UNBREAKING, 3);
        item.addEnchantment(Enchantment.MENDING, 1);
        return item;
    }

    /// just the given enchantment/level pairs
    private static ItemStack enchanted(Material type, Object... pairs) {
        ItemStack item = new ItemStack(type);
        for (int i = 0; i < pairs.length; i += 2) {
            item.addEnchantment((Enchantment) pairs[i], (Integer) pairs[i + 1]);
        }
        return item;
    }

    private static ItemStack potion(Material type, PotionType potion) {
        ItemStack item = new ItemStack(type);
        item.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(potion));
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory clicked = event.getClickedInventory();

        if (clicked == null || clicked instanceof PlayerInventory) {
            // their own inventory works as normal, except shift-clicking into the menu or
            // double-click collecting, which would pull stacks out of it
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || item.isEmpty()) return;
        ItemStack copy = item.clone();

        if (event.getClick() == ClickType.NUMBER_KEY) {
            PlayerInventory inventory = player.getInventory();
            if (inventory.getItem(event.getHotbarButton()) == null) {
                inventory.setItem(event.getHotbarButton(), copy);
            }
        } else if (event.isShiftClick()) {
            player.getInventory().addItem(copy);
        } else if (event.getCursor().isEmpty()) {
            player.setItemOnCursor(copy);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }
}
