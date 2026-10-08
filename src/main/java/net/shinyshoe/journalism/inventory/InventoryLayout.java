package net.shinyshoe.journalism.inventory;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

public record InventoryLayout(String id, Component title, List<String> rows, Map<Character, Item> items,
                              List<Item> children) {

    public static final int ROW_LENGTH = 9;
    public static final int MAX_ROWS = 6;

    private static final char EMPTY = ' ';
    private static final String PAGE = "%page%";
    private static final String PAGES = "%pages%";
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
                items.put(symbol.charAt(0), Item.parse("item '" + symbol + "'", itemSection));
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
        return new InventoryLayout(id, title, List.copyOf(rows), Map.copyOf(items), List.of());
    }

    // children are listed in the empty slots, over as many pages as it takes
    public InventoryLayout withChildren(final List<Item> children) {
        return new InventoryLayout(id, title, rows, items, List.copyOf(children));
    }

    public int size() {
        return rows.size() * ROW_LENGTH;
    }

    public char symbolAt(final int slot) {
        return rows.get(slot / ROW_LENGTH).charAt(slot % ROW_LENGTH);
    }

    public int pageCount() {
        final int perPage = childrenPerPage();
        return perPage == 0 ? 1 : Math.max(1, Math.ceilDiv(children.size(), perPage));
    }

    public Component titleOn(final int page) {
        return placeholders(page).apply(title);
    }

    public @Nullable Item[] itemsOn(final int page) {
        final UnaryOperator<Component> placeholders = placeholders(page);
        final Item[] slots = new Item[size()];
        int child = page * childrenPerPage();
        for (int slot = 0; slot < slots.length; slot++) {
            final char symbol = symbolAt(slot);
            Item item = items.get(symbol);
            if (symbol == EMPTY && child < children.size()) item = children.get(child++);
            if (item != null) slots[slot] = item.withText(placeholders);
        }
        return slots;
    }

    private int childrenPerPage() {
        return (int) String.join("", rows).chars().filter(symbol -> symbol == EMPTY).count();
    }

    private UnaryOperator<Component> placeholders(final int page) {
        final TextReplacementConfig current = TextReplacementConfig.builder()
                .matchLiteral(PAGE).replacement(String.valueOf(page + 1)).build();
        final TextReplacementConfig total = TextReplacementConfig.builder()
                .matchLiteral(PAGES).replacement(String.valueOf(pageCount())).build();
        return text -> text.replaceText(current).replaceText(total);
    }

    public record Item(Material material, @Nullable ResolvableProfile head, @Nullable Component name, List<Component> lore,
                       @Nullable String goTo, @Nullable Function function) {

        static final String MATERIAL = "material";
        static final String HEAD = "head";
        static final String NAME = "name";

        private static final String TEXTURES = "textures";
        private static final int MAX_PLAYER_NAME_LENGTH = 16;

        static Item parse(final String label, final ConfigurationSection section) {
            final String materialName = section.getString(MATERIAL, "");
            final Material material = Material.matchMaterial(materialName);
            if (material == null || !material.isItem()) {
                throw new IllegalArgumentException(label + " has material '" + materialName + "', which is not an item");
            }

            final String head = material == Material.PLAYER_HEAD ? section.getString(HEAD) : null;

            final String goTo = section.getString("goto");
            final String function = section.getString("function");
            if (goTo != null && function != null) {
                throw new IllegalArgumentException(label + " cannot have both goto and function");
            }

            final String name = section.getString(NAME);
            return new Item(
                    material,
                    head == null ? null : profile(label, head),
                    name == null ? null : text(name),
                    section.getStringList("lore").stream().map(Item::text).toList(),
                    goTo,
                    function == null ? null : Function.parse(label, function)
            );
        }

        Item withGoTo(final String goTo) {
            return new Item(material, head, name, lore, goTo, null);
        }

        private Item withText(final UnaryOperator<Component> text) {
            return new Item(material, head, name == null ? null : text.apply(name), lore.stream().map(text).toList(), goTo, function);
        }

        // a player name, or like the longer texture value of a custom head
        private static ResolvableProfile profile(final String label, final String head) {
            final ResolvableProfile.Builder profile = ResolvableProfile.resolvableProfile();
            if (head.length() > MAX_PLAYER_NAME_LENGTH) {
                return profile.addProperty(new ProfileProperty(TEXTURES, head)).build();
            }
            try {
                return profile.name(head).build();
            } catch (final IllegalArgumentException e) {
                throw new IllegalArgumentException(label + " has head '" + head + "', which is not a player name or a texture value");
            }
        }

        @Nullable
        static String headOf(final ItemStack stack) {
            final ResolvableProfile profile = stack.getData(DataComponentTypes.PROFILE);
            if (stack.getType() != Material.PLAYER_HEAD || profile == null) return null;
            for (final ProfileProperty property : profile.properties()) {
                if (property.getName().equals(TEXTURES)) return property.getValue();
            }
            return profile.name();
        }

        // names and lore italic by default
        private static Component text(final String input) {
            return MINI_MESSAGE.deserialize(input).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        }

        public ItemStack createStack() {
            final ItemStack stack = ItemStack.of(material);
            if (head != null) stack.setData(DataComponentTypes.PROFILE, head);
            stack.editMeta(meta -> {
                if (name != null) meta.customName(name);
                if (!lore.isEmpty()) meta.lore(lore);
            });
            return stack;
        }
    }

    public enum Function {
        BACK,
        HOME,
        NEXT,
        PREV;

        private static Function parse(final String label, final String name) {
            for (final Function function : values()) {
                if (function.name().equalsIgnoreCase(name)) return function;
            }
            throw new IllegalArgumentException(label + " has function '" + name + "', which is not one of back, home, next, prev");
        }
    }
}
