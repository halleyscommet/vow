package us.dingl.VowSMPPlugin.Commands.Vow;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.Permissions;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.Impl.GuruVow;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/// /vow trust [player] - lists, or toggles, who the Guru vow's smite ignores
public class TrustSubCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public TrustSubCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("trust")
                .requires(CommandUtil.vowHolder(plugin, GuruVow.class, Permissions.TRUST))
                .executes(this::list)
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            if (ctx.getSource().getSender() instanceof Player player
                                    && plugin.getVowManager().getVow(player.getUniqueId()) instanceof GuruVow guru) {
                                // trusted players too, so offline ones can be untrusted
                                guru.getTrusted(player.getUniqueId()).forEach(id -> builder.suggest(plugin.getPlayerName(id)));
                            }
                            Bukkit.getOnlinePlayers().stream()
                                    .filter(p -> !p.equals(ctx.getSource().getSender()))
                                    .forEach(p -> builder.suggest(p.getName()));
                            return builder.buildFuture();
                        })
                        .executes(this::toggle));
    }

    private int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player player = CommandUtil.requirePlayer(ctx);
        GuruVow guru = requireGuru(player);
        if (guru == null) return 0;

        Set<UUID> trusted = guru.getTrusted(player.getUniqueId());
        if (trusted.isEmpty()) {
            player.sendMessage(Component.text("You don't trust anyone. Use /vow trust <player> to add someone.", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("Trusted: " + trusted.stream()
                    .map(plugin::getPlayerName)
                    .collect(Collectors.joining(", ")), NamedTextColor.YELLOW));
        }
        return Command.SINGLE_SUCCESS;
    }

    /// trusts them, or untrusts them if they already were
    private int toggle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player player = CommandUtil.requirePlayer(ctx);
        if (requireGuru(player) == null) return 0;
        String name = StringArgumentType.getString(ctx, "player");

        CommandUtil.lookupUuid(plugin, name, id -> {
            if (id == null) {
                player.sendMessage(Component.text("No player named " + name, NamedTextColor.RED));
                return;
            }
            if (id.equals(player.getUniqueId())) {
                player.sendMessage(Component.text("You can't trust yourself.", NamedTextColor.RED));
                return;
            }
            // the lookup took a moment - make sure he's still a guru
            GuruVow guru = requireGuru(player);
            if (guru == null) return;

            boolean trust = !guru.getTrusted(player.getUniqueId()).contains(id);
            guru.setTrusted(player.getUniqueId(), id, trust);
            player.sendMessage(Component.text(trust
                    ? "Trusted " + plugin.getPlayerName(id) + ". Your smite won't target them."
                    : "Stopped trusting " + plugin.getPlayerName(id) + ".", NamedTextColor.YELLOW));
        });
        return Command.SINGLE_SUCCESS;
    }

    private GuruVow requireGuru(Player player) {
        if (plugin.getVowManager().getVow(player.getUniqueId()) instanceof GuruVow guru) {
            return guru;
        }
        player.sendMessage(Component.text("Only the Guru vow can trust players.", NamedTextColor.RED));
        return null;
    }
}
