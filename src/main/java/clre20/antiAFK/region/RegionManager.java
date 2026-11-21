package clre20.antiAFK.region;

import clre20.antiAFK.AntiAFK;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * 管理所有範圍與世界預設模式。
 * <p>
 * 讀取：全部放在記憶體，查詢不碰硬碟。
 * 寫入：髒資料標記 + 定時非同步批次寫入 + 關服同步存檔。
 * 所有修改與查詢只在主執行緒進行。
 */
public final class RegionManager {

    private static final String FILE_NAME = "regions.yml";
    private static final Comparator<Region> ORDER =
            Comparator.comparingInt(Region::getPriority).reversed().thenComparing(Region::getName);

    private final AntiAFK plugin;
    private final File file;
    private final Object writeLock = new Object();

    /** key = 小寫名稱 */
    private final Map<String, Region> regions = new LinkedHashMap<>();
    /** key = 世界名稱，依優先度由高到低排序 */
    private Map<String, List<Region>> byWorld = new HashMap<>();
    /** key = 世界名稱 */
    private final Map<String, ZoneMode> worldModes = new HashMap<>();

    private boolean dirty;
    private BukkitTask autoSaveTask;

    public RegionManager(AntiAFK plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
    }

    // ------------------------------------------------------------------ 讀寫

