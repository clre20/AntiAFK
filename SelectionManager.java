package clre20.antiAFK.region;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 管理玩家的選取模式與兩個選取點。只存 UUID 與座標，不持有 Player / World 實例。
 */
public final class SelectionManager {

    private final Set<UUID> selecting = new HashSet<>();
    private final Map<UUID, Selection> selections = new HashMap<>();

    /** 高頻事件用的快速判斷 */
    public boolean isSelecting(UUID uuid) {
        return selecting.contains(uuid);
    }

    /**
     * 切換選取模式。
     *
     * @return 切換後是否為選取模式
     */
    public boolean toggle(UUID uuid) {
        if (selecting.remove(uuid)) {
            return false;
        }
        selecting.add(uuid);
        return true;
    }

    public void exit(UUID uuid) {
        selecting.remove(uuid);
    }

    /**
     * 設定下一個點（點 1、點 2 交替）。換世界時重新從點 1 開始。
     *
     * @return 這次設定的是第幾點（1 或 2）
     */
    public int setNextPoint(UUID uuid, String world, int x, int y, int z) {
        Selection sel = selections.get(uuid);
        if (sel == null || !sel.world.equals(world)) {
            sel = new Selection(world);
            selections.put(uuid, sel);
        }
        if (!sel.hasPos1 || !sel.nextIsPos2) {
            sel.x1 = x;
            sel.y1 = y;
            sel.z1 = z;
            sel.hasPos1 = true;
            sel.nextIsPos2 = true;
            return 1;
        }
        sel.x2 = x;
        sel.y2 = y;
        sel.z2 = z;
        sel.hasPos2 = true;
        sel.nextIsPos2 = false;
        return 2;
    }

    /** 取得完整選取（兩點都有），否則回傳 null */
    public Selection getComplete(UUID uuid) {
        Selection sel = selections.get(uuid);
        return sel != null && sel.hasPos1 && sel.hasPos2 ? sel : null;
    }

    public void clear(UUID uuid) {
        selecting.remove(uuid);
        selections.remove(uuid);
    }

    public void clearAll() {
        selecting.clear();
        selections.clear();
    }

    public static final class Selection {
        public final String world;
        public int x1;
        public int y1;
        public int z1;
        public int x2;
        public int y2;
        public int z2;
        boolean hasPos1;
        boolean hasPos2;
        boolean nextIsPos2;

        Selection(String world) {
            this.world = world;
        }
    }
}
