package clre20.antiAFK;

import clre20.antiAFK.afk.AfkManager;
import clre20.antiAFK.command.AntiAFKCommand;
import clre20.antiAFK.config.ConfigManager;
import clre20.antiAFK.config.MessageManager;
import clre20.antiAFK.gui.GuiListener;
import clre20.antiAFK.gui.GuiManager;
import clre20.antiAFK.listener.ActivityListener;
import clre20.antiAFK.listener.SelectionListener;
import clre20.antiAFK.region.RegionManager;
import clre20.antiAFK.region.SelectionManager;
import clre20.antiAFK.update.UpdateChecker;
import clre20.antiAFK.update.UpdateListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * AntiAFK 主類別。
 */
public final class AntiAFK extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private RegionManager regionManager;
    private SelectionManager selectionManager;
    private AfkManager afkManager;
    private GuiManager guiManager;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);
        configManager.load();
        messageManager.load();

        regionManager = new RegionManager(this);
        regionManager.load();
        regionManager.startAutoSave();

        selectionManager = new SelectionManager();
        afkManager = new AfkManager(this);
        guiManager = new GuiManager(this);
        updateChecker = new UpdateChecker(this);

        getServer().getPluginManager().registerEvents(new ActivityListener(this), this);
        getServer().getPluginManager().registerEvents(new SelectionListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new UpdateListener(this), this);

        PluginCommand command = getCommand("antiafk");
        if (command != null) {
            AntiAFKCommand executor = new AntiAFKCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        afkManager.trackOnlinePlayers();
        afkManager.start();

        if (configManager.isUpdateCheckOnStartup()) {
            updateChecker.checkOnStartup();
        }
        getLogger().info("AntiAFK v" + getPluginMeta().getVersion() + " 已啟用。");
    }

    @Override
    public void onDisable() {
        if (afkManager != null) {
            afkManager.stop();
        }
        if (guiManager != null) {
            guiManager.closeAll();
        }
        getServer().getScheduler().cancelTasks(this);
        if (regionManager != null) {
            regionManager.saveNow();
        }
        if (selectionManager != null) {
            selectionManager.clearAll();
        }
        if (updateChecker != null) {
            updateChecker.shutdown();
        }
    }

    /**
     * 重新讀取 config.yml / messages.yml，並自動補齊缺失項目。
     */
    public void reloadAll() {
        configManager.load();
        messageManager.load();
        regionManager.startAutoSave();
        afkManager.restart();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public RegionManager getRegionManager() {
        return regionManager;
    }

    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    public AfkManager getAfkManager() {
        return afkManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }
}
