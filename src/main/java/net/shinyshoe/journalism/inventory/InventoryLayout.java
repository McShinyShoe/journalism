package net.shinyshoe.journalism.inventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record InventoryLayout(String id, Component title, List<String> rows, Map<Character, Item> items) {

    public static final int ROW_LENGTH = 9;
    public static final int MAX_ROWS = 6;

    private static final char EMPTY = ' ';
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public static InventoryLayout parse(final String id, final ConfigurationSection section) {
        final List<String> rows = section.getStringList("layout");
        if (rows.isEmpty() || rows.size() > MAX_ROWS) {
            throw new IllegalArgumentException("layout must have 1 to " + MAX_ROWS + " rows, got " + rows.size());
        }

        final Map<Character, Item> items = new HashMap<>();
        final ConfigurationSection itemsSection = section.getConfigurationSection("items");
        if (itemsSection != null) {
            for (final String symbol : itemsSection.getKeys(false)) {
                final ConfigurationSection itemSection = itemsSection.getConfigurationSection(symbol);
                if (symbol.length() != 1 || itemSection == null) {
                    throw new IllegalArgumentException("item '" + symbol + "' must be a single character with a material");
                }
                items.put(symbol.charAt(0), Item.parse(symbol, itemSection));
            }
        }

        for (int row = 0; row < rows.size(); row++) {
            final String line = rows.get(row);
            if (line.length() != ROW_LENGTH) {
                throw new IllegalArgumentException("row " + (row + 1) + " must be " + ROW_LENGTH + " characters, got " + line.length());
            }
            for (final char symbol : line.toCharArray()) {
                if (symbol != EMPTY && !items.containsKey(symbol)) {
                    throw new IllegalArgumentException("row " + (row + 1) + " uses '" + symbol + "', which is not defined in items");
                }
            }
        }

        final Component title = MINI_MESSAGE.deserialize(section.getString("title", id));
        return new InventoryLayout(id, title, List.copyOf(rows), Map.copyOf(items));
    }

    public int size() {
        return rows.size() * ROW_LENGTH;
    }

    public char symbolAt(final int slot) {
        return rows.get(slot / ROW_LENGTH).charAt(slot % ROW_LENGTH);
    }

    @Nullable
    public Item itemAt(final int slot) {
        return items.get(symbolAt(slot));
    }

    public record Item(Material material, @Nullable Component name, List<Component> lore,
                       @Nullable String goTo, @Nullable Function function) {

        private static Item parse(final String symbol, final ConfigurationSection section) {
            final String materialName = section.getString("material", "");
            final Material material = Material.matchMaterial(materialName);
            if (material == null || !material.isItem()) {
                throw new IllegalArgumentException("item '" + symbol + "' has material '" + materialName + "', which is not an item");
            }

            final String goTo = section.getString("goto");
            final String function = section.getString("function");
            if (goTo != null && function != null) {
                throw new IllegalArgumentException("item '" + symbol + "' cannot have both goto and function");
            }

            final String name = section.getString("name");
            return new Item(
                    material,
                    name == null ? null : text(name),
                    section.getStringList("lore").stream().map(Item::text).toList(),
                    goTo,
                    function == null ? null : Function.parse(symbol, function)
            );
        }

        // names and lore italic by default
        private static Component text(final String input) {
            return MINI_MESSAGE.deserialize(input).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        }

        public ItemStack createStack() {
            final ItemStack stack = ItemStack.of(material);
            stack.editMeta(meta -> {
                if (name != null) meta.customName(name);
                if (!lore.isEmpty()) meta.lore(lore);
            });
            return stack;
        }
    }

    public enum Function {
        BACK,
        HOME;

        private static Function parse(final String symbol, final String name) {
            for (final Function function : values()) {
                if (function.name().equalsIgnoreCase(name)) return function;
            }
            throw new IllegalArgumentException("item '" + symbol + "' has function '" + name + "', which is not one of back, home");
        }
    }
}
