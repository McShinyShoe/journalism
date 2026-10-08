package net.shinyshoe.journalism.entry;

import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum EntryType {
    // highest priority first
    INVENTORY("inventory.yml"),
    BOOK("book.yml"),
    SIGN("sign.yml"),
    NONE(null);

    public static final List<String> NAMES = Arrays.stream(values()).map(EntryType::commandName).toList();

    @Nullable
    private final String fileName;

    EntryType(@Nullable final String fileName) {
        this.fileName = fileName;
    }

    public static EntryType of(final Path folder) {
        for (final EntryType type : values()) {
            if (type.fileName != null && Files.isRegularFile(folder.resolve(type.fileName))) return type;
        }
        return NONE;
    }

    @Nullable
    public static EntryType byName(final String name) {
        for (final EntryType type : values()) {
            if (type.commandName().equals(name.toLowerCase(Locale.ROOT))) return type;
        }
        return null;
    }

    public String commandName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public String fileName() {
        return fileName;
    }
}
