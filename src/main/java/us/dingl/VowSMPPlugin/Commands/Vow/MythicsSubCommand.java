package us.dingl.VowSMPPlugin.Commands.Vow;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import us.dingl.VowSMPPlugin.Mythics.MythicManager;
import us.dingl.VowSMPPlugin.Mythics.MythicManager.Mythic;
import us.dingl.VowSMPPlugin.Permissions;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

/// /mythics - who holds each mythic item. Registered both as its own command and as a /vow subcommand.
public class MythicsSubCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public MythicsSubCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("mythics")
                .requires(CommandUtil.permission(Permissions.MYTHICS))
                .executes(this::list)
                .then(Commands.literal("list").executes(this::list));
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        MythicManager mythics = plugin.getMythicManager();

        if (mythics.getMythics().isEmpty()) {
            sender.sendMessage(Component.text("There are no mythics yet.", NamedTextColor.GRAY));
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage(Component.text("Mythics:", NamedTextColor.GOLD));
        for (Mythic mythic : mythics.getMythics()) {
            String held = mythic.holder() == null ? "nobody" : plugin.getPlayerName(mythic.holder());

            sender.sendMessage(mythic.name()
                    .append(Component.text(" (" + mythic.id() + ")", NamedTextColor.DARK_GRAY)));
            sender.sendMessage(Component.text("  Held by: " + held, NamedTextColor.GRAY));
        }
        return Command.SINGLE_SUCCESS;
    }
}
