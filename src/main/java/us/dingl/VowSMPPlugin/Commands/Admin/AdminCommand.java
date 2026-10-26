package us.dingl.VowSMPPlugin.Commands.Admin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.Commands.Vow.CommandUtil;
import us.dingl.VowSMPPlugin.Commands.Vow.SubCommand;
import us.dingl.VowSMPPlugin.Permissions;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.List;

/// /vow admin - opens the admin panel. Everything the panel does also has a command under here,
/// for the console or when typing is quicker.
public class AdminCommand implements SubCommand {

    private final VowSMPPlugin plugin;
    private final List<SubCommand> subCommands;

    public AdminCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
        this.subCommands = List.of(
                new LivesAdminCommand(plugin),
                new VowsAdminCommand(plugin),
                new MythicAdminCommand(plugin),
                new LeaderboardAdminCommand(plugin),
                new RitualAdminCommand(plugin),
                new StatsAdminCommand(plugin),
                new UpdateAdminCommand(plugin),
                new DebugAdminCommand(plugin)
        );
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("admin")
                .requires(CommandUtil.permission(Permissions.ADMIN))
                .executes(ctx -> {
                    if (ctx.getSource().getSender() instanceof Player player) {
                        plugin.getAdminMenus().openMain(player);
                    } else {
                        ctx.getSource().getSender().sendMessage("Usage: /vow admin <" + String.join("|",
                                subCommands.stream().map(sub -> sub.build().getLiteral()).toList()) + ">");
                    }
                    return Command.SINGLE_SUCCESS;
                });

        for (SubCommand subCommand : subCommands) {
            root.then(subCommand.build());
        }
        return root;
    }
}
