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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.Permissions;
import us.dingl.VowSMPPlugin.VowSMPPlugin;
import us.dingl.VowSMPPlugin.Vows.TrustList;
import us.dingl.VowSMPPlugin.Vows.TrustingVow;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/// /vow trust [player] - lists, or toggles, who targeting vows (Guru's smite, Jax's stun) ignore.
/// Someone holding several of those vows has one trust list in practice: toggling changes all of them.
public class TrustSubCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public TrustSubCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("trust")
                .requires(this::canSee)
                .executes(this::list)
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            if (ctx.getSource().getSender() instanceof Player player) {
                                // trusted players too, so offline ones can be untrusted
                                trustedBy(player).forEach(id -> builder.suggest(plugin.getPlayerName(id)));
                            }
                            Bukkit.getOnlinePlayers().stream()
                                    .filter(p -> !p.equals(ctx.getSource().getSender()))
                                    .forEach(p -> builder.suggest(p.getName()));
                            return builder.buildFuture();
                        })
                        .executes(this::toggle));
    }

    /// like CommandUtil.vowHolder, but for any vow that has a trust list
    private boolean canSee(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        if (sender.hasPermission(Permissions.ADMIN)) return true;
        return sender instanceof Player player
                && sender.hasPermission(Permissions.TRUST)
                && !trustLists(player).isEmpty();
    }

    private int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player player = CommandUtil.requirePlayer(ctx);
        if (requireTrustLists(player).isEmpty()) return 0;

        Set<UUID> trusted = trustedBy(player);
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
        if (requireTrustLists(player).isEmpty()) return 0;
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
            // the lookup took a moment - their vows might have changed
            List<TrustList> lists = requireTrustLists(player);
            if (lists.isEmpty()) return;

            // trusted by any of their vows counts as trusted, so the toggle untrusts everywhere
            boolean trust = !trustedBy(player).contains(id);
            lists.forEach(list -> list.setTrusted(player.getUniqueId(), id, trust));
            player.sendMessage(Component.text(trust
                    ? "Trusted " + plugin.getPlayerName(id) + ". Your vow abilities won't target them."
                    : "Stopped trusting " + plugin.getPlayerName(id) + ".", NamedTextColor.YELLOW));
        });
        return Command.SINGLE_SUCCESS;
    }

    private List<TrustList> trustLists(Player player) {
        return plugin.getVowManager().getVows(player.getUniqueId()).stream()
                .filter(vow -> vow instanceof TrustingVow)
                .map(vow -> ((TrustingVow) vow).trustList())
                .toList();
    }

    /// everyone trusted by any of the player's vows
    private Set<UUID> trustedBy(Player player) {
        Set<UUID> all = new LinkedHashSet<>();
        trustLists(player).forEach(list -> all.addAll(list.getTrusted(player.getUniqueId())));
        return all;
    }

    private List<TrustList> requireTrustLists(Player player) {
        List<TrustList> lists = trustLists(player);
        if (lists.isEmpty()) {
            player.sendMessage(Component.text("Only the Guru and Jax vows can trust players.", NamedTextColor.RED));
        }
        return lists;
    }
}
