package clre20.antiAFK.afk;

import clre20.antiAFK.AntiAFK;
import clre20.antiAFK.config.ConfigManager;
import clre20.antiAFK.config.MessageManager;
import clre20.antiAFK.region.Region;
import clre20.antiAFK.region.RegionManager;
import clre20.antiAFK.region.ZoneMode;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 掛機偵測核心。
 * <p>
 * 事件只更新「最後活動時間」（成本極低）；判定、預警、執行指令都集中在一個低頻排程（預設每秒一次）。
 */
public final class AfkManager {

    public static final String BYPASS_PERMISSION = "antiafk.bypass";
    /** 一般 Java 版名稱，以及 Floodgate 常見的 . 前綴 */
    private static final java.util.regex.Pattern SAFE_NAME = java.util.regex.Pattern.compile("\\.?[A-Za-z0-9_]{1,16}");

    private final AntiAFK plugin;
    private final Map<UUID, AfkState> states = new ConcurrentHashMap<>();
    private BukkitTask task;

    public AfkManager(AntiAFK plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ 生命週期

    public void start() {
        stopTask();
        long period = plugin.getConfigManager().getCheckIntervalTicks();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }

    public void restart() {
        start();
    }

    public void stop() {
        stopTask();
        states.clear();
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void trackOnlinePlayers() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            states.putIfAbsent(p.getUniqueId(), new AfkState(now));
        }
    }

    public void track(UUID uuid) {
        states.put(uuid, new AfkState(System.currentTimeMillis()));
    }

    public void untrack(UUID uuid) {
        states.remove(uuid);
    }

    // ------------------------------------------------------------------ 活動記錄

    /** 任何執行緒皆可呼叫 */
    public void markActivity(UUID uuid) {
        AfkState state = states.get(uuid);
        if (state != null) {
            state.lastActivity = System.currentTimeMillis();
        }
    }

    /**
     * 主執行緒呼叫：視角變化超過門檻才算活動。與「上次算作活動時」的視角比較，
     * 因此慢慢轉動也會累積觸發，而微小抖動不會。
     */
    public void handleRotation(UUID uuid, float yaw, float pitch) {
        AfkState state = states.get(uuid);
        if (state == null) {
            return;
        }
        if (!state.hasOrientation) {
            state.lastYaw = yaw;
            state.lastPitch = pitch;
            state.hasOrientation = true;
            return;
        }
        float min = plugin.getConfigManager().getMinRotation();
        float dYaw = Math.abs(wrap(yaw - state.lastYaw));
        float dPitch = Math.abs(pitch - state.lastPitch);
        if (dYaw >= min || dPitch >= min) {
            state.lastYaw = yaw;
            state.lastPitch = pitch;
            state.lastActivity = System.currentTimeMillis();
        }
    }

    private static float wrap(float angle) {
        float a = angle % 360.0f;
        if (a >= 180.0f) {
            a -= 360.0f;
        } else if (a < -180.0f) {
            a += 360.0f;
        }
        return a;
    }

    /** 給 /antiafk check 使用：玩家已閒置的秒數 */
    public long getIdleSeconds(UUID uuid) {
        AfkState state = states.get(uuid);
        return state == null ? 0L : (System.currentTimeMillis() - state.lastActivity) / 1000L;
    }

    // ------------------------------------------------------------------ 判定

