package us.dingl.VowSMPPlugin.Commands.Vow;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.concurrent.CompletableFuture;

public class LeaderboardSubCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public LeaderboardSubCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("leaderboard")
                .requires(source -> source.getSender().hasPermission("vow.config"))
                .then(Commands.literal("setpos")
                        .then(coordArg("x", 0)
                                .then(coordArg("y", 1)
                                        .then(coordArg("z", 2).executes(this::setPos)))))
                .then(Commands.literal("getpos").executes(this::getPos))
                .then(Commands.literal("toggle").executes(this::toggle))
                .then(Commands.literal("getenabled").executes(this::getEnabled));
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

    private int getPos(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Location location = plugin.getLeaderboardLocation();
        if (location == null) {
            sender.sendMessage("No worlds are loaded.");
            return 0;
        }
        sender.sendMessage("Leaderboard position: " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ());
        return Command.SINGLE_SUCCESS;
    }

    private int toggle(CommandContext<CommandSourceStack> ctx) {
        boolean enabled = plugin.toggleLeaderboard();
        ctx.getSource().getSender().sendMessage("Leaderboard has been " + (enabled ? "enabled" : "disabled") + ".");
        return Command.SINGLE_SUCCESS;
    }

    private int getEnabled(CommandContext<CommandSourceStack> ctx) {
        boolean enabled = plugin.getLeaderboardEnabled();
        ctx.getSource().getSender().sendMessage("Leaderboard is " + (enabled ? "enabled" : "disabled") + ".");
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
