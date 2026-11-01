package us.dingl.VowSMPPlugin.Commands.Admin;

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
import us.dingl.VowSMPPlugin.Leaderboard.Leaderboard;

import java.util.concurrent.CompletableFuture;

/// the status / setpos / toggle commands every leaderboard has
final class LeaderboardCommands {

    private LeaderboardCommands() {
    }

    /// {@code /vow admin <literal> [setpos x y z | toggle]}. {@code label} names the board in replies.
    static LiteralArgumentBuilder<CommandSourceStack> build(String literal, String label, Leaderboard board) {
        return Commands.literal(literal)
                .executes(ctx -> status(ctx, label, board))
                .then(Commands.literal("setpos")
                        .then(coordArg("x", 0)
                                .then(coordArg("y", 1)
                                        .then(coordArg("z", 2).executes(ctx -> setPos(ctx, label, board))))))
                .then(Commands.literal("toggle").executes(ctx -> {
                    boolean enabled = board.toggle();
                    ctx.getSource().getSender().sendMessage(label + " has been " + (enabled ? "enabled" : "disabled") + ".");
                    return Command.SINGLE_SUCCESS;
                }));
    }

    private static int setPos(CommandContext<CommandSourceStack> ctx, String label, Leaderboard board) {
        int x = IntegerArgumentType.getInteger(ctx, "x");
        int y = IntegerArgumentType.getInteger(ctx, "y");
        int z = IntegerArgumentType.getInteger(ctx, "z");

        board.setPosition(x, y, z);
        ctx.getSource().getSender().sendMessage(label + " position has been set to: " + x + " " + y + " " + z);
        return Command.SINGLE_SUCCESS;
    }

    private static int status(CommandContext<CommandSourceStack> ctx, String label, Leaderboard board) {
        CommandSender sender = ctx.getSource().getSender();
        Location location = board.getLocation();
        String pos = location == null
                ? "unknown (no worlds loaded)"
                : location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();

        sender.sendMessage(label + " is " + (board.isEnabled() ? "enabled" : "disabled") + ".");
        sender.sendMessage("Position: " + pos);
        return Command.SINGLE_SUCCESS;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> coordArg(String name, int index) {
        return Commands.argument(name, IntegerArgumentType.integer())
                .suggests((ctx, builder) -> suggestCoord(ctx, builder, index));
    }

    private static CompletableFuture<Suggestions> suggestCoord(CommandContext<CommandSourceStack> ctx,
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
