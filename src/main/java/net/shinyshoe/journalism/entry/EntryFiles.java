package net.shinyshoe.journalism.entry;

import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class EntryFiles {

    public static final int SIGN_LINES = 4;

    private static final String PAGES = "pages";
    private static final String LINES = "lines";
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private EntryFiles() {
    }

    public static Entry load(final EntryType type, final Path folder) {
        return switch (type) {
            case BOOK -> new Entry.TextBook(Book.book(readPages(folder).stream()
                    .map(MINI_MESSAGE::deserialize)
                    .flatMap(page -> BookPages.split(page).stream())
                    .toList()));
            case SIGN -> new Entry.SignText(readSign(folder));
            case INVENTORY, NONE -> new Entry.None();
        };
    }

    static List<String> readPages(final Path folder) {
        return YamlConfiguration.loadConfiguration(folder.resolve(EntryType.BOOK.fileName()).toFile()).getStringList(PAGES);
    }

    static void writePages(final Path folder, final List<String> pages) throws IOException {
        edit(folder.resolve(EntryType.BOOK.fileName()).toFile(), PAGES, pages);
    }

    static List<String> readSign(final Path folder) {
        final List<String> lines = new ArrayList<>(YamlConfiguration.loadConfiguration(folder.resolve(EntryType.SIGN.fileName()).toFile()).getStringList(LINES));
        while (lines.size() < SIGN_LINES) lines.add("");
        return List.copyOf(lines.subList(0, SIGN_LINES));
    }

    static void writeSign(final Path folder, final List<String> lines) throws IOException {
        edit(folder.resolve(EntryType.SIGN.fileName()).toFile(), LINES, lines);
    }

    private static void edit(final File file, final String key, final List<String> value) throws IOException {
        final YamlConfiguration config = new YamlConfiguration();
        try {
            if (file.exists()) config.load(file);
        } catch (final InvalidConfigurationException e) {
            throw new IOException(file + " is not valid YAML", e);
        }
        config.set(key, value);
        config.save(file);
    }
}
