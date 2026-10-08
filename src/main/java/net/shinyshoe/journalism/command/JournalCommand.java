package net.shinyshoe.journalism.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.shinyshoe.journalism.Journalism;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Stream;

public final class JournalCommand {

    private static final String LABEL = "journal";
    private static final String DESCRIPTION = "Open the journal menu";
    private static final String RELOAD_PERMISSION = "journalism.reload";

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Component MENU_UNAVAILABLE = MINI_MESSAGE.deserialize("<red>The journal menu is unavailable.");
    private static final Component RELOADED = MINI_MESSAGE.deserialize("<green>Journal reloaded.");
    private static final List<Component> HELP = Stream.of(
            "<gold>Journalism",
            "<yellow>/journal <dark_gray>- <white>Open the journal menu",
            "<yellow>/journal help <dark_gray>- <white>Show this help"
    ).map(MINI_MESSAGE::deserialize).toList();
    private static final Component HELP_RELOAD = MINI_MESSAGE.deserialize(
            "<yellow>/journal reload <dark_gray>- <white>Load the menus from the files again");

    private final Journalism plugin;

    private JournalCommand(final Journalism plugin) {
        this.plugin = plugin;
    }

    public static void register(final Journalism plugin) {
        final JournalCommand command = new JournalCommand(plugin);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(command.build(), DESCRIPTION));
    }

    private LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(LABEL)
                .executes(this::openMenu)
                .then(Commands.literal("help").executes(this::help))
                .then(Commands.literal("reload")
                        .requires(source -> source.getSender().hasPermission(RELOAD_PERMISSION))
                        .executes(this::reload))
                .build();
    }

    private int openMenu(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final Player player = context.getSource().getPlayerOrThrow();
        if (!plugin.getInventoryManager().open(player)) {
            player.sendMessage(MENU_UNAVAILABLE);
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int help(final CommandContext<CommandSourceStack> context) {
        final CommandSender sender = context.getSource().getSender();
        HELP.forEach(sender::sendMessage);
        if (sender.hasPermission(RELOAD_PERMISSION)) sender.sendMessage(HELP_RELOAD);
        return Command.SINGLE_SUCCESS;
    }

    private int reload(final CommandContext<CommandSourceStack> context) {
        final int problems = plugin.getInventoryManager().reload();
        context.getSource().getSender().sendMessage(problems == 0 ? RELOADED : MINI_MESSAGE.deserialize(
                "<yellow>Journal reloaded with " + problems + (problems == 1 ? " problem" : " problems") + ", see the console."));
        return Command.SINGLE_SUCCESS;
    }
}
