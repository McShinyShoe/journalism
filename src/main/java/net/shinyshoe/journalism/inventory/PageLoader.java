package net.shinyshoe.journalism.inventory;

import net.kyori.adventure.text.minimessage.ParsingException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

final class PageLoader {

    static final String FOLDER = "data";

    private static final String ID_ROOT = "page";
    private static final String LAYOUT_FILE = "layout.yml";
    private static final String ITEM_FILE = "item.yml";
    private static final String SORT_PRIORITY = "sort_priority";
    private static final int ENTRY_DEPTH = 3;

    private static final Comparator<Listing> ORDER = Comparator
            .comparing(Listing::sortPriority, Comparator.nullsLast(Comparator.<Integer>naturalOrder()))
            .thenComparing(Listing::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Listing::name);

    private final Consumer<String> warn;
    private final List<InventoryLayout> layouts = new ArrayList<>();

    private PageLoader(final Consumer<String> warn) {
        this.warn = warn;
    }

    static List<InventoryLayout> load(final File folder, final Consumer<String> warn) {
        final PageLoader loader = new PageLoader(warn);
        loader.loadChildren(folder, ID_ROOT, 1);
        return loader.layouts;
    }

    private List<InventoryLayout.Item> loadChildren(final File directory, final String id, final int depth) {
        final File[] children = directory.listFiles(File::isDirectory);
        if (children == null) return List.of();
        return Arrays.stream(children)
                .map(child -> load(child, id + "/" + child.getName(), depth))
                .filter(Objects::nonNull)
                .sorted(ORDER)
                .map(Listing::item)
                .toList();
    }

    @Nullable
    private Listing load(final File directory, final String id, final int depth) {
        try {
            final YamlConfiguration item = read(directory, ITEM_FILE);
            if (item.contains(SORT_PRIORITY) && !item.isInt(SORT_PRIORITY)) {
                throw new IllegalArgumentException(ITEM_FILE + " has a " + SORT_PRIORITY + " that is not a whole number");
            }
            // entries have no inventory to go to yet
            final Listing listing = new Listing(
                    directory.getName(),
                    InventoryLayout.Item.parse(ITEM_FILE, item).withGoTo(id),
                    item.contains(SORT_PRIORITY) ? item.getInt(SORT_PRIORITY) : null
            );

            if (depth < ENTRY_DEPTH) {
                final InventoryLayout layout = InventoryLayout.parse(id, read(directory, LAYOUT_FILE));
                layouts.add(layout.withChildren(loadChildren(directory, id, depth + 1)));
            }
            return listing;
        } catch (final IllegalArgumentException | ParsingException e) {
            warn.accept("Skipping " + id + ": " + e.getMessage());
            return null;
        }
    }

    private static YamlConfiguration read(final File directory, final String name) {
        final File file = new File(directory, name);
        if (!file.isFile()) {
            throw new IllegalArgumentException(name + " is missing");
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private record Listing(String name, InventoryLayout.Item item, @Nullable Integer sortPriority) {
    }
}
