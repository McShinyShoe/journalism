package net.shinyshoe.journalism.inventory;

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
    private final Map<UUID, Deque<String>> menuStacks = new HashMap<>();

    public InventoryManager(final Journalism plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(new InventoryListener(), plugin);
        load();
    }

    public void load() {
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
                plugin.getLogger().warning("Skipping inventory '" + id + "' in " + FILE_NAME + ": " + e.getMessage());
            }
        }

        for (final InventoryLayout layout : layouts.values()) {
            layout.items().forEach((symbol, item) -> {
                if (item.goTo() != null && !layouts.containsKey(item.goTo())) {
                    plugin.getLogger().warning("Inventory '" + layout.id() + "' item '" + symbol + "' in " + FILE_NAME
                            + " goes to '" + item.goTo() + "', which is not a loaded inventory");
                }
            });
        }
    }

    // opens the menu the player was last on
    public boolean open(final Player player) {
        final Deque<String> stack = menuStack(player);

        // reset when reloaded
        while (!stack.isEmpty() && !layouts.containsKey(stack.peek())) stack.pop();

        final InventoryLayout layout = layouts.get(stack.isEmpty() ? HOME_ID : stack.peek());
        if (layout == null) return false;
        player.openInventory(new LayoutHolder(layout).getInventory());
        return true;
    }

    public void goTo(final Player player, final String id) {
        if (!layouts.containsKey(id)) return;
        final Deque<String> stack = menuStack(player);
        if (!id.equals(stack.isEmpty() ? HOME_ID : stack.peek())) stack.push(id);
        open(player);
    }

    public void back(final Player player) {
        final Deque<String> stack = menuStack(player);
        if (stack.isEmpty()) return;
        stack.pop();
        open(player);
    }

    public void home(final Player player) {
        final Deque<String> stack = menuStack(player);
        if (stack.isEmpty()) return;
        stack.clear();
        open(player);
    }

    private Deque<String> menuStack(final Player player) {
        return menuStacks.computeIfAbsent(player.getUniqueId(), key -> new ArrayDeque<>());
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

    private static final class LayoutHolder implements InventoryHolder {
        private final InventoryLayout layout;
        private final Inventory inventory;

        private LayoutHolder(final InventoryLayout layout) {
            this.layout = layout;
            this.inventory = Bukkit.createInventory(this, layout.size(), layout.title());
            for (int slot = 0; slot < layout.size(); slot++) {
                final InventoryLayout.Item item = layout.itemAt(slot);
                if (item != null) inventory.setItem(slot, item.createStack());
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

            final InventoryLayout.Item item = holder.layout.itemAt(slot);
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
    }
}
