package clre20.antiAFK.gui;

import clre20.antiAFK.AntiAFK;
import clre20.antiAFK.command.AntiAFKCommand;
import clre20.antiAFK.config.ConfigManager;
import clre20.antiAFK.region.Region;
import clre20.antiAFK.region.RegionManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.UUID;

/**
 * 箱子 UI 點擊處理：一律取消原始事件，只接受左鍵。
 */
public final class GuiListener implements Listener {

    private final AntiAFK plugin;

    public GuiListener(AntiAFK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof AntiAFKHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof AntiAFKHolder holder)) {
            return;
        }
        // 不論點哪裡（包含自己的背包、Shift、數字鍵）都取消，防止物品被拿走或塞入
        event.setCancelled(true);

        if (event.getClick() != ClickType.LEFT) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) {
            return;
        }
        if (!player.hasPermission(AntiAFKCommand.PERM_ADMIN)) {
            player.closeInventory();
            return;
        }

        switch (holder.getType()) {
            case LIST -> handleList(player, holder, slot);
            case EDIT -> handleEdit(player, holder, slot);
        }
    }

    private void handleList(Player player, AntiAFKHolder holder, int slot) {
        GuiManager gui = plugin.getGuiManager();
        if (slot < GuiManager.PER_PAGE) {
            Region region = plugin.getRegionManager().get(holder.getSlotRegion(slot));
            if (region != null) {
                gui.openEdit(player, region);
            } else if (holder.getSlotRegion(slot) != null) {
                plugin.getMessageManager().send(player, "region.not-found", "region", holder.getSlotRegion(slot));
                gui.openList(player, holder.getPage());
            }
            return;
        }
        switch (slot) {
            case GuiManager.SLOT_PREV -> gui.openList(player, holder.getPage() - 1);
            case GuiManager.SLOT_NEXT -> gui.openList(player, holder.getPage() + 1);
            case GuiManager.SLOT_CLOSE -> player.closeInventory();
            case GuiManager.SLOT_WORLD -> {
                String world = player.getWorld().getName();
                RegionManager rm = plugin.getRegionManager();
                rm.setWorldMode(world, rm.getWorldMode(world).toggle());
                plugin.getMessageManager().send(player, "world.set", "world", world,
                        "mode", gui.modeText(rm.getWorldMode(world)));
                gui.openList(player, holder.getPage());
            }
            case GuiManager.SLOT_SELECT -> {
                boolean on = plugin.getSelectionManager().toggle(player.getUniqueId());
                plugin.getMessageManager().send(player, on ? "select.enabled" : "select.disabled");
                gui.openList(player, holder.getPage());
            }
            default -> {
            }
        }
    }

    private void handleEdit(Player player, AntiAFKHolder holder, int slot) {
        GuiManager gui = plugin.getGuiManager();
        RegionManager rm = plugin.getRegionManager();
        ConfigManager cfg = plugin.getConfigManager();
        Region region = rm.get(holder.getRegionName());
        if (region == null) {
            plugin.getMessageManager().send(player, "region.not-found", "region", holder.getRegionName());
            gui.openList(player, 0);
            return;
        }

        switch (slot) {
            case GuiManager.SLOT_MODE -> rm.setMode(region, region.getMode().toggle());
            case GuiManager.SLOT_PRIO_DOWN -> rm.setPriority(region, region.getPriority() - 1);
            case GuiManager.SLOT_PRIO_UP -> rm.setPriority(region, region.getPriority() + 1);
            case GuiManager.SLOT_PROFILE -> rm.setProfile(region, nextProfile(region.getProfile(), cfg.getProfileNames()));
            case GuiManager.SLOT_TIME_DOWN -> rm.setAfkTime(region, clampTime(currentTime(region, cfg) - cfg.getGuiTimeStep()));
            case GuiManager.SLOT_TIME_UP -> rm.setAfkTime(region, clampTime(currentTime(region, cfg) + cfg.getGuiTimeStep()));
            case GuiManager.SLOT_TIME_RESET -> rm.setAfkTime(region, Region.USE_GLOBAL_TIME);
            case GuiManager.SLOT_BACK -> {
                gui.openList(player, 0);
                return;
            }
            case GuiManager.SLOT_TELEPORT -> {
                player.closeInventory();
                teleport(player, region);
                return;
            }
            case GuiManager.SLOT_DELETE -> {
                if (!holder.isDeleteArmed()) {
                    gui.openEdit(player, region, true);
                    return;
                }
                rm.remove(region.getName());
                plugin.getMessageManager().send(player, "region.deleted", "region", region.getName());
                gui.openList(player, 0);
                return;
            }
            default -> {
                return;
            }
        }
        gui.openEdit(player, region);
    }

    /** 依序切換：預設 → 動作組 1 → 動作組 2 → ... → 預設 */
    private static String nextProfile(String current, List<String> names) {
        if (names.isEmpty()) {
            return null;
        }
        if (current == null) {
            return names.get(0);
        }
        int idx = names.indexOf(current);
        if (idx < 0 || idx + 1 >= names.size()) {
            return null;
        }
        return names.get(idx + 1);
    }

    private static int currentTime(Region region, ConfigManager cfg) {
        return region.getAfkTime() == Region.USE_GLOBAL_TIME ? cfg.getAfkTimeSeconds() : region.getAfkTime();
    }

    private static int clampTime(int seconds) {
        return Math.max(10, Math.min(86400, seconds));
    }

    /** 傳送到範圍中心的最高點；先非同步載入區塊，避免主執行緒卡頓 */
    public static void teleport(AntiAFK plugin, Player player, Region region) {
        World world = plugin.getServer().getWorld(region.getWorld());
        if (world == null) {
            plugin.getMessageManager().send(player, "region.world-missing", "world", region.getWorld());
            return;
        }
        int cx = Math.floorDiv(region.getMinX() + region.getMaxX(), 2);
        int cz = Math.floorDiv(region.getMinZ() + region.getMaxZ(), 2);
        UUID uuid = player.getUniqueId();
        world.getChunkAtAsync(cx >> 4, cz >> 4).thenAccept(chunk -> {
            Player online = plugin.getServer().getPlayer(uuid);
            if (online == null) {
                return;
            }
            // 一律傳到地表，避免範圍在地底時被卡在方塊裡窒息
            int y = world.getHighestBlockYAt(cx, cz) + 1;
            online.teleportAsync(new Location(world, cx + 0.5, y, cz + 0.5, online.getYaw(), online.getPitch()));
            plugin.getMessageManager().send(online, "region.teleported", "region", region.getName());
        });
    }

    private void teleport(Player player, Region region) {
        teleport(plugin, player, region);
    }
}
