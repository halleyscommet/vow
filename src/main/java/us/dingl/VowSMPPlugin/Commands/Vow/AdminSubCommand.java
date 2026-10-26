package us.dingl.VowSMPPlugin.Commands.Vow;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import us.dingl.VowSMPPlugin.Mythics.MythicManager;
import us.dingl.VowSMPPlugin.Mythics.MythicManager.Mythic;
import us.dingl.VowSMPPlugin.Permissions;
import us.dingl.VowSMPPlugin.VowSMPPlugin;

/// /vow admin makemythic - turns the held item into a tracked mythic
public class AdminSubCommand implements SubCommand {

    private final VowSMPPlugin plugin;

    public AdminSubCommand(VowSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("admin")
                .requires(CommandUtil.permission(Permissions.MYTHICS_MANAGE))
                .then(Commands.literal("makemythic").executes(this::makeMythic));
    }

    private int makeMythic(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player player = CommandUtil.requirePlayer(ctx);
        MythicManager mythics = plugin.getMythicManager();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.isEmpty()) {
            player.sendMessage(Component.text("Hold the item you want to make mythic.", NamedTextColor.RED));
            return 0;
        }
        // the uuid goes on the whole stack, so a stack would be several "one-of-a-kind" items
        if (item.getAmount() > 1) {
            player.sendMessage(Component.text("Hold a single item, not a stack.", NamedTextColor.RED));
            return 0;
        }
        if (mythics.getId(item) != null) {
            player.sendMessage(Component.text("That's already a mythic.", NamedTextColor.RED));
            return 0;
        }

        Mythic mythic = mythics.makeMythic(item, player);
        player.getInventory().setItemInMainHand(item);
        player.sendMessage(Component.text("Made ", NamedTextColor.YELLOW)
                .append(mythic.name())
                .append(Component.text(" a mythic (" + mythic.id() + ").", NamedTextColor.YELLOW)));
        return Command.SINGLE_SUCCESS;
    }
}
