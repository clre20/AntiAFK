package clre20.antiAFK.config;

import clre20.antiAFK.AntiAFK;
import clre20.antiAFK.region.ZoneMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * config.yml 讀取與防呆。所有值在讀取時就做好範圍限制，執行期間直接使用欄位，不再碰檔案。
 */
public final class ConfigManager {

    /** 單一指令最大長度，避免設定錯誤造成過長指令 */
    private static final int MAX_COMMAND_LENGTH = 512;
    private static final int MAX_COMMANDS_PER_PROFILE = 32;

    private final AntiAFK plugin;

    private int afkTimeSeconds = 300;
    private int actionDelaySeconds = 60;
    private int checkIntervalTicks = 20;
    private float minRotation = 0.5f;
    private boolean countChat = true;
    private boolean countCommands = true;
    private boolean warningTitle = true;
    private boolean warningChat = true;
    private boolean cancelMessage = true;
    private ZoneMode defaultWorldMode = ZoneMode.ALLOW;
    private int maxRegionsPerWorld = 100;
    private int autoSaveSeconds = 60;
    private int guiTimeStep = 30;
    private String defaultProfile = "default";
    private Map<String, ActionProfile> profiles = Collections.emptyMap();
    private boolean updateCheckOnStartup = true;
    private boolean updateNotifyOnJoin = true;

    public ConfigManager(AntiAFK plugin) {
        this.plugin = plugin;
    }

    public void load() {
        YamlConfiguration cfg = YamlMerger.loadAndMerge(plugin, "config.yml", List.of("actions.profiles"));

        afkTimeSeconds = clamp(cfg.getInt("afk.afk-time", 300), 10, 86400);
        actionDelaySeconds = clamp(cfg.getInt("afk.action-delay", 60), 0, 86400);
        checkIntervalTicks = clamp(cfg.getInt("afk.check-interval-ticks", 20), 10, 200);
        minRotation = (float) Math.max(0.0, Math.min(45.0, cfg.getDouble("afk.min-rotation", 0.5)));
        countChat = cfg.getBoolean("afk.count-chat", true);
        countCommands = cfg.getBoolean("afk.count-commands", true);
        warningTitle = cfg.getBoolean("afk.warning.title", true);
        warningChat = cfg.getBoolean("afk.warning.chat", true);
        cancelMessage = cfg.getBoolean("afk.warning.cancel-message", true);

        defaultWorldMode = ZoneMode.parse(cfg.getString("worlds.default-mode", "allow"), ZoneMode.ALLOW);

        maxRegionsPerWorld = clamp(cfg.getInt("regions.max-per-world", 100), 1, 1000);
        autoSaveSeconds = clamp(cfg.getInt("regions.auto-save-seconds", 60), 10, 3600);
        guiTimeStep = clamp(cfg.getInt("gui.time-step", 30), 5, 3600);

        Map<String, ActionProfile> loaded = new LinkedHashMap<>();
        ConfigurationSection section = cfg.getConfigurationSection("actions.profiles");
        if (section != null) {
            for (String name : section.getKeys(false)) {
                ConfigurationSection p = section.getConfigurationSection(name);
                if (p == null) {
                    continue;
                }
                List<String> commands = new ArrayList<>();
                for (String c : p.getStringList("commands")) {
                    if (c == null) {
                        continue;
                    }
                    String trimmed = c.trim();
                    if (trimmed.startsWith("/")) {
                        trimmed = trimmed.substring(1);
                    }
                    if (trimmed.isEmpty() || trimmed.length() > MAX_COMMAND_LENGTH) {
                        plugin.getLogger().warning("動作組 " + name + " 有空白或過長的指令，已略過。");
                        continue;
                    }
                    if (commands.size() >= MAX_COMMANDS_PER_PROFILE) {
                        plugin.getLogger().warning("動作組 " + name + " 指令超過 " + MAX_COMMANDS_PER_PROFILE + " 條，多的已略過。");
                        break;
                    }
                    commands.add(trimmed);
                }
                int repeat = p.getInt("repeat-interval", 0);
                repeat = repeat <= 0 ? 0 : clamp(repeat, 10, 86400);
                loaded.put(name.toLowerCase(Locale.ROOT), new ActionProfile(name.toLowerCase(Locale.ROOT), List.copyOf(commands), repeat));
            }
        }
        if (loaded.isEmpty()) {
            plugin.getLogger().warning("config.yml 沒有任何動作組，已建立空的 default 動作組（不會執行任何指令）。");
            loaded.put("default", new ActionProfile("default", List.of(), 0));
        }
        profiles = Collections.unmodifiableMap(loaded);

        String def = cfg.getString("actions.default-profile", "default");
        defaultProfile = def == null ? "default" : def.toLowerCase(Locale.ROOT);
        if (!profiles.containsKey(defaultProfile)) {
            String first = profiles.keySet().iterator().next();
            plugin.getLogger().warning("actions.default-profile 指定的動作組不存在，改用 " + first + "。");
            defaultProfile = first;
        }

        updateCheckOnStartup = cfg.getBoolean("update-checker.check-on-startup", true);
        updateNotifyOnJoin = cfg.getBoolean("update-checker.notify-on-join", true);
    }

    /**
     * 取得動作組；不存在時退回預設動作組。
     */
    public ActionProfile resolveProfile(String name) {
        if (name != null) {
            ActionProfile p = profiles.get(name);
            if (p != null) {
                return p;
            }
        }
        return profiles.get(defaultProfile);
    }

    public List<String> getProfileNames() {
        return new ArrayList<>(profiles.keySet());
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public int getAfkTimeSeconds() {
        return afkTimeSeconds;
    }

    public int getActionDelaySeconds() {
        return actionDelaySeconds;
    }

    public int getCheckIntervalTicks() {
        return checkIntervalTicks;
    }

    public float getMinRotation() {
        return minRotation;
    }

    public boolean isCountChat() {
        return countChat;
    }

    public boolean isCountCommands() {
        return countCommands;
    }

    public boolean isWarningTitle() {
        return warningTitle;
    }

    public boolean isWarningChat() {
        return warningChat;
    }

    public boolean isCancelMessage() {
        return cancelMessage;
    }

    public ZoneMode getDefaultWorldMode() {
        return defaultWorldMode;
    }

    public int getMaxRegionsPerWorld() {
        return maxRegionsPerWorld;
    }

    public int getAutoSaveSeconds() {
        return autoSaveSeconds;
    }

    public int getGuiTimeStep() {
        return guiTimeStep;
    }

    public String getDefaultProfile() {
        return defaultProfile;
    }

    public boolean isUpdateCheckOnStartup() {
        return updateCheckOnStartup;
    }

    public boolean isUpdateNotifyOnJoin() {
        return updateNotifyOnJoin;
    }

    /**
     * 掛機後執行的動作組。
     *
     * @param commands       以控制台身分執行的指令
     * @param repeatInterval 持續掛機時重複執行的間隔秒數，0 = 不重複
     */
    public record ActionProfile(String name, List<String> commands, int repeatInterval) {
    }
}
