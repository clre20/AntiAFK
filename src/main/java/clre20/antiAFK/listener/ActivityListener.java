package clre20.antiAFK.listener;

import clre20.antiAFK.AntiAFK;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 記錄玩家活動。所有處理都是 O(1)，移動事件在第一行就過濾掉「只有位置變化」的情況，
 * 因此水流、礦車、活塞推動都不會被當成活動。
 */
public final class ActivityListener implements Listener {

    private final AntiAFK plugin;

    public ActivityListener(AntiAFK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedOrientation()) {
            return;
        }
        plugin.getAfkManager().handleRotation(event.getPlayer().getUniqueId(),
                event.getTo().getYaw(), event.getTo().getPitch());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getConfigManager().isCountChat()) {
            return;
        }
        plugin.getAfkManager().markActivity(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfigManager().isCountCommands()) {
            return;
        }
        plugin.getAfkManager().markActivity(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getAfkManager().track(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getAfkManager().untrack(event.getPlayer().getUniqueId());
        plugin.getSelectionManager().clear(event.getPlayer().getUniqueId());
    }
}