    public void load() {
        regions.clear();
        worldModes.clear();
        if (!file.exists()) {
            rebuildIndex();
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        for (Map<?, ?> entry : yaml.getMapList("worlds")) {
            Object name = entry.get("name");
            Object mode = entry.get("mode");
            if (name instanceof String w && mode instanceof String m) {
                worldModes.put(w, ZoneMode.parse(m, plugin.getConfigManager().getDefaultWorldMode()));
            }
        }

        ConfigurationSection section = yaml.getConfigurationSection("regions");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection r = section.getConfigurationSection(key);
                if (r == null || !Region.NAME_PATTERN.matcher(key).matches()) {
                    plugin.getLogger().warning("regions.yml 中的範圍 " + key + " 名稱或格式無效，已略過。");
                    continue;
                }
                String world = r.getString("world");
                List<Integer> min = r.getIntegerList("min");
                List<Integer> max = r.getIntegerList("max");
                if (world == null || min.size() != 3 || max.size() != 3) {
                    plugin.getLogger().warning("regions.yml 中的範圍 " + key + " 座標缺失，已略過。");
                    continue;
                }
                ZoneMode mode = ZoneMode.parse(r.getString("mode"), ZoneMode.DENY);
                int priority = r.getInt("priority", 0);
                int afkTime = r.getInt("afk-time", Region.USE_GLOBAL_TIME);
                if (afkTime != Region.USE_GLOBAL_TIME) {
                    afkTime = Math.max(10, Math.min(86400, afkTime));
                }
                String profile = r.getString("profile");
                Region region = new Region(key, world, min.get(0), min.get(1), min.get(2),
                        max.get(0), max.get(1), max.get(2), mode, priority, afkTime,
                        profile == null || profile.isBlank() ? null : profile.toLowerCase(Locale.ROOT));
                regions.put(key.toLowerCase(Locale.ROOT), region);
            }
        }
        rebuildIndex();
        dirty = false;
        plugin.getLogger().info("已載入 " + regions.size() + " 個範圍。");
    }

    public void startAutoSave() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }
        long period = plugin.getConfigManager().getAutoSaveSeconds() * 20L;
        autoSaveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveIfDirtyAsync, period, period);
    }

    /** 主執行緒：若有變更，先做快照，再丟到背景寫檔 */
    private void saveIfDirtyAsync() {
        if (!dirty) {
            return;
        }
        String snapshot = buildSnapshot();
        dirty = false;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> writeFile(snapshot));
    }

    /** 關服時同步存檔 */
    public void saveNow() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        if (!dirty) {
            return;
        }
        writeFile(buildSnapshot());
        dirty = false;
    }

    private String buildSnapshot() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> worlds = new ArrayList<>();
        for (Map.Entry<String, ZoneMode> e : worldModes.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.getKey());
            m.put("mode", e.getValue().key());
            worlds.add(m);
        }
        yaml.set("worlds", worlds);
        for (Region r : regions.values()) {
            String path = "regions." + r.getName();
            yaml.set(path + ".world", r.getWorld());
            yaml.set(path + ".min", List.of(r.getMinX(), r.getMinY(), r.getMinZ()));
            yaml.set(path + ".max", List.of(r.getMaxX(), r.getMaxY(), r.getMaxZ()));
            yaml.set(path + ".mode", r.getMode().key());
            yaml.set(path + ".priority", r.getPriority());
            yaml.set(path + ".afk-time", r.getAfkTime());
            yaml.set(path + ".profile", r.getProfile());
        }
        return yaml.saveToString();
    }

    private void writeFile(String content) {
        synchronized (writeLock) {
            try {
                Path dir = plugin.getDataFolder().toPath();
                Files.createDirectories(dir);
                Path tmp = dir.resolve(FILE_NAME + ".tmp");
                Files.writeString(tmp, content, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "無法儲存 " + FILE_NAME, e);
            }
        }
    }

    private void markDirty() {
        dirty = true;
    }

    private void rebuildIndex() {
        Map<String, List<Region>> index = new HashMap<>();
        for (Region r : regions.values()) {
            index.computeIfAbsent(r.getWorld(), k -> new ArrayList<>()).add(r);
        }
        for (List<Region> list : index.values()) {
            list.sort(ORDER);
        }
        byWorld = index;
    }

    // ------------------------------------------------------------------ 查詢

    /**
     * 判斷座標所在的掛機模式。依優先度由高到低找第一個包含此點的範圍，都沒有則用世界預設。
     *
     * @return 不會為 null
     */
    public ZoneResult resolve(String world, int x, int y, int z) {
        List<Region> list = byWorld.get(world);
        if (list != null) {
            for (int i = 0, size = list.size(); i < size; i++) {
                Region r = list.get(i);
                if (r.contains(x, y, z)) {
                    return new ZoneResult(r.getMode(), r);
                }
            }
        }
        return new ZoneResult(getWorldMode(world), null);
    }

    public ZoneMode getWorldMode(String world) {
        ZoneMode mode = worldModes.get(world);
        return mode != null ? mode : plugin.getConfigManager().getDefaultWorldMode();
    }

    public boolean hasCustomWorldMode(String world) {
        return worldModes.containsKey(world);
    }

    public Region get(String name) {
        return name == null ? null : regions.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<Region> all() {
        return Collections.unmodifiableCollection(regions.values());
    }

    /** 依世界、優先度排序的列表（給 GUI / list 指令使用） */
    public List<Region> sorted() {
        List<Region> list = new ArrayList<>(regions.values());
        list.sort(Comparator.comparing(Region::getWorld).thenComparing(ORDER));
        return list;
    }

    public List<String> names() {
        List<String> out = new ArrayList<>(regions.size());
        for (Region r : regions.values()) {
            out.add(r.getName());
        }
        return out;
    }

    public int countInWorld(String world) {
        List<Region> list = byWorld.get(world);
        return list == null ? 0 : list.size();
    }

    // ------------------------------------------------------------------ 修改

    public void add(Region region) {
        regions.put(region.getName().toLowerCase(Locale.ROOT), region);
        rebuildIndex();
        markDirty();
    }

    public boolean remove(String name) {
        Region removed = regions.remove(name.toLowerCase(Locale.ROOT));
        if (removed == null) {
            return false;
        }
        rebuildIndex();
        markDirty();
        return true;
    }

    public void setMode(Region region, ZoneMode mode) {
        region.setMode(mode);
        markDirty();
    }

    public void setPriority(Region region, int priority) {
        region.setPriority(priority);
        rebuildIndex();
        markDirty();
    }

    public void setAfkTime(Region region, int seconds) {
        region.setAfkTime(seconds);
        markDirty();
    }

    public void setProfile(Region region, String profile) {
        region.setProfile(profile);
        markDirty();
    }

    public void setWorldMode(String world, ZoneMode mode) {
        if (mode == null) {
            worldModes.remove(world);
        } else {
            worldModes.put(world, mode);
        }
        markDirty();
    }

    /**
     * 查詢結果。
     *
     * @param mode   該位置的掛機模式
     * @param region 命中的範圍；null 代表套用世界預設
     */
    public record ZoneResult(ZoneMode mode, Region region) {
    }
}
