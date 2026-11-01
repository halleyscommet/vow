package us.dingl.VowSMPPlugin.Commands.Admin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import us.dingl.VowSMPPlugin.Commands.Vow.CommandUtil;
import us.dingl.VowSMPPlugin.Commands.Vow.SubCommand;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

/// /vow admin item - opens the item builder on the held item (or a new one if your hand is empty)
public class ItemAdminCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public ItemAdminCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("item").executes(ctx -> {
            plugin.getAdminMenus().openItemBuilder(CommandUtil.requirePlayer(ctx));
            return Command.SINGLE_SUCCESS;
        });
    }
}
