package net.shinyshoe.journalism.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.shinyshoe.journalism.Journalism;
import net.shinyshoe.journalism.inventory.PageStore;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

final class PageCommand {

    static final String PERMISSION = "journalism.manage";

    private static final List<String> LEVELS = List.of("section", "category", "entry");
    private static final int ENTRY_DEPTH = 3;
    private static final List<String> ENTRY_ACTIONS = List.of("create", "open", "edit", "delete");
    private static final String CONFIRM = "confirm";
    private static final String NEW_NAME = "new_name";
    private static final String MATERIAL = "material";
    private static final String NAME = "name";

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    static final List<Component> HELP = Stream.of(
            "<yellow>/journal section \\<create|open|delete> \\<section>",
            "<yellow>/journal category \\<create|open|delete> \\<section> \\<category>",
            "<yellow>/journal \\<section|category> rename \\<names> \\<new name>",
            "<yellow>/journal entry \\<create|open|edit|delete> \\<section> \\<category> \\<entry> <dark_gray>- <white>Does nothing yet",
            "<yellow>/journal \\<section|category|entry> item set material \\<names> [material] <dark_gray>- <white>Item in hand if left out",
            "<yellow>/journal \\<section|category|entry> item set name \\<names> \\<name>"
    ).map(MINI_MESSAGE::deserialize).toList();

    private enum Action {
        CREATE,
        OPEN,
        RENAME,
        DELETE
    }

    private final Journalism plugin;

    private PageCommand(final Journalism plugin) {
        this.plugin = plugin;
    }

    static List<LiteralArgumentBuilder<CommandSourceStack>> nodes(final Journalism plugin) {
        final PageCommand command = new PageCommand(plugin);
        return Stream.of(1, 2, ENTRY_DEPTH).map(command::node).toList();
    }

