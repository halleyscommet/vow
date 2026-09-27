package us.dingl.VowSMPPlugin.Commands.Vow;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class StatsSubCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public StatsSubCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("stats")
                .requires(source -> source.getSender().hasPermission("vow.stats"))
                .then(Commands.literal("nodeaths").executes(ctx -> {
                    sendNames(ctx.getSource().getSender(), "The following players haven't died yet:", plugin.getPlayersNotYetKilled());
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("deaths").executes(ctx -> {
                    sendNames(ctx.getSource().getSender(), "The following players have died:", plugin.getKilledPlayers());
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("allplayers").executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    Set<UUID> yetToDie = new HashSet<>(plugin.getPlayersNotYetKilled());
                    sender.sendMessage("The following players have logged in:");
                    for (UUID id : plugin.getLoggedPlayers()) {
                        NamedTextColor color = yetToDie.contains(id) ? NamedTextColor.GREEN : NamedTextColor.RED;
                        sender.sendMessage(Component.text(plugin.getPlayerName(id), color));
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("lives").executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    sender.sendMessage("Players and their lives:");
                    for (Map.Entry<UUID, Integer> entry : plugin.getPlayerLives().entrySet()) {
                        sender.sendMessage(plugin.getPlayerName(entry.getKey()) + ": " + entry.getValue());
                    }
                    return Command.SINGLE_SUCCESS;
                }));
    }

    private void sendNames(CommandSender sender, String header, Collection<UUID> ids) {
        sender.sendMessage(header);
        for (UUID id : ids) {
            sender.sendMessage(plugin.getPlayerName(id));
        }
    }
}
