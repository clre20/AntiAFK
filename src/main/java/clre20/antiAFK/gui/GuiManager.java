package clre20.antiAFK.gui;

import clre20.antiAFK.AntiAFK;
import clre20.antiAFK.config.ConfigManager;
import clre20.antiAFK.config.MessageManager;
import clre20.antiAFK.region.Region;
import clre20.antiAFK.region.ZoneMode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 建立箱子 UI。所有文字皆取自 messages.yml。
 */
public final class GuiManager {

    public static final int LIST_SIZE = 54;
    public static final int PER_PAGE = 45;
    public static final int SLOT_PREV = 45;
    public static final int SLOT_WORLD = 48;
    public static final int SLOT_CLOSE = 49;
    public static final int SLOT_SELECT = 50;
    public static final int SLOT_NEXT = 53;

    public static final int EDIT_SIZE = 27;
    public static final int SLOT_INFO = 4;
    public static final int SLOT_MODE = 10;
    public static final int SLOT_PRIO_DOWN = 11;
    public static final int SLOT_PRIO_UP = 12;
    public static final int SLOT_PROFILE = 13;
    public static final int SLOT_TIME_DOWN = 14;
    public static final int SLOT_TIME_UP = 15;
    public static final int SLOT_TIME_RESET = 16;
    public static final int SLOT_BACK = 18;
    public static final int SLOT_TELEPORT = 22;
    public static final int SLOT_DELETE = 26;

    private final AntiAFK plugin;

    public GuiManager(AntiAFK plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ 列表

    public void openList(Player player, int page) {
        MessageManager msg = plugin.getMessageManager();
        List<Region> regions = plugin.getRegionManager().sorted();
        int maxPage = Math.max(0, (regions.size() - 1) / PER_PAGE);
        int current = Math.max(0, Math.min(page, maxPage));

        AntiAFKHolder holder = new AntiAFKHolder(plugin, AntiAFKHolder.Type.LIST, current, null);
        Inventory inv = holder.create(LIST_SIZE, msg.component("gui.list.title",
                "page", String.valueOf(current + 1), "pages", String.valueOf(maxPage + 1)));

        int start = current * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < regions.size(); i++) {
            Region region = regions.get(start + i);
            inv.setItem(i, regionItem(region, "gui.list.region-lore"));
            holder.setSlotRegion(i, region.getName());
        }

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, "gui.filler", null);
        for (int i = PER_PAGE; i < LIST_SIZE; i++) {
            inv.setItem(i, filler);
        }
        if (current > 0) {
            inv.setItem(SLOT_PREV, item(Material.ARROW, "gui.list.prev", null));
        }
        if (current < maxPage) {
            inv.setItem(SLOT_NEXT, item(Material.ARROW, "gui.list.next", null));
        }

        String world = player.getWorld().getName();
        ZoneMode worldMode = plugin.getRegionManager().getWorldMode(world);
        inv.setItem(SLOT_WORLD, item(worldMode == ZoneMode.ALLOW ? Material.GRASS_BLOCK : Material.NETHERRACK,
                "gui.list.world", "gui.list.world-lore",
                "world", world, "mode", modeText(worldMode)));
        inv.setItem(SLOT_CLOSE, item(Material.BARRIER, "gui.close", null));
        boolean selecting = plugin.getSelectionManager().isSelecting(player.getUniqueId());
        inv.setItem(SLOT_SELECT, item(selecting ? Material.GOLDEN_AXE : Material.WOODEN_AXE,
                "gui.list.select", "gui.list.select-lore",
                "state", msg.raw(selecting ? "state.on" : "state.off")));

