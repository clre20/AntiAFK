package clre20.antiAFK.region;

import java.util.regex.Pattern;

/**
 * 一個長方體範圍。座標為方塊座標（含邊界）。
 * 只在主執行緒修改；存檔時於主執行緒做快照。
 */
public final class Region {

    /** 範圍名稱只允許英數、底線、減號，避免色碼注入與 YAML 路徑問題 */
    public static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    public static final int MIN_PRIORITY = -1000;
    public static final int MAX_PRIORITY = 1000;
    /** -1 代表使用 config.yml 的 afk-time */
    public static final int USE_GLOBAL_TIME = -1;

    private final String name;
    private final String world;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private ZoneMode mode;
    private int priority;
    private int afkTime;
    private String profile;

    public Region(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2,
                  ZoneMode mode, int priority, int afkTime, String profile) {
        this.name = name;
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.mode = mode;
        this.priority = clampPriority(priority);
        this.afkTime = afkTime;
        this.profile = profile;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public static int clampPriority(int p) {
        return Math.max(MIN_PRIORITY, Math.min(MAX_PRIORITY, p));
    }

    public String getName() {
        return name;
    }

    public String getWorld() {
        return world;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public ZoneMode getMode() {
        return mode;
    }

    void setMode(ZoneMode mode) {
        this.mode = mode;
    }

    public int getPriority() {
        return priority;
    }

    void setPriority(int priority) {
        this.priority = clampPriority(priority);
    }

    public int getAfkTime() {
        return afkTime;
    }

    void setAfkTime(int afkTime) {
        this.afkTime = afkTime;
    }

    /** 可能為 null，代表使用預設動作組 */
    public String getProfile() {
        return profile;
    }

    void setProfile(String profile) {
        this.profile = profile;
    }
}
