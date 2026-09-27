package us.dingl.VowSMPPlugin.Guis;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import us.dingl.VowSMPPlugin.Gui.Gui;
import us.dingl.VowSMPPlugin.Gui.GuiInstance;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.List;
import java.util.UUID;

public final class LivesMenuGui {

    private LivesMenuGui() {
    }

    public static GuiInstance create(VowSMPPlugin plugin) {
        Gui gui = Gui.builder(Component.text("Player Lives"), 6);

        List<UUID> players = plugin.getLoggedPlayers();
        for (int slot = 0; slot < Math.min(players.size(), 45); slot++) {
            UUID id = players.get(slot);
            // only plain left/right; drop keys, number keys, double clicks etc. do nothing
            gui.button(slot, () -> head(plugin, id), (player, click) -> {
                switch (click) {
                    case LEFT -> plugin.updatePlayerLifeCounter(id, 1);
                    case RIGHT -> plugin.updatePlayerLifeCounter(id, -1);
                    default -> { }
                }
            });
        }
        return gui.build();
    }

    private static ItemStack head(VowSMPPlugin plugin, UUID id) {
        return plugin.getHeadCache().head(
                id,
                Component.text(plugin.getPlayerName(id)).decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("Lives: " + plugin.getPlayerLives(id), NamedTextColor.GREEN)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("Left click: +1", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("Right click: -1", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                )
        );
    }
}