    private void tick() {
        ConfigManager cfg = plugin.getConfigManager();
        RegionManager regions = plugin.getRegionManager();
        long now = System.currentTimeMillis();
        long actionDelayMs = cfg.getActionDelaySeconds() * 1000L;

        // 先收集要執行的動作，迴圈結束後再執行，避免踢人等指令在走訪玩家清單時修改它
        List<PendingAction> pending = null;

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            AfkState state = states.get(uuid);
            if (state == null) {
                state = new AfkState(now);
                states.put(uuid, state);
                continue;
            }

            long lastActivity = state.lastActivity;

            // 已預警或已執行後，玩家恢復活動 → 重置
            if (state.stage != AfkState.Stage.NORMAL && lastActivity > state.stageStart) {
                if (state.stage == AfkState.Stage.WARNED && cfg.isCancelMessage()) {
                    plugin.getMessageManager().send(player, "afk.cancelled");
                }
                state.reset();
                continue;
            }

            if (player.hasPermission(BYPASS_PERMISSION)) {
                state.reset();
                continue;
            }

            Location loc = player.getLocation();
            RegionManager.ZoneResult zone = regions.resolve(loc.getWorld().getName(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

            if (zone.mode() == ZoneMode.ALLOW) {
                // 走進可掛機區：靜默取消（閒置時間不歸零）
                state.reset();
                continue;
            }

            Region region = zone.region();
            int afkSeconds = region != null && region.getAfkTime() != Region.USE_GLOBAL_TIME
                    ? region.getAfkTime() : cfg.getAfkTimeSeconds();
            long idle = now - lastActivity;

            switch (state.stage) {
                case NORMAL -> {
                    if (idle >= afkSeconds * 1000L) {
                        state.stage = AfkState.Stage.WARNED;
                        state.stageStart = now;
                        sendWarning(player, cfg);
                    }
                }
                case WARNED -> {
                    if (now - state.stageStart >= actionDelayMs) {
                        ConfigManager.ActionProfile profile =
                                cfg.resolveProfile(region != null ? region.getProfile() : null);
                        state.stage = AfkState.Stage.PUNISHED;
                        state.stageStart = now;
                        state.nextRepeatAt = profile.repeatInterval() > 0
                                ? now + profile.repeatInterval() * 1000L : Long.MAX_VALUE;
                        if (pending == null) {
                            pending = new ArrayList<>();
                        }
                        pending.add(new PendingAction(player.getName(), uuid, region, loc.getWorld().getName(), profile));
                    }
                }
                case PUNISHED -> {
                    if (now >= state.nextRepeatAt) {
                        ConfigManager.ActionProfile profile =
                                cfg.resolveProfile(region != null ? region.getProfile() : null);
                        state.nextRepeatAt = profile.repeatInterval() > 0
                                ? now + profile.repeatInterval() * 1000L : Long.MAX_VALUE;
                        if (pending == null) {
                            pending = new ArrayList<>();
                        }
                        pending.add(new PendingAction(player.getName(), uuid, region, loc.getWorld().getName(), profile));
                    }
                }
            }
        }

        if (pending != null) {
            for (PendingAction action : pending) {
                execute(action);
            }
        }
    }

    private void sendWarning(Player player, ConfigManager cfg) {
        MessageManager msg = plugin.getMessageManager();
        String seconds = String.valueOf(cfg.getActionDelaySeconds());
        if (cfg.isWarningChat()) {
            msg.send(player, "afk.warning-chat", "seconds", seconds);
        }
        if (cfg.isWarningTitle()) {
            player.showTitle(Title.title(
                    msg.component("afk.warning-title", "seconds", seconds),
                    msg.component("afk.warning-subtitle", "seconds", seconds),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(500))));
        }
    }

    private void execute(PendingAction action) {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        String regionName = action.region() != null
                ? action.region().getName()
                : plugin.getMessageManager().raw("placeholder.no-region");
        // 防止指令注入：名稱含有非常規字元（例如 @a、空白、分號）時改用 UUID，
        // 避免在關閉名稱驗證的伺服器上被當成目標選擇器或多餘參數
        String safeName = SAFE_NAME.matcher(action.playerName()).matches()
                ? action.playerName() : action.uuid().toString();
        if (!safeName.equals(action.playerName())) {
            plugin.getLogger().warning("玩家名稱含有特殊字元，掛機指令中的 %player% 已改用 UUID：" + action.uuid());
        }
        for (String raw : action.profile().commands()) {
            String command = raw
                    .replace("%player%", safeName)
                    .replace("%uuid%", action.uuid().toString())
                    .replace("%region%", regionName)
                    .replace("%world%", action.world());
            try {
                Bukkit.dispatchCommand(console, command);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "執行掛機指令失敗：" + command, e);
            }
        }
        plugin.getLogger().info(action.playerName() + " 在 " + regionName + "（" + action.world()
                + "）掛機，已執行動作組 " + action.profile().name() + "。");
    }

    private record PendingAction(String playerName, UUID uuid, Region region, String world,
                                 ConfigManager.ActionProfile profile) {
    }
}
