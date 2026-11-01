package us.dingl.VowSMPPlugin.Commands.Admin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import us.dingl.VowSMPPlugin.Commands.Vow.CommandUtil;
import us.dingl.VowSMPPlugin.Commands.Vow.SubCommand;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.List;
import java.util.UUID;

public class LeaderboardAdminCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public LeaderboardAdminCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return LeaderboardCommands.build("leaderboard", "Leaderboard", plugin.getLeaderboard())
                .then(Commands.literal("add")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    List<UUID> immortals = plugin.getPlayersNotYetKilled();
                                    plugin.getKilledPlayers().forEach(id -> builder.suggest(plugin.getPlayerName(id)));
                                    Bukkit.getOnlinePlayers().stream()
                                            .filter(p -> !immortals.contains(p.getUniqueId()))
                                            .forEach(p -> builder.suggest(p.getName()));
                                    return builder.buildFuture();
                                })
                                .executes(this::add)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    plugin.getPlayersNotYetKilled().forEach(id -> builder.suggest(plugin.getPlayerName(id)));
                                    return builder.buildFuture();
                                })
                                .executes(this::remove)));
    }

    /// puts a player on the immortals list, even if they've died or never joined
    private int add(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "player");

        CommandUtil.lookupUuid(plugin, name, id -> {
            if (id == null) {
                sender.sendMessage(Component.text("No player named " + name, NamedTextColor.RED));
            } else if (plugin.revivePlayer(id)) {
                sender.sendMessage(plugin.getPlayerName(id) + " has been added to the immortals.");
            } else {
                sender.sendMessage(Component.text(plugin.getPlayerName(id) + " is already an immortal.", NamedTextColor.RED));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    /// marks an immortal as dead, same as their first death would
    private int remove(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "player");

        UUID id = plugin.getPlayersNotYetKilled().stream()
                .filter(immortal -> plugin.getPlayerName(immortal).equalsIgnoreCase(name))
                .findFirst().orElse(null);
        if (id == null) {
            sender.sendMessage(Component.text(name + " isn't an immortal.", NamedTextColor.RED));
            return 0;
        }

        plugin.addPlayerToKilled(id);
        sender.sendMessage(plugin.getPlayerName(id) + " has been removed from the immortals.");
        return Command.SINGLE_SUCCESS;
    }
}
