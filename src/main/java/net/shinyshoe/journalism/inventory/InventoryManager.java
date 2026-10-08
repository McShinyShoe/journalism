package net.shinyshoe.journalism.inventory;

import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import net.kyori.adventure.text.minimessage.ParsingException;
import net.shinyshoe.journalism.Journalism;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class InventoryManager {

    private static final String FILE_NAME = "inventory.yml";
    private static final String HOME_ID = "main";

    private final Journalism plugin;
    private final Map<String, InventoryLayout> layouts = new HashMap<>();
    private final Map<String, Map<Character, Consumer<InventoryClickEvent>>> clickHandlers = new HashMap<>();
    private final Map<UUID, Deque<View>> menuStacks = new HashMap<>();
    private int problems;

    public InventoryManager(final Journalism plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(new InventoryListener(), plugin);
        load();
    }

    public int reload() {
        load();
        plugin.getServer().getOnlinePlayers().forEach(this::refresh);
        return problems;
    }

    private void refresh(final Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof LayoutHolder && !open(player)) {
            player.closeInventory();
        }
    }

    private void load() {
        problems = 0;
        final File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }
        final YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        layouts.clear();
        for (final String id : config.getKeys(false)) {
            final ConfigurationSection section = config.getConfigurationSection(id);
            if (section == null) continue;
            try {
                layouts.put(id, InventoryLayout.parse(id, section));
            } catch (final IllegalArgumentException | ParsingException e) {
                warn("Skipping inventory '" + id + "' in " + FILE_NAME + ": " + e.getMessage());
            }
        }

        final File pageFolder = new File(plugin.getDataFolder(), PageLoader.FOLDER);
        if (!pageFolder.exists()) {
            plugin.saveResources(PageLoader.FOLDER);
        }
        for (final InventoryLayout layout : PageLoader.load(pageFolder, this::warn)) {
            layouts.put(layout.id(), layout);
        }

        if (!layouts.containsKey(HOME_ID)) {
            warn("There is no '" + HOME_ID + "' inventory in " + FILE_NAME + ", so /journal has nothing to open");
        }

        for (final InventoryLayout layout : layouts.values()) {
            layout.items().forEach((symbol, item) -> {
                if (item.goTo() != null && !layouts.containsKey(item.goTo())) {
                    warn("Inventory '" + layout.id() + "' item '" + symbol
                            + "' goes to '" + item.goTo() + "', which is not a loaded inventory");
                }
            });
        }
    }

    private void warn(final String message) {
        problems++;
        plugin.getLogger().warning(message);
    }

    // opens the menu the player was last on
    public boolean open(final Player player) {
        final Deque<View> stack = menuStack(player);

        // reset when reloaded
        while (stack.size() > 1 && !layouts.containsKey(stack.peek().id)) stack.pop();

        final View view = stack.peek();
        final InventoryLayout layout = layouts.get(view.id);
        if (layout == null) return false;
        view.page = Math.min(view.page, layout.pageCount() - 1);
        player.openInventory(new LayoutHolder(layout, view.page).getInventory());
        return true;
    }

    public void goTo(final Player player, final String id) {
        if (!layouts.containsKey(id)) return;
        final Deque<View> stack = menuStack(player);
        if (!id.equals(stack.peek().id)) stack.push(new View(id));
        open(player);
    }

    public void back(final Player player) {
        final Deque<View> stack = menuStack(player);
        if (stack.size() == 1) return;
        stack.pop();
        open(player);
    }

    public void home(final Player player) {
        final Deque<View> stack = menuStack(player);
        if (stack.size() == 1) return;
        while (stack.size() > 1) stack.pop();
        open(player);
    }

    public void turnPage(final Player player, final int pages) {
        final View view = menuStack(player).peek();
        final InventoryLayout layout = layouts.get(view.id);
        if (layout == null) return;
        final int page = Math.clamp(view.page + pages, 0, layout.pageCount() - 1);
        if (page == view.page) return;
        view.page = page;
        open(player);
    }

    // home stays at the bottom
    private Deque<View> menuStack(final Player player) {
        return menuStacks.computeIfAbsent(player.getUniqueId(), key -> {
            final Deque<View> stack = new ArrayDeque<>();
            stack.push(new View(HOME_ID));
            return stack;
        });
    }

    public void onClick(final String id, final char symbol, final Consumer<InventoryClickEvent> handler) {
        clickHandlers.computeIfAbsent(id, key -> new HashMap<>()).put(symbol, handler);
    }

    public void closeAll() {
        for (final Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof LayoutHolder) {
                player.closeInventory();
            }
        }
    }

    private static final class View {
        private final String id;
        private int page;

        private View(final String id) {
            this.id = id;
        }
    }

    private static final class LayoutHolder implements InventoryHolder {
        private final InventoryLayout layout;
        private final InventoryLayout.Item[] items;
        private final Inventory inventory;

        private LayoutHolder(final InventoryLayout layout, final int page) {
            this.layout = layout;
            this.items = layout.itemsOn(page);
            this.inventory = Bukkit.createInventory(this, layout.size(), layout.titleOn(page));
            for (int slot = 0; slot < items.length; slot++) {
                if (items[slot] != null) inventory.setItem(slot, items[slot].createStack());
            }
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final class InventoryListener implements Listener {

        @EventHandler
        public void onInventoryClick(final InventoryClickEvent event) {
            if (!(event.getInventory().getHolder(false) instanceof LayoutHolder holder)) return;
            event.setCancelled(true);

            final int slot = event.getRawSlot();
            if (slot < 0 || slot >= holder.layout.size()) return;

            final Consumer<InventoryClickEvent> handler = clickHandlers
                    .getOrDefault(holder.layout.id(), Map.of())
                    .get(holder.layout.symbolAt(slot));
            if (handler != null) handler.accept(event);

            final InventoryLayout.Item item = holder.items[slot];
            if (item == null || !(event.getWhoClicked() instanceof Player player)) return;
            final String goTo = item.goTo();
            final InventoryLayout.Function function = item.function();
            if (goTo == null && function == null) return;

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.getOpenInventory().getTopInventory().getHolder(false) != holder) return;
                if (goTo != null) {
                    goTo(player, goTo);
                    return;
                }
                switch (function) {
                    case BACK -> back(player);
                    case HOME -> home(player);
                    case NEXT -> turnPage(player, 1);
                    case PREV -> turnPage(player, -1);
                }
            });
        }

        @EventHandler
        public void onInventoryDrag(final InventoryDragEvent event) {
            if (event.getInventory().getHolder(false) instanceof LayoutHolder) {
                event.setCancelled(true);
            }
        }

        @EventHandler
        public void onPlayerQuit(final PlayerQuitEvent event) {
            menuStacks.remove(event.getPlayer().getUniqueId());
        }

        // the server's own /reload
        @EventHandler
        public void onServerResourcesReloaded(final ServerResourcesReloadedEvent event) {
            reload();
        }
    }
}
