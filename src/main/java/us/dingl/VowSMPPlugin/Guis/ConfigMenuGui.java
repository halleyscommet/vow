package us.dingl.VowSMPPlugin.Guis;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import us.dingl.VowSMPPlugin.Gui.Gui;
import us.dingl.VowSMPPlugin.Gui.GuiInstance;
import us.dingl.VowSMPPlugin.Gui.SlotPermission;
import us.dingl.VowSMPPlugin.Gui.StateBinding;
import us.dingl.VowSMPPlugin.Permissions;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.List;
import java.util.Set;

@SuppressWarnings("UnstableApiUsage")
public final class ConfigMenuGui {

    private static final Set<Integer> FILLER_SLOTS =
            Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26);

    private ConfigMenuGui() {}

    public static GuiInstance create(VowSMPPlugin plugin) {
        ItemStack disable = actionItem(Material.REDSTONE, "Disable Leaderboard", NamedTextColor.DARK_RED);
        ItemStack enable = actionItem(Material.EMERALD, "Enable Leaderboard", NamedTextColor.DARK_GREEN);

        ItemStack filler = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.setItemModel(NamespacedKey.fromString("vow:blank_slot")));
        filler.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());

        ItemStack stormStop = actionItem(Material.LIGHTNING_ROD, "Stop Storm", NamedTextColor.DARK_RED);
        ItemStack stormStart = actionItem(Material.LIGHTNING_ROD, "Start Storm", NamedTextColor.DARK_GREEN);

        return Gui.builder(Component.text("Config Menu"), 3)
                .button(10, StateBinding.bool(plugin::getLeaderboardEnabled, disable, enable),
                        (player, clickType) -> plugin.toggleLeaderboard())
                .button(11, () -> positionItem(plugin), (player, clickType) -> {
                    Block target = player.getTargetBlockExact(5);
                    Location location = target != null ? target.getLocation() : player.getLocation();

                    // +1 so the board floats above the block you're looking at
                    int x = location.getBlockX();
                    int y = location.getBlockY() + 1;
                    int z = location.getBlockZ();

                    plugin.setLeaderboardPosition(x, y, z);
                    player.sendMessage("Leaderboard position has been set to: " + x + " " + y + " " + z);
                })
                .button(13, StateBinding.bool(() -> plugin.getStorm().isActive(), stormStop, stormStart),
                        (player, clickType) -> {
                            if (plugin.getStorm().isActive()) {
                                plugin.getStorm().stop();
                            } else {
                                plugin.getStorm().start(player, false);
                            }
                        })
                .button(14, () -> actionItem(Material.PLAYER_HEAD, "Edit Lives", NamedTextColor.GOLD),
                        (player, clickType) -> LivesMenuGui.create(plugin).open(player))
                .button(15, () -> actionItem(Material.ENCHANTED_BOOK, "Vows", NamedTextColor.LIGHT_PURPLE),
                        (player, clickType) -> VowsMenuGui.create(plugin).open(player))
                .button(16, () -> updateItem(plugin), (player, clickType) -> {
                    if (!player.hasPermission(Permissions.UPDATE)) {
                        player.sendMessage(Component.text("You don't have permission to update the plugin.", NamedTextColor.RED));
                        return;
                    }
                    // chat is hidden behind the menu, so close it to show the update progress.
                    // next tick because closing inside the click event is unreliable
                    Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
                    plugin.getUpdater().update(player);
                })
                .filler(FILLER_SLOTS, filler)
                .zone("zone1", SlotPermission.NONE, FILLER_SLOTS)
                .build();
    }

    private static ItemStack actionItem(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.displayName(
                Component.text(name, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false)));
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().build());
        return item;
    }

    private static ItemStack updateItem(VowSMPPlugin plugin) {
        ItemStack item = actionItem(Material.RECOVERY_COMPASS, "Check for Updates", NamedTextColor.AQUA);
        item.editMeta(meta -> meta.lore(List.of(
                Component.text("Current version: " + plugin.getPluginMeta().getVersion(), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Downloads the latest release,", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("then restart to apply it", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        )));
        return item;
    }

    private static ItemStack positionItem(VowSMPPlugin plugin) {
        Location pos = plugin.getLeaderboardLocation();
        String coords = pos == null
                ? "unknown"
                : pos.getBlockX() + " " + pos.getBlockY() + " " + pos.getBlockZ();

        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Set Leaderboard Position").decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Current position is:", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text(coords).decoration(TextDecoration.ITALIC, false)
            ));
        });
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().build());
        return item;
    }
}
