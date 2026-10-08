package net.shinyshoe.journalism.entry;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.shinyshoe.journalism.Journalism;
import net.shinyshoe.journalism.inventory.PageStore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EntryManager implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Journalism plugin;
    private final SignPrompt signPrompt;
    private final NamespacedKey editingKey;
    // chat arrives on another thread, the texts are only touched on the main one
    private final Map<UUID, ChatEdit> chatEdits = new ConcurrentHashMap<>();

    public EntryManager(final Journalism plugin) {
        this.plugin = plugin;
        this.signPrompt = new SignPrompt(plugin);
        this.editingKey = new NamespacedKey(plugin, "editing");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(final Player player, final Entry entry) {
        switch (entry) {
            case Entry.TextBook book -> {
                player.closeInventory();
                player.openBook(book.book());
            }
            case Entry.SignText sign -> {
                player.closeInventory();
                signPrompt.open(player, sign.lines(), null);
            }
            case Entry.None ignored -> {
            }
        }
    }

    public void close() {
        signPrompt.closeAll();
    }

    // false when the inventory is full
    public boolean giveEditBook(final Player player, final List<String> path) {
        final List<String> pages = EntryFiles.readPages(folder(path));
        final ItemStack book = ItemStack.of(Material.WRITABLE_BOOK);
        book.editMeta(BookMeta.class, meta -> {
            // a page that is too long to edit is spread over several
            meta.pages(pages.stream().flatMap(page -> BookPages.split(page).stream()).map(page -> (Component) Component.text(page)).toList());
            meta.itemName(Component.text("Editing " + String.join("/", path)));
            meta.getPersistentDataContainer().set(editingKey, PersistentDataType.STRING, String.join("/", path));
        });

        final PlayerInventory inventory = player.getInventory();
        if (inventory.getItemInMainHand().isEmpty()) {
            inventory.setItemInMainHand(book);
            return true;
        }
        return inventory.addItem(book).isEmpty();
    }

    public void editSign(final Player player, final List<String> path) {
        final Path folder = folder(path);
        final List<String> before = EntryFiles.readSign(folder);
        signPrompt.open(player, before, typed -> {
            // the screen sends plain text back, a line that was not touched keeps its formatting
            final List<String> lines = new ArrayList<>();
            for (int line = 0; line < typed.size(); line++) {
                final String old = line < before.size() ? before.get(line) : "";
                lines.add(typed.get(line).equals(PLAIN.serialize(MINI_MESSAGE.deserialize(old))) ? old : typed.get(line));
            }
            save(player, path, () -> EntryFiles.writeSign(folder, lines));
        });
    }

    // what the player says from now on goes to the entry instead of chat
    public void startChatEdit(final Player player, final List<String> path) {
        chatEdits.put(player.getUniqueId(), new ChatEdit(path, EntryType.of(folder(path)), new ArrayList<>()));
    }

    // these three are false when the player is not editing in chat
    public boolean saveChatEdit(final Player player) {
        final ChatEdit edit = chatEdits.remove(player.getUniqueId());
        if (edit == null) return false;
        final List<String> path = edit.path;
        if (edit.texts.isEmpty()) {
            tell(player, "<yellow>Nothing was typed, the <page> was left as it was.", path);
        } else if (!plugin.getPageStore().exists(path) || EntryType.of(folder(path)) != edit.type) {
            tell(player, "<red>The <page> is gone or changed type, nothing was saved.", path);
        } else if (edit.type == EntryType.SIGN) {
            save(player, path, () -> EntryFiles.writeSign(folder(path), edit.texts));
        } else {
            save(player, path, () -> EntryFiles.writePages(folder(path), edit.texts));
        }
        return true;
    }

    public boolean cancelChatEdit(final Player player) {
        final ChatEdit edit = chatEdits.remove(player.getUniqueId());
        if (edit == null) return false;
        tell(player, "<yellow>Stopped editing the <page>, nothing was saved.", edit.path);
        return true;
    }

    public boolean restartChatEdit(final Player player) {
        final ChatEdit edit = chatEdits.get(player.getUniqueId());
        if (edit == null) return false;
        edit.texts.clear();
        tell(player, "<yellow>Started over on the <page>, what you typed so far is dropped.", edit.path);
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAsyncChat(final AsyncChatEvent event) {
        final Player player = event.getPlayer();
        if (!chatEdits.containsKey(player.getUniqueId())) return;
        event.setCancelled(true);
        final String text = event.signedMessage().message();
        plugin.getServer().getScheduler().runTask(plugin, () -> addChat(player, text));
    }

    private void addChat(final Player player, final String text) {
        final ChatEdit edit = chatEdits.get(player.getUniqueId());
        if (edit == null) return;
        edit.texts.add(text);

        final boolean sign = edit.type == EntryType.SIGN;
        player.sendMessage(MINI_MESSAGE.deserialize(sign ? "<gray>Line <number>/" + EntryFiles.SIGN_LINES + ": <reset><text>" : "<gray>Page <number>: <reset><text>",
                Placeholder.unparsed("number", String.valueOf(edit.texts.size())),
                Placeholder.component("text", MINI_MESSAGE.deserialize(text))));
        if (sign && edit.texts.size() == EntryFiles.SIGN_LINES) saveChatEdit(player);
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        chatEdits.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerEditBook(final PlayerEditBookEvent event) {
        final String target = event.getPreviousBookMeta().getPersistentDataContainer().get(editingKey, PersistentDataType.STRING);
        if (target == null) return;
        event.setCancelled(true);

        final Player player = event.getPlayer();
        final PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            final ItemStack item = inventory.getItem(slot);
            if (item != null && target.equals(item.getPersistentDataContainer().get(editingKey, PersistentDataType.STRING))) {
                inventory.setItem(slot, null);
            }
        }

        final List<String> path = List.of(target.split("/"));
        if (!player.hasPermission(Journalism.MANAGE_PERMISSION) || !path.stream().allMatch(PageStore::isValidName)) return;
        if (!plugin.getPageStore().exists(path) || EntryType.of(folder(path)) != EntryType.BOOK) {
            tell(player, "<red>The <page> is gone or not a book any more, nothing was saved.", path);
            return;
        }

        final List<String> pages = event.getNewBookMeta().pages().stream().map(PLAIN::serialize).toList();
        save(player, path, () -> EntryFiles.writePages(folder(path), pages));
    }

    private void save(final Player player, final List<String> path, final Write write) {
        try {
            write.run();
        } catch (final IOException e) {
            plugin.getLogger().warning("Could not save entry " + String.join("/", path) + ": " + e);
            tell(player, "<red>The <page> could not be saved, see the console.", path);
            return;
        }
        plugin.getInventoryManager().reload();
        tell(player, "<green>Saved the <page>.", path);
    }

    private Path folder(final List<String> path) {
        return plugin.getPageStore().folder(path);
    }

    private static void tell(final Player player, final String message, final List<String> path) {
        player.sendMessage(MINI_MESSAGE.deserialize(message, Placeholder.unparsed("page", "entry " + String.join("/", path))));
    }

    @FunctionalInterface
    private interface Write {
        void run() throws IOException;
    }

    private record ChatEdit(List<String> path, EntryType type, List<String> texts) {
    }
}
