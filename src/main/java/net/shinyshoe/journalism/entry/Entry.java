package net.shinyshoe.journalism.entry;

import net.kyori.adventure.inventory.Book;

import java.util.List;

public sealed interface Entry {

    record TextBook(Book book) implements Entry {
    }

    record SignText(List<String> lines) implements Entry {
    }

    record None() implements Entry {
    }
}
