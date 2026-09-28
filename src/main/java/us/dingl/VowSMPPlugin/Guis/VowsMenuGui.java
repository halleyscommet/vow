package us.dingl.VowSMPPlugin.Guis;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import us.dingl.VowSMPPlugin.Gui.Gui;
import us.dingl.VowSMPPlugin.Gui.GuiInstance;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Vow;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class VowsMenuGui {

    private static final int WRAP_WIDTH = 40;

    private VowsMenuGui() {
    }

    public static GuiInstance create(VowSMPPlugin plugin) {
        Gui gui = Gui.builder(Component.text("Vows"), 6);

        List<Vow> vows = new ArrayList<>(plugin.getVowManager().getVows());
        for (int slot = 0; slot < Math.min(vows.size(), 54); slot++) {
            Vow vow = vows.get(slot);
            // display only for now - supplier so the holder list stays current
            gui.button(slot, () -> vowItem(plugin, vow), (player, click) -> { });
        }
        return gui.build();
    }

    private static ItemStack vowItem(VowSMPPlugin plugin, Vow vow) {
        List<Component> lore = new ArrayList<>();
        for (String line : wrap(vow.description())) {
            lore.add(line(line, NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(line("ID: " + vow.id(), NamedTextColor.DARK_GRAY));

        List<UUID> holders = plugin.getVowManager().getHolders(vow);
        if (holders.isEmpty()) {
            lore.add(line("Nobody has this vow", NamedTextColor.DARK_GRAY));
        } else {
            lore.add(line("Held by:", NamedTextColor.GREEN));
            for (UUID id : holders) {
                lore.add(line(" - " + plugin.getPlayerName(id), NamedTextColor.WHITE));
            }
        }

        ItemStack item = new ItemStack(vow.icon());
        item.editMeta(meta -> {
            meta.displayName(Component.text(vow.name(), NamedTextColor.GOLD, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        return item;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    /// lore doesn't wrap on its own, so long descriptions would run off the screen
    private static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!current.isEmpty() && current.length() + 1 + word.length() > WRAP_WIDTH) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append(' ');
            current.append(word);
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }
}
