package us.dingl.VowSMPPlugin.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import us.dingl.VowSMPPlugin.Chat.GeminiChat;
import us.dingl.VowSMPPlugin.Commands.Vow.CommandUtil;
import us.dingl.VowSMPPlugin.Permissions;

/// /ai <message> talks to the chatbot, /ai reset starts a new conversation
public final class AiCommand {

    private final GeminiChat chat;

    public AiCommand(GeminiChat chat) {
        this.chat = chat;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("ai")
                .requires(CommandUtil.permission(Permissions.AI))
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(Component.text(
                            "Usage: /ai <message>, or /ai reset to start over", NamedTextColor.RED));
                    return 0;
                })
                .then(Commands.literal("reset").executes(ctx -> {
                    Player player = CommandUtil.requirePlayer(ctx);
                    chat.reset(player);
                    player.sendMessage(Component.text("Started a new conversation with the AI.", NamedTextColor.YELLOW));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
                    Player player = CommandUtil.requirePlayer(ctx);
                    chat.ask(player, StringArgumentType.getString(ctx, "message"));
                    return Command.SINGLE_SUCCESS;
                }));
    }
}