    private LiteralArgumentBuilder<CommandSourceStack> node(final int depth) {
        final LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(LEVELS.get(depth - 1))
                .requires(source -> source.getSender().hasPermission(PERMISSION));

        if (depth == ENTRY_DEPTH) {
            for (final String action : ENTRY_ACTIONS) {
                node.then(Commands.literal(action).then(names(depth, true, last -> last.executes(this::nothingYet))));
            }
        } else {
            for (final Action action : Action.values()) {
                node.then(Commands.literal(action.name().toLowerCase(Locale.ROOT)).then(names(depth, action != Action.CREATE, last -> {
                    switch (action) {
                        case RENAME -> last.then(Commands.argument(NEW_NAME, StringArgumentType.word())
                                .executes(context -> run(context, action, depth, false)));
                        case DELETE -> last.executes(context -> run(context, action, depth, false))
                                .then(Commands.literal(CONFIRM).executes(context -> run(context, action, depth, true)));
                        default -> last.executes(context -> run(context, action, depth, false));
                    }
                })));
            }
        }

        return node.then(Commands.literal("item").then(Commands.literal("set")
                .then(Commands.literal(MATERIAL).then(names(depth, true, last -> last
                        .executes(context -> setMaterial(context, depth, false))
                        .then(Commands.argument(MATERIAL, ArgumentTypes.resource(RegistryKey.ITEM))
                                .executes(context -> setMaterial(context, depth, true))))))
                .then(Commands.literal(NAME).then(names(depth, true, last -> last
                        .then(Commands.argument(NAME, StringArgumentType.greedyString())
                                .executes(context -> setName(context, depth))))))));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> names(final int depth, final boolean suggestLast,
                                                                      final Consumer<RequiredArgumentBuilder<CommandSourceStack, String>> last) {
        RequiredArgumentBuilder<CommandSourceStack, String> node = null;
        for (int level = depth - 1; level >= 0; level--) {
            final int parents = level;
            final RequiredArgumentBuilder<CommandSourceStack, String> argument =
                    Commands.argument(LEVELS.get(level), StringArgumentType.word());
            if (node != null || suggestLast) {
                argument.suggests((context, builder) -> suggest(context, builder, parents));
            }
            if (node == null) {
                last.accept(argument);
            } else {
                argument.then(node);
            }
            node = argument;
        }
        return node;
    }

    private CompletableFuture<Suggestions> suggest(final CommandContext<CommandSourceStack> context,
                                                   final SuggestionsBuilder builder, final int parents) {
        final List<String> path = path(context, parents);
        if (path.stream().allMatch(PageStore::isValidName)) {
            plugin.getPageStore().children(path).stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase()))
                    .forEach(builder::suggest);
        }
        return builder.buildFuture();
    }

    private int run(final CommandContext<CommandSourceStack> context, final Action action, final int depth,
                    final boolean confirmed) throws CommandSyntaxException {
        final CommandSender sender = context.getSource().getSender();
        final List<String> path = path(context, depth);
        if (!areValidNames(sender, path)) return 0;
        try {
            return switch (action) {
                case CREATE -> create(context, path);
                case OPEN -> open(context, path);
                case RENAME -> rename(sender, path, StringArgumentType.getString(context, NEW_NAME));
                case DELETE -> delete(sender, path, confirmed);
            };
        } catch (final IOException e) {
            return failed(context, e);
        }
    }

    private int nothingYet(final CommandContext<CommandSourceStack> context) {
        context.getSource().getSender().sendMessage(MINI_MESSAGE.deserialize("<gray>That entry command does nothing yet."));
        return Command.SINGLE_SUCCESS;
    }

    private int create(final CommandContext<CommandSourceStack> context, final List<String> path) throws IOException {
        final CommandSender sender = context.getSource().getSender();
        final PageStore store = plugin.getPageStore();
        final List<String> parent = path.subList(0, path.size() - 1);
        if (!store.exists(parent)) return tell(sender, "<red>There is no <page>.", parent);
        if (store.exists(path)) return tell(sender, "<red>There is already a <page>.", path);

        store.create(path);
        final ItemStack held = heldItem(context);
        if (held != null) store.setItem(path, held);
        tell(sender, "<green>Created <page>.", path);
        return reload(sender);
    }

    private int open(final CommandContext<CommandSourceStack> context, final List<String> path) throws CommandSyntaxException {
        final CommandSender sender = context.getSource().getSender();
        if (!plugin.getPageStore().exists(path)) return tell(sender, "<red>There is no <page>.", path);
        if (!plugin.getInventoryManager().goTo(context.getSource().getPlayerOrThrow(), PageStore.id(path))) {
            return tell(sender, "<red>The <page> could not be loaded, see the console.", path);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int rename(final CommandSender sender, final List<String> path, final String name) throws IOException {
        final PageStore store = plugin.getPageStore();
        if (!store.exists(path)) return tell(sender, "<red>There is no <page>.", path);
        final List<String> renamed = Stream.concat(path.subList(0, path.size() - 1).stream(), Stream.of(name)).toList();
        if (!areValidNames(sender, renamed)) return 0;
        if (store.exists(renamed)) return tell(sender, "<red>There is already a <page>.", renamed);

        final int links = store.rename(path, name);
        plugin.getInventoryManager().renamePage(PageStore.id(path), PageStore.id(renamed));
        sender.sendMessage(MINI_MESSAGE.deserialize("<green>Renamed <page> to <name>."
                        + (links == 0 ? "" : " Updated " + links + (links == 1 ? " link" : " links") + " to it."),
                Placeholder.unparsed("page", page(path)),
                Placeholder.unparsed(NAME, name)));
        return reload(sender);
    }

    private int delete(final CommandSender sender, final List<String> path, final boolean confirmed) throws IOException {
        final PageStore store = plugin.getPageStore();
        if (!store.exists(path)) return tell(sender, "<red>There is no <page>.", path);

        final int inside = store.count(path);
        if (inside > 0 && !confirmed) {
            return tell(sender, "<yellow>The <page> has " + inside + (inside == 1 ? " folder" : " folders")
                    + " inside. Add " + CONFIRM + " to the command to delete it with everything in it.", path);
        }

        store.delete(path);
        tell(sender, "<green>Deleted <page>.", path);
        return reload(sender);
    }

    private int setMaterial(final CommandContext<CommandSourceStack> context, final int depth, final boolean named) {
        final CommandSender sender = context.getSource().getSender();
        final List<String> path = path(context, depth);
        if (!areValidNames(sender, path)) return 0;
        if (!plugin.getPageStore().exists(path)) return tell(sender, "<red>There is no <page>.", path);

        final ItemStack stack = named ? context.getArgument(MATERIAL, ItemType.class).createItemStack() : heldItem(context);
        if (stack == null || stack.isEmpty()) {
            return tell(sender, named ? "<red>That material cannot be shown as an item."
                    : "<red>Hold an item, or add a material to the command.", path);
        }

        try {
            plugin.getPageStore().setItem(path, stack);
        } catch (final IOException e) {
            return failed(context, e);
        }
        sender.sendMessage(MINI_MESSAGE.deserialize("<green>The <page> is now shown as <material>.",
                Placeholder.unparsed("page", page(path)),
                Placeholder.unparsed(MATERIAL, stack.getType().getKey().getKey())));
        return reload(sender);
    }

    private int setName(final CommandContext<CommandSourceStack> context, final int depth) {
        final CommandSender sender = context.getSource().getSender();
        final List<String> path = path(context, depth);
        if (!areValidNames(sender, path)) return 0;
        if (!plugin.getPageStore().exists(path)) return tell(sender, "<red>There is no <page>.", path);

        final String name = StringArgumentType.getString(context, NAME);
        try {
            plugin.getPageStore().setName(path, name);
        } catch (final IOException e) {
            return failed(context, e);
        }
        sender.sendMessage(MINI_MESSAGE.deserialize("<green>The <page> is now named <reset><name><green>.",
                Placeholder.unparsed("page", page(path)),
                Placeholder.parsed(NAME, name)));
        return reload(sender);
    }

    private int reload(final CommandSender sender) {
        final int problems = plugin.getInventoryManager().reload();
        if (problems > 0) {
            sender.sendMessage(MINI_MESSAGE.deserialize("<yellow>The journal now has " + problems
                    + (problems == 1 ? " problem" : " problems") + ", see the console."));
        }
        return Command.SINGLE_SUCCESS;
    }

    @Nullable
    private static ItemStack heldItem(final CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getExecutor() instanceof Player player)) return null;
        final ItemStack held = player.getInventory().getItemInMainHand();
        return held.isEmpty() ? null : held;
    }

    private static List<String> path(final CommandContext<CommandSourceStack> context, final int depth) {
        return LEVELS.subList(0, depth).stream().map(name -> StringArgumentType.getString(context, name)).toList();
    }

    private static boolean areValidNames(final CommandSender sender, final List<String> path) {
        if (path.stream().allMatch(PageStore::isValidName)) return true;
        sender.sendMessage(MINI_MESSAGE.deserialize("<red>Folder names can only use letters, numbers, _ and -."));
        return false;
    }

    private int failed(final CommandContext<CommandSourceStack> context, final IOException e) {
        plugin.getLogger().warning("/" + context.getInput() + " failed: " + e);
        context.getSource().getSender().sendMessage(MINI_MESSAGE.deserialize("<red>That did not work, see the console."));
        return 0;
    }

    private static String page(final List<String> path) {
        return path.isEmpty() ? "data folder" : LEVELS.get(path.size() - 1) + " " + String.join("/", path);
    }

    private static int tell(final CommandSender sender, final String message, final List<String> path) {
        sender.sendMessage(MINI_MESSAGE.deserialize(message, Placeholder.unparsed("page", page(path))));
        return 0;
    }
}
