package net.shinyshoe.journalism.entry;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.IntPredicate;

public final class BookPages {

    private static final int LINE_WIDTH = 114;
    private static final int LINES = 14;

    private BookPages() {
    }

    public static List<Component> split(final Component page) {
        final StringBuilder text = new StringBuilder();
        final List<Style> styles = new ArrayList<>();
        final Deque<Style> stack = new ArrayDeque<>();
        ComponentFlattener.basic().flatten(page, new FlattenerListener() {
            @Override
            public void pushStyle(final Style style) {
                stack.push(stack.isEmpty() ? style : stack.peek().merge(style));
            }

            @Override
            public void component(final String part) {
                text.append(part);
                for (int i = 0; i < part.length(); i++) {
                    styles.add(stack.isEmpty() ? Style.empty() : stack.peek());
                }
            }

            @Override
            public void popStyle(final Style style) {
                stack.pop();
            }
        });

        final List<int[]> ranges = ranges(text, index -> styles.get(index).hasDecoration(TextDecoration.BOLD));
        if (ranges.size() == 1) return List.of(page);

        final List<Component> pages = new ArrayList<>();
        for (final int[] range : ranges) {
            final TextComponent.Builder builder = Component.text();
            int start = range[0];
            for (int i = start + 1; i <= range[1]; i++) {
                if (i == range[1] || !styles.get(i).equals(styles.get(start))) {
                    builder.append(Component.text(text.substring(start, i), styles.get(start)));
                    start = i;
                }
            }
            pages.add(builder.build());
        }
        return pages;
    }

    public static List<String> split(final String text) {
        return ranges(text, index -> false).stream().map(range -> text.substring(range[0], range[1])).toList();
    }

    // start and end of every page, the space or line break a page is split at belongs to neither
    private static List<int[]> ranges(final CharSequence text, final IntPredicate bold) {
        final List<int[]> pages = new ArrayList<>();
        int pageStart = 0;
        int line = 1;
        int width = 0;
        int lastSpace = -1;
        int index = 0;
        while (index < text.length()) {
            final char character = text.charAt(index);
            final int advance = width(character) + (bold.test(index) ? 1 : 0);
            final int end;
            final int next;
            if (character == '\n') {
                end = index;
                next = index + 1;
            } else if (width + advance <= LINE_WIDTH) {
                if (character == ' ') lastSpace = index;
                width += advance;
                index++;
                continue;
            } else if (character == ' ') {
                end = index;
                next = index + 1;
            } else if (lastSpace >= 0) {
                end = lastSpace;
                next = lastSpace + 1;
            } else {
                end = index;
                next = index;
            }

            if (line == LINES) {
                pages.add(new int[]{pageStart, end});
                pageStart = next;
                line = 1;
            } else {
                line++;
            }
            width = 0;
            lastSpace = -1;
            index = next;
        }
        if (pageStart < text.length() || pages.isEmpty()) {
            pages.add(new int[]{pageStart, text.length()});
        }
        return pages;
    }

    // advance of the default font, other characters are taken as the common width
    private static int width(final char character) {
        return switch (character) {
            case '!', '\'', ',', '.', ':', ';', 'i', '|' -> 2;
            case '`', 'l' -> 3;
            case ' ', '"', '(', ')', '*', 'I', '[', ']', 't', '{', '}' -> 4;
            case '<', '>', 'f', 'k' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }
}
