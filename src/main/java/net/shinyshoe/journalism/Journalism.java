package net.shinyshoe.journalism;

import net.shinyshoe.journalism.command.JournalCommand;
import net.shinyshoe.journalism.inventory.InventoryManager;
import net.shinyshoe.journalism.inventory.PageStore;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class Journalism extends JavaPlugin {

    private InventoryManager inventoryManager;
    private PageStore pageStore;

    @Override
    public void onEnable() {
        inventoryManager = new InventoryManager(this);
        pageStore = new PageStore(this);
        JournalCommand.register(this);
    }

    @Override
    public void onDisable() {
        if (inventoryManager != null) {
            inventoryManager.closeAll();
        }
    }

    public InventoryManager getInventoryManager() {
        return inventoryManager;
    }

    public PageStore getPageStore() {
        return pageStore;
    }

    public void saveResources(final String folder) {
        try (JarFile jar = new JarFile(getFile())) {
            jar.stream()
                    .map(JarEntry::getName)
                    .filter(name -> name.startsWith(folder + "/") && !name.endsWith("/"))
                    .forEach(name -> saveResource(name, false));
        } catch (final IOException e) {
            getLogger().warning("Could not save the bundled " + folder + " folder: " + e.getMessage());
        }
    }
}
