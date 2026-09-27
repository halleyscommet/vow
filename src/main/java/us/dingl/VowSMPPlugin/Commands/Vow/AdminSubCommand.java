package us.dingl.VowSMPPlugin.Commands.Vow;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.Items.RitualItem;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Vow;
import us.dingl.VowSMPPlugin.Vows.VowManager;

public class AdminSubCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public AdminSubCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("admin")
                .requires(source -> source.getSender().hasPermission("vow.debug"))
                .then(Commands.literal("flyspeed")
                        .then(Commands.argument("speed", FloatArgumentType.floatArg(-1f, 1f))
                                .executes(ctx -> {
                                    Player player = CommandUtil.requirePlayer(ctx);
                                    float speed = FloatArgumentType.getFloat(ctx, "speed");
                                    player.setFlySpeed(speed);
                                    player.sendMessage("Fly speed set to " + speed);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(Commands.literal("givecurtana")
                        .executes(ctx -> {
                            Player player = CommandUtil.requirePlayer(ctx);
                            player.give(RitualItem.create(1));
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(Commands.literal("setvow")
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .then(Commands.argument("vow", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            plugin.getVowManager().getVows().forEach(v -> builder.suggest(v.id()));
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            Player target = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                                                    .resolve(ctx.getSource()).getFirst();
                                            String vowId = StringArgumentType.getString(ctx, "vow");
                                            VowManager vows = plugin.getVowManager();
                                            Vow vow = vows.getVowById(vowId);
                                            if (vow == null) {
                                                ctx.getSource().getSender().sendMessage("Unknown vow: " + vowId);
                                                return 0;
                                            }
                                            vows.setVow(target.getUniqueId(), vow);
                                            ctx.getSource().getSender().sendMessage(target.getName() + " now has " + vow.name());
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )
                .then(Commands.literal("clearvow")
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .executes(ctx -> {
                                    Player target = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                                            .resolve(ctx.getSource()).getFirst();
                                    plugin.getVowManager().setVow(target.getUniqueId(), null);
                                    ctx.getSource().getSender().sendMessage(target.getName() + " no longer has a vow");
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                );
    }
}
