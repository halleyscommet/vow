package us.dingl.VowSMPPlugin.Commands.Admin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.Commands.Vow.CommandUtil;
import us.dingl.VowSMPPlugin.Commands.Vow.SubCommand;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LeaderboardAdminCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public LeaderboardAdminCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("leaderboard")
                .executes(this::status)
                .then(Commands.literal("setpos")
                        .then(coordArg("x", 0)
                                .then(coordArg("y", 1)
                                        .then(coordArg("z", 2).executes(this::setPos)))))
                .then(Commands.literal("toggle").executes(this::toggle))
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

    private int setPos(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        int x = IntegerArgumentType.getInteger(ctx, "x");
        int y = IntegerArgumentType.getInteger(ctx, "y");
        int z = IntegerArgumentType.getInteger(ctx, "z");

        plugin.setLeaderboardPosition(x, y, z);
        sender.sendMessage("Leaderboard position has been set to: " + x + " " + y + " " + z);
        return Command.SINGLE_SUCCESS;
    }

    private int status(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Location location = plugin.getLeaderboardLocation();
        String pos = location == null
                ? "unknown (no worlds loaded)"
                : location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();

        sender.sendMessage("Leaderboard is " + (plugin.getLeaderboardEnabled() ? "enabled" : "disabled") + ".");
        sender.sendMessage("Position: " + pos);
        return Command.SINGLE_SUCCESS;
    }

    private int toggle(CommandContext<CommandSourceStack> ctx) {
        boolean enabled = plugin.toggleLeaderboard();
        ctx.getSource().getSender().sendMessage("Leaderboard has been " + (enabled ? "enabled" : "disabled") + ".");
        return Command.SINGLE_SUCCESS;
    }

    private RequiredArgumentBuilder<CommandSourceStack, Integer> coordArg(String name, int index) {
        return Commands.argument(name, IntegerArgumentType.integer())
                .suggests((ctx, builder) -> suggestCoord(ctx, builder, index));
    }

    private CompletableFuture<Suggestions> suggestCoord(CommandContext<CommandSourceStack> ctx,
                                                        SuggestionsBuilder builder, int index) {
        if (ctx.getSource().getSender() instanceof Player player) {
            var targetBlock = player.getTargetBlockExact(5);
            Location target = targetBlock != null ? targetBlock.getLocation() : player.getLocation();
            int[] coords = {target.getBlockX(), target.getBlockY(), target.getBlockZ()};
            builder.suggest(coords[index]);
        }
        return builder.buildFuture();
    }
}