        player.openInventory(inv);
    }

    // ------------------------------------------------------------------ 編輯

    public void openEdit(Player player, Region region) {
        openEdit(player, region, false);
    }

    void openEdit(Player player, Region region, boolean deleteArmed) {
        MessageManager msg = plugin.getMessageManager();
        ConfigManager cfg = plugin.getConfigManager();
        AntiAFKHolder holder = new AntiAFKHolder(plugin, AntiAFKHolder.Type.EDIT, 0, region.getName());
        holder.setDeleteArmed(deleteArmed);
        Inventory inv = holder.create(EDIT_SIZE, msg.component("gui.edit.title", "region", region.getName()));

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, "gui.filler", null);
        for (int i = 0; i < EDIT_SIZE; i++) {
            inv.setItem(i, filler);
        }

        String step = String.valueOf(cfg.getGuiTimeStep());
        inv.setItem(SLOT_INFO, regionItem(region, "gui.edit.info-lore"));
        inv.setItem(SLOT_MODE, item(region.getMode() == ZoneMode.ALLOW ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                "gui.edit.mode", "gui.edit.mode-lore", "mode", modeText(region.getMode())));
        inv.setItem(SLOT_PRIO_DOWN, item(Material.RED_DYE, "gui.edit.priority-down", "gui.edit.priority-lore",
                "priority", String.valueOf(region.getPriority())));
        inv.setItem(SLOT_PRIO_UP, item(Material.LIME_DYE, "gui.edit.priority-up", "gui.edit.priority-lore",
                "priority", String.valueOf(region.getPriority())));
        inv.setItem(SLOT_PROFILE, item(Material.COMMAND_BLOCK, "gui.edit.profile", "gui.edit.profile-lore",
                "profile", profileText(region), "profiles", String.join(", ", cfg.getProfileNames())));
        inv.setItem(SLOT_TIME_DOWN, item(Material.CLOCK, "gui.edit.time-down", "gui.edit.time-lore",
                "time", timeText(region), "step", step));
        inv.setItem(SLOT_TIME_UP, item(Material.CLOCK, "gui.edit.time-up", "gui.edit.time-lore",
                "time", timeText(region), "step", step));
        inv.setItem(SLOT_TIME_RESET, item(Material.COMPASS, "gui.edit.time-reset", "gui.edit.time-reset-lore",
                "seconds", String.valueOf(cfg.getAfkTimeSeconds())));
        inv.setItem(SLOT_BACK, item(Material.ARROW, "gui.edit.back", null));
        inv.setItem(SLOT_TELEPORT, item(Material.ENDER_PEARL, "gui.edit.teleport", null));
        inv.setItem(SLOT_DELETE, deleteArmed
                ? item(Material.TNT, "gui.edit.delete-confirm", "gui.edit.delete-confirm-lore")
                : item(Material.LAVA_BUCKET, "gui.edit.delete", "gui.edit.delete-lore"));

        player.openInventory(inv);
    }

    // ------------------------------------------------------------------ 工具

    /** 關服 / 重載時關閉所有本插件的介面 */
    public void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof AntiAFKHolder) {
                p.closeInventory();
            }
        }
    }

    private ItemStack regionItem(Region region, String loreKey) {
        Material mat = region.getMode() == ZoneMode.ALLOW ? Material.LIME_WOOL : Material.RED_WOOL;
        return item(mat, "gui.region-name", loreKey,
                "region", region.getName(),
                "world", region.getWorld(),
                "min", region.getMinX() + ", " + region.getMinY() + ", " + region.getMinZ(),
                "max", region.getMaxX() + ", " + region.getMaxY() + ", " + region.getMaxZ(),
                "mode", modeText(region.getMode()),
                "priority", String.valueOf(region.getPriority()),
                "time", timeText(region),
                "profile", profileText(region));
    }

    public String modeText(ZoneMode mode) {
        return plugin.getMessageManager().raw(mode == ZoneMode.ALLOW ? "mode.allow" : "mode.deny");
    }

    public String timeText(Region region) {
        MessageManager msg = plugin.getMessageManager();
        if (region.getAfkTime() == Region.USE_GLOBAL_TIME) {
            return msg.raw("gui.time-global", "seconds", String.valueOf(plugin.getConfigManager().getAfkTimeSeconds()));
        }
        return msg.raw("gui.time-custom", "seconds", String.valueOf(region.getAfkTime()));
    }

    public String profileText(Region region) {
        if (region.getProfile() == null) {
            return plugin.getMessageManager().raw("gui.profile-default",
                    "profile", plugin.getConfigManager().getDefaultProfile());
        }
        return region.getProfile();
    }

    private ItemStack item(Material material, String nameKey, String loreKey, String... placeholders) {
        MessageManager msg = plugin.getMessageManager();
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(msg.item(nameKey, placeholders));
            if (loreKey != null) {
                meta.lore(new ArrayList<>(msg.itemLore(loreKey, placeholders)));
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
