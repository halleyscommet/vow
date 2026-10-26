package us.dingl.VowSMPPlugin.Commands.Admin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.Commands.Vow.CommandUtil;
import us.dingl.VowSMPPlugin.Commands.Vow.SubCommand;
import us.dingl.VowSMPPlugin.Items.RitualItem;
import us.dingl.VowSMPPlugin.Ritual.FakeLightning;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

/// testing tools - nothing here should be needed during normal play
public class DebugAdminCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public DebugAdminCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("debug")
                .then(Commands.literal("flyspeed")
                        .then(Commands.argument("speed", FloatArgumentType.floatArg(-1f, 1f))
                                .executes(ctx -> {
                                    Player player = CommandUtil.requirePlayer(ctx);
                                    float speed = FloatArgumentType.getFloat(ctx, "speed");
                                    player.setFlySpeed(speed);
                                    player.sendMessage("Fly speed set to " + speed);
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("givecurtana")
                        .executes(ctx -> {
                            Player player = CommandUtil.requirePlayer(ctx);
                            player.give(RitualItem.create(1));
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("lightning")
                        .executes(ctx -> {
                            Player player = CommandUtil.requirePlayer(ctx);
                            new FakeLightning().summon(plugin, player);
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
