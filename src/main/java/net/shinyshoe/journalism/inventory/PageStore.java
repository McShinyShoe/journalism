package net.shinyshoe.journalism.inventory;

import net.shinyshoe.journalism.Journalism;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class PageStore {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]+");
    private static final String TEMPLATE_FOLDER = "template";
    private static final String NAME_PLACEHOLDER = "%name%";
    private static final List<String> LEVELS = List.of("section", "category");

    private final Journalism plugin;

    public PageStore(final Journalism plugin) {
        this.plugin = plugin;
    }

    public static boolean isValidName(final String name) {
        return NAME.matcher(name).matches();
    }

    public static String id(final List<String> path) {
        return PageLoader.ID_ROOT + "/" + String.join("/", path);
    }

    public boolean exists(final List<String> path) {
        return Files.isDirectory(folder(path));
    }

    public List<String> children(final List<String> path) {
        try (Stream<Path> files = Files.list(folder(path))) {
            return files.filter(Files::isDirectory).map(file -> file.getFileName().toString()).sorted().toList();
        } catch (final IOException e) {
            return List.of();
        }
    }

    public void create(final List<String> path) throws IOException {
        final Path folder = Files.createDirectory(folder(path));
        final String template = TEMPLATE_FOLDER + "/" + LEVELS.get(path.size() - 1) + "/";
        for (final String file : List.of(PageLoader.ITEM_FILE, PageLoader.LAYOUT_FILE)) {
            try (InputStream input = plugin.getResource(template + file)) {
                if (input == null) throw new IOException(template + file + " is not in the plugin jar");
                final String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                Files.writeString(folder.resolve(file), text.replace(NAME_PLACEHOLDER, path.getLast()));
            }
        }
    }

    // moves the folder and points every goto at its new id, returns how many were changed
    public int rename(final List<String> path, final String name) throws IOException {
        final List<String> renamed = new ArrayList<>(path);
        renamed.set(renamed.size() - 1, name);
        Files.move(folder(path), folder(renamed));

        // the id itself or anything below it, in block or flow style, quoted or not
        final Pattern links = Pattern.compile("(goto[\"']?\\s*:\\s*[\"']?)" + Pattern.quote(id(path)) + "(?=[/\"'\\s,}]|$)");
        final String replacement = "$1" + Matcher.quoteReplacement(id(renamed));
        int changed = 0;
        for (final Path file : layoutFiles()) {
            final String text = Files.readString(file);
            final Matcher matcher = links.matcher(text);
            final int found = (int) matcher.results().count();
            if (found == 0) continue;
            Files.writeString(file, matcher.replaceAll(replacement));
            changed += found;
        }
        return changed;
    }

    private List<Path> layoutFiles() throws IOException {
        final List<Path> files = new ArrayList<>();
        final Path inventories = plugin.getDataFolder().toPath().resolve(InventoryManager.FILE_NAME);
        if (Files.isRegularFile(inventories)) files.add(inventories);
        try (Stream<Path> found = Files.walk(folder(List.of()), LEVELS.size() + 1)) {
            found.filter(file -> file.getFileName().toString().equals(PageLoader.LAYOUT_FILE)).forEach(files::add);
        }
        return files;
    }

    // material and head only, the rest of item.yml is kept
    public void setItem(final List<String> path, final ItemStack stack) throws IOException {
        editItem(path, item -> {
            item.set(InventoryLayout.Item.MATERIAL, stack.getType().getKey().getKey());
            item.set(InventoryLayout.Item.HEAD, InventoryLayout.Item.headOf(stack));
        });
    }

    public void setName(final List<String> path, final String name) throws IOException {
        editItem(path, item -> item.set(InventoryLayout.Item.NAME, name));
    }

    private void editItem(final List<String> path, final Consumer<YamlConfiguration> change) throws IOException {
        final File file = folder(path).resolve(PageLoader.ITEM_FILE).toFile();
        final YamlConfiguration item = new YamlConfiguration();
        try {
            if (file.exists()) item.load(file);
        } catch (final InvalidConfigurationException e) {
            throw new IOException(file + " is not valid YAML", e);
        }
        change.accept(item);
        item.save(file);
    }

    // folders inside, at any depth
    public int count(final List<String> path) throws IOException {
        try (Stream<Path> files = Files.walk(folder(path))) {
            return (int) files.filter(Files::isDirectory).count() - 1;
        }
    }

    public void delete(final List<String> path) throws IOException {
        try (Stream<Path> files = Files.walk(folder(path))) {
            for (final Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
    }

    private Path folder(final List<String> path) {
        Path folder = plugin.getDataFolder().toPath().resolve(PageLoader.FOLDER);
        for (final String name : path) {
            if (!isValidName(name)) throw new IllegalArgumentException("'" + name + "' is not a valid folder name");
            folder = folder.resolve(name);
        }
        return folder;
    }
}
