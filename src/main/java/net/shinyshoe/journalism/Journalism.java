package net.shinyshoe.journalism;

import net.shinyshoe.journalism.command.JournalCommand;
import net.shinyshoe.journalism.inventory.InventoryManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class Journalism extends JavaPlugin {

    private InventoryManager inventoryManager;

    @Override
    public void onEnable() {
        inventoryManager = new InventoryManager(this);
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
}
