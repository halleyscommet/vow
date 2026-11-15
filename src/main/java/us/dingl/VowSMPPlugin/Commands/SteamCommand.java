package us.dingl.VowSMPPlugin.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.Commands.Vow.CommandUtil;
import us.dingl.VowSMPPlugin.Easter.SteamVents;

import java.util.List;
import java.util.function.BiFunction;

/// /steam add|remove [x y z] (the block you're looking at by default), /steam list, /steam clear,
/// /steam interval <ticks>. Anyone SteamVents doesn't allow doesn't see the command at all.
public final class SteamCommand {

    private static final int REACH = 10;

    private final SteamVents vents;

    public SteamCommand(SteamVents vents) {
        this.vents = vents;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("steam")
                .requires(source -> source.getSender() instanceof Player player && SteamVents.isAllowed(player))
                .then(blockCommand("add", (v, b) -> v.add(b), "Now steaming: ", "Already a steam source: "))
                .then(blockCommand("remove", (v, b) -> v.remove(b), "Stopped steaming: ", "Not a steam source: "))
                .then(Commands.literal("list").executes(ctx -> {
                    Player player = CommandUtil.requirePlayer(ctx);
                    List<String> sources = vents.list();
                    if (sources.isEmpty()) {
                        player.sendMessage(Component.text("No steam sources.", NamedTextColor.GRAY));
                    } else {
                        player.sendMessage(Component.text("Steam sources (every " + vents.getInterval() + " ticks):", NamedTextColor.YELLOW));
                        sources.forEach(s -> player.sendMessage(Component.text(" - " + s.replace(",", " "), NamedTextColor.GRAY)));
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("clear").executes(ctx -> {
                    Player player = CommandUtil.requirePlayer(ctx);
                    player.sendMessage(Component.text("Removed " + vents.clear() + " steam sources.", NamedTextColor.YELLOW));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("interval")
                        .executes(ctx -> {
                            CommandUtil.requirePlayer(ctx).sendMessage(Component.text(
                                    "Steam puffs every " + vents.getInterval() + " ticks.", NamedTextColor.YELLOW));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 20 * 60 * 60))
                                .executes(ctx -> {
                                    int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
                                    vents.setInterval(ticks);
                                    CommandUtil.requirePlayer(ctx).sendMessage(Component.text(
                                            "Steam now puffs every " + ticks + " ticks.", NamedTextColor.YELLOW));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    private LiteralArgumentBuilder<CommandSourceStack> blockCommand(String name, BiFunction<SteamVents, Block, Boolean> action,
                                                                     String done, String unchanged) {
        return Commands.literal(name)
                .executes(ctx -> {
                    Player player = CommandUtil.requirePlayer(ctx);
                    Block block = player.getTargetBlockExact(REACH);
                    if (block == null) {
                        player.sendMessage(Component.text("Look at a block within " + REACH + " blocks, or give x y z.", NamedTextColor.RED));
                        return 0;
                    }
                    return run(player, block, action, done, unchanged);
                })
                .then(Commands.argument("pos", ArgumentTypes.blockPosition())
                        .executes(ctx -> {
                            Player player = CommandUtil.requirePlayer(ctx);
                            Block block = resolve(ctx).toLocation(player.getWorld()).getBlock();
                            return run(player, block, action, done, unchanged);
                        }));
    }

    private static BlockPosition resolve(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getArgument("pos", BlockPositionResolver.class).resolve(ctx.getSource());
    }

    private int run(Player player, Block block, BiFunction<SteamVents, Block, Boolean> action, String done, String unchanged) {
        boolean changed = action.apply(vents, block);
        String where = block.getX() + " " + block.getY() + " " + block.getZ() + " (" + block.getType().getKey().getKey() + ")";
        player.sendMessage(Component.text((changed ? done : unchanged) + where, changed ? NamedTextColor.YELLOW : NamedTextColor.RED));
        return changed ? Command.SINGLE_SUCCESS : 0;
    }
}
