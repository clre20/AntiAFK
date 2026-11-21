package clre20.antiAFK.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * AntiAFK 所有箱子 UI 的簽署 Holder。其他插件可用
 * {@code inventory.getHolder() instanceof AntiAFKHolder} 或 {@link #getPlugin()} 辨識來源。
 */
public final class AntiAFKHolder implements InventoryHolder {

    public enum Type {
        LIST,
        EDIT
    }

    private final Plugin plugin;
    private final Type type;
    private final int page;
    private final String regionName;
    private boolean deleteArmed;
    private Inventory inventory;
    /** 列表頁：每一格對應的範圍名稱（開啟時固定，避免別人同時刪除造成點錯） */
    private final String[] slotRegions = new String[GuiManager.PER_PAGE];

    void setSlotRegion(int slot, String name) {
        slotRegions[slot] = name;
    }

    String getSlotRegion(int slot) {
        return slot >= 0 && slot < slotRegions.length ? slotRegions[slot] : null;
    }

    AntiAFKHolder(Plugin plugin, Type type, int page, String regionName) {
        this.plugin = plugin;
        this.type = type;
        this.page = page;
        this.regionName = regionName;
    }

    Inventory create(int size, net.kyori.adventure.text.Component title) {
        this.inventory = Bukkit.createInventory(this, size, title);
        return inventory;
    }

    /** 簽署：此介面所屬的插件 */
    public Plugin getPlugin() {
        return plugin;
    }

    public Type getType() {
        return type;
    }

    public int getPage() {
        return page;
    }

    public String getRegionName() {
        return regionName;
    }

    boolean isDeleteArmed() {
        return deleteArmed;
    }

    void setDeleteArmed(boolean deleteArmed) {
        this.deleteArmed = deleteArmed;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
