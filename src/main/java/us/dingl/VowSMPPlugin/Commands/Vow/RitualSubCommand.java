package us.dingl.VowSMPPlugin.Commands.Vow;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.Ritual.FakeLightning;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

public class RitualSubCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public RitualSubCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("ritual")
                .requires(source -> source.getSender().hasPermission("vow.debug"))
                .then(Commands.literal("start")
                        .executes(ctx -> {
                            Player player = CommandUtil.requirePlayer(ctx);
                            if (!plugin.getStorm().start(player, false)) {
                                player.sendMessage("A storm is already active. Use /vow ritual stop first.");
                                return 0;
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("stop")
                        .executes(ctx -> {
                            plugin.getStorm().stop();
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
