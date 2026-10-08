# Journalism

A Paper plugin that gives players an in-game journal.
`/journal` opens a chest menu where they can browse  sections, then categories, then entries.
A journal entry can be a hover-able item, book, sign or a whole inventory menu.

Every menu is a YAML file. Nothing about the menus is hard-coded,
and pages can be made either by editing the files or with commands in game.

Requires `Paper 26.2` and `Java 25`.

- [How sections, categories and entries work](#how-sections-categories-and-entries-work)
- [Folder structure](#folder-structure)
- [File formats](#file-formats)
- [Commands](#commands)
- [Permissions](#permissions)

## How sections, categories and entries work

The journal has three levels. Each one is a folder inside `plugins/Journalism/data/`.

| Level    | Folder                              | What it is                                    | Id                                   |
|----------|-------------------------------------|-----------------------------------------------|--------------------------------------|
| Section  | `data/<section>/`                   | A menu that lists its categories              | `page/<section>`                     |
| Category | `data/<section>/<category>/`        | A menu that lists its entries                 | `page/<section>/<category>`          |
| Entry    | `data/<section>/<category>/<entry>/`| The thing a player reads                      | `page/<section>/<category>/<entry>`  |

The id is how a page is pointed at from a button (`goto: page/journals/example`).

### Sections and categories

A section or category has two files:

- `layout.yml` is the inventory players see when they open it.
- `item.yml` is the item that stands for it in the menu above.

The empty slots (spaces) in a `layout.yml` are filled in automatically.
In a section they show the item of each category in that folder, and clicking one opens that category.
In a category they show the item of each entry, and clicking one opens that entry
(assuming that the entry have a type).

When theres more items than empty slots, the list will automatically paginate.
Put `next` and `prev` buttons in the layout so players can change page.
A layout with no empty slots lists nothing (no shit sherlock).

Sections are not listed anywhere on their own.
To make one reachable, add a button with `goto: page/<section>` to `inventory.yml`.

(A section still needs an `item.yml`, but it is not shown yet).

### Order of the list

Items are sorted by folder name, ignoring upper and lower case.
To pin something or manually gives the order, give its `item.yml` a `sort_priority`.
Everything with a `sort_priority` comes first, lowest number first, and then by name.

### Entries

An entry is basically folder with an `item.yml` and at most one content file.
The content file decides what clicking the entry does:

| Type        | File in the folder | Clicking it                                                        |
|-------------|--------------------|--------------------------------------------------------------------|
| `inventory` | `inventory.yml`    | Opens that inventory, with the id `page/<section>/<category>/<entry>` |
| `book`      | `book.yml`         | Opens a book                                                       |
| `sign`      | `sign.yml`         | Opens a sign                                                       |
| `none`      | none of them       | Does nothing                                                       |

If a folder has more than one of these files, the first in that table wins:

`inventory -> book -> sign -> none`

A sign entry is shown on the sign screen, so the reader can type on it.
What they type is thrown away. No block is placed in the world.

### Navigating

- `/journal` starts on the inventory called `main` in `inventory.yml`.
- Each player has a stack of the menus they went through. `back` returns to the previous one, and `home`
  returns to `main`.
- Running `/journal` again opens the menu the player was last on. This is forgotten when they leave the
  server.

### Loading and mistakes

The files are read when the plugin starts, on `/journal reload` and on the server's own `/reload`.
Every command that changes a page reloads by itself.

- A page with a missing or invalid file is skipped and the reason is written to the console as `Skipping page/...: <reason>`.
- A skipped section takes its categories with it.
- A `goto` that points at something that does not exist is reported the same way.

## Folder structure

```
plugins/Journalism/
├── inventory.yml - main menu and any other free-standing inventories
└── data/
    └── <section>/
        ├── item.yml
        ├── layout.yml
        └── <category>/
            ├── item.yml
            ├── layout.yml
            └── <entry>/
                ├── item.yml
                └── inventory.yml, book.yml or sign.yml - optional, decides the type
```

Folder names can use letters, numbers, `_` and `-`.
The commands refuse anything else.

On first start the plugin writes `inventory.yml` and this example, which shows one entry of every type:

```
data/journals/                 section   page/journals
├── item.yml
├── layout.yml
└── example/                   category  page/journals/example
    ├── item.yml
    ├── layout.yml
    ├── alpha/                 book entry
    │   ├── item.yml
    │   └── book.yml
    ├── beta/                  sign entry
    │   ├── item.yml
    │   └── sign.yml
    ├── pinned/                entry of type none, listed first by sort_priority
    │   └── item.yml
    └── shelf/                 inventory entry
        ├── item.yml
        └── inventory.yml
```

`inventory.yml` is only written when it does not exist, and the example only when theres no `data` folder,
so your own files are never overwritten.

## File formats

All text is [MiniMessage](https://docs.advntr.dev/minimessage/format.html), like `<green>text</green>`.
Item names and lore are not italic unless you add `<italic>`.

### An inventory

`inventory.yml` in the plugin folder, every `layout.yml`, and the `inventory.yml` of an inventory entry all describe an inventory in the same way.

```yaml
title: "Journals"
layout:
  - "b###h####"
  - "#       #"
  - "#       #"
  - "###pin###"
items:
  "#":
    material: gray_stained_glass_pane
    name: " "
  b:
    material: arrow
    name: "<yellow>Back"
    function: back
  h:
    material: arrow
    name: "<green>Home"
    function: home
  p:
    material: arrow
    name: "<yellow>Previous page"
    function: prev
  i:
    material: paper
    name: "<white>Page %page%/%pages%"
  n:
    material: arrow
    name: "<yellow>Next page"
    function: next
```

| Key      | Required | Meaning                                                                              |
|----------|----------|--------------------------------------------------------------------------------------|
| `title`  | no       | Text at the top of the inventory. The id is used when it is left out.                |
| `layout` | yes      | 1 to 6 rows of exactly 9 characters, one character per slot. Keep the quotes.        |
| `items`  | no       | The item each character in the layout stands for.                                    |

In `layout`, a space is an empty slot.
Any other character must have an entry under `items`, and the key must be exactly that one character.

Each item under `items` takes these keys:

| Key        | Required | Meaning                                                                             |
|------------|----------|-------------------------------------------------------------------------------------|
| `material` | yes      | Item id, such as `book` or `minecraft:book`.                                        |
| `head`     | no       | Only for `player_head`: a player name, or the texture value of a custom head.       |
| `name`     | no       | Item name.                                                                          |
| `lore`     | no       | List of lore lines.                                                                 |
| `goto`     | no       | Id of the inventory or entry to open when the item is clicked.                      |
| `function` | no       | A button function, see below. Cannot be combined with `goto`.                       |

`goto` takes `main`, any other id from `inventory.yml`, or a page id such as `page/journals`,
`page/journals/example` or `page/journals/example/alpha`.

`function` is one of:

| Value  | What it does                                   |
|--------|------------------------------------------------|
| `back` | Returns to the previous menu.                  |
| `home` | Returns to `main`.                             |
| `next` | Shows the next page of the list.               |
| `prev` | Shows the previous page of the list.           |

Two placeholders work in the `title` and in an item's `name` and `lore`:

| Placeholder | Replaced with                  |
|-------------|--------------------------------|
| `%page%`    | The current page of the list, starting at 1 |
| `%pages%`   | The number of pages            |

### `inventory.yml` in the plugin folder

This one file holds several inventories.
Each top-level key is the id of one inventory, written as shown above.
`main` is the one `/journal` opens, so it has to exist.

```yaml
main:
  title: "Journalism"
  layout:
    - "#########"
    - "#   j   #"
    - "#########"
  items:
    "#":
      material: gray_stained_glass_pane
      name: " "
    j:
      material: book
      name: "<gold>Journals"
      lore:
        - "<white>Access the journal you have found"
      goto: page/journals

settings:
  title: "Settings"
  layout:
    - "b########"
  items:
    "#":
      material: gray_stained_glass_pane
      name: " "
    b:
      material: arrow
      name: "<yellow>Back"
      function: back
```

One YAML syntax error anywhere in this file makes all of it fail to load.
`/journal` then answers "The journal menu is unavailable." and the console shows where the error is.

### `layout.yml`

One inventory, for a section or a category.
Its id comes from the folder, so theres no id key.
The empty slots list the categories or entries, as described above.

### `item.yml`

The item that stands for a section, category or entry.

```yaml
material: paper
name: "<gold>Pinned"
lore:
  - "<white>Listed first because of sort_priority"
sort_priority: 1
```

| Key             | Required | Meaning                                                                        |
|-----------------|----------|--------------------------------------------------------------------------------|
| `material`      | yes      | Item id.                                                                       |
| `head`          | no       | Only for `player_head`: a player name, or the texture value of a custom head.  |
| `name`          | no       | Item name.                                                                     |
| `lore`          | no       | List of lore lines.                                                            |
| `sort_priority` | no       | Whole number. Lowest is listed first, ahead of everything without one.         |

A custom head looks like this:

```yaml
material: player_head
head: "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvLi4uIn19fQ=="
```

Anything of 16 characters or fewer in `head` is read as a player name.

### `book.yml`

```yaml
pages:
  - "<bold>Alpha</bold>\n\nThis entry is a <dark_green>book</dark_green>."
  - "The second item in the list is the second page."
```

Each item under `pages` is one page. `\n` starts a new line.
A page with more text than fits is continued on the next page, split at a space.

### `sign.yml`

```yaml
lines:
  - "<dark_green><bold>Beta"
  - "is a <red>sign"
  - ""
  - ""
```

Up to 4 lines. The sign screen can only show the 16 basic colors and bold, italic, underlined and strikethrough.
Any other color becomes the nearest basic one.

### `inventory.yml` in an entry folder

One inventory, written like a `layout.yml`. Nothing is listed in its empty slots.

## Commands

| Command           | Permission          | What it does                                       |
|-------------------|---------------------|----------------------------------------------------|
| `/journal`        | `journalism.use`    | Opens the menu on the page you were last on.       |
| `/journal help`   | `journalism.use`    | Lists the commands you are allowed to use.         |
| `/journal reload` | `journalism.reload` | Reads the files again and refreshes open menus.    |

Every player has `journalism.use` by default. See [Permissions](#permissions) for the rest.

### Managing pages

All of these need `journalism.manage`.

```
/journal section  <create|open|delete> <section>
/journal section  rename <section> <new_name>

/journal category <create|open|delete> <section> <category>
/journal category rename <section> <category> <new_name>

/journal entry    create <section> <category> <entry> [inventory|book|sign|none]
/journal entry    <open|edit|delete> <section> <category> <entry>
/journal entry    edit <section> <category> <entry> chat
/journal edit     <save|cancel|restart>

/journal <section|category|entry> item set material <names> [material]
/journal <section|category|entry> item set name <names> <name>
```

`<names>` is the folder names down to that page: `<section>`, `<section> <category>` or
`<section> <category> <entry>`. Existing names are suggested with tab.

| Action              | What it does                                                                                 |
|---------------------|----------------------------------------------------------------------------------------------|
| `create`            | Makes the folder with a default `item.yml`, and a default `layout.yml` or content file. The item is the one in your hand, or a book when your hand is empty. An entry made without a type is `none`. |
| `open`              | Opens that page for you.                                                                     |
| `rename`            | Renames the folder, so the id changes. Every `goto` that pointed at the page or anything inside it is updated. The item name and the title are left alone. |
| `delete`            | Deletes the folder. A section or category that still has folders inside needs `confirm` at the end of the command. |
| `edit`              | Changes the content of a `book` or `sign` entry, see below.                                  |
| `item set material` | Changes the item of the page. Without a material it uses the item in your hand, and a player head keeps its skin, custom heads included. |
| `item set name`     | Changes the item name. Takes MiniMessage, for example `<green>Dragons`.                      |

Lore cannot be changed by command yet. Edit `item.yml` for that.

### Editing an entry

Only `book` and `sign` entries can be edited in game.
An `inventory` entry is changed in its `inventory.yml`, and a `none` entry has nothing to edit.

`/journal entry edit <section> <category> <entry>`

- **Book**: you get a book and quill that holds the MiniMessage source of the entry. Each page of the book
  is one page of the entry. Pressing Done or signing the book saves it and takes the book back. Throwing the
  book away cancels.
- **Sign**: the sign screen opens with the current text. Closing it saves. The screen gives back plain
  text, so a line you changed loses its colors, and a line you left alone keeps them.

`/journal entry edit <section> <category> <entry> chat`

Your chat goes to the entry instead of to other players, and MiniMessage tags work.

- **Book**: every message becomes one page.
- **Sign**: every message becomes one line, and after 4 messages it saves by itself.

What you type replaces the old content. It is not added to it.

| Command                 | What it does                                             |
|-------------------------|----------------------------------------------------------|
| `/journal edit save`    | Saves what you typed and stops. With nothing typed, the entry is left as it was. |
| `/journal edit cancel`  | Stops without saving.                                    |
| `/journal edit restart` | Drops what you typed and keeps editing.                  |

Leaving the server while editing in chat also discards it.

### Example

```
/journal section create lore
/journal category create lore dragons
/journal entry create lore dragons ender_dragon book
/journal entry item set name lore dragons ender_dragon <dark_purple>The Ender Dragon
/journal entry edit lore dragons ender_dragon
```

Then add a button to `inventory.yml` so players can reach the new section, and run `/journal reload`:

```yaml
    l:
      material: dragon_head
      name: "<dark_purple>Lore"
      goto: page/lore
```

## Permissions

| Permission          | Default   | Allows                                                                          |
|---------------------|-----------|---------------------------------------------------------------------------------|
| `journalism.use`    | everyone  | `/journal` and `/journal help`.                                                 |
| `journalism.reload` | operators | `/journal reload`.                                                              |
| `journalism.manage` | operators | `/journal section`, `/journal category`, `/journal entry` and `/journal edit`.  |

- `journalism.use` is the permission for the `/journal` command itself. Someone without it cannot use any
  part of the command, so `journalism.reload` and `journalism.manage` only work together with it.
- A command you are not allowed to use is hidden. It is not suggested with tab, the server answers as if it
  does not exist, and `/journal help` leaves it out.
- `journalism.manage` covers everything that creates, renames, deletes or edits a page, and `open` as well.
- There are no permissions for single pages. Everyone who can open `/journal` can read every section,
  category and entry that a button leads to.
- The console can use every command that does not need a player.

To change who has a permission, use a permissions plugin. With LuckPerms, for example:

```
/lp group default permission set journalism.use false
/lp group builder permission set journalism.manage true
/lp user Steve permission set journalism.reload true
```
