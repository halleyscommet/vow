package us.dingl.VowSMPPlugin.Commands.Admin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import us.dingl.VowSMPPlugin.Commands.Vow.CommandUtil;
import us.dingl.VowSMPPlugin.Commands.Vow.SubCommand;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

/// /vow admin kills - the kills leaderboard, plus fixing someone's kill count
public class KillsAdminCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public KillsAdminCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return LeaderboardCommands.build("kills", "Kills leaderboard", plugin.getKillsLeaderboard())
                .then(Commands.literal("set")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    plugin.getLoggedPlayers().forEach(id -> builder.suggest(plugin.getPlayerName(id)));
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("kills", IntegerArgumentType.integer(0))
                                        .executes(this::set))));
    }

    private int set(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "player");
        int kills = IntegerArgumentType.getInteger(ctx, "kills");

        CommandUtil.lookupUuid(plugin, name, id -> {
            if (id == null) {
                sender.sendMessage(Component.text("No player named " + name, NamedTextColor.RED));
                return;
            }
            plugin.setPlayerKills(id, kills);
            sender.sendMessage(plugin.getPlayerName(id) + " now has " + kills + " kills.");
        });
        return Command.SINGLE_SUCCESS;
    }
}
