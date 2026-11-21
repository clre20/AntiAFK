package clre20.antiAFK.listener;

import clre20.antiAFK.AntiAFK;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * 選取模式中，空手右鍵方塊輪流設定點 1 / 點 2。
 */
public final class SelectionListener implements Listener {

    private final AntiAFK plugin;

    public SelectionListener(AntiAFK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        // 最便宜的判斷放最前面：不在選取模式就直接結束
        if (!plugin.getSelectionManager().isSelecting(player.getUniqueId())) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!player.getInventory().getItemInMainHand().getType().isAir()) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        // 主手與副手事件都取消，避免選取時順便開箱子、按按鈕
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (!player.hasPermission("antiafk.admin")) {
            plugin.getSelectionManager().exit(player.getUniqueId());
            return;
        }

        int point = plugin.getSelectionManager().setNextPoint(player.getUniqueId(),
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        plugin.getMessageManager().send(player, point == 1 ? "select.pos1" : "select.pos2",
                "x", String.valueOf(block.getX()),
                "y", String.valueOf(block.getY()),
                "z", String.valueOf(block.getZ()));
    }
}
