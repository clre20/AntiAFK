package clre20.antiAFK.command;

import clre20.antiAFK.AntiAFK;
import clre20.antiAFK.config.ConfigManager;
import clre20.antiAFK.config.MessageManager;
import clre20.antiAFK.gui.GuiListener;
import clre20.antiAFK.gui.GuiManager;
import clre20.antiAFK.region.Region;
import clre20.antiAFK.region.RegionManager;
import clre20.antiAFK.region.SelectionManager;
import clre20.antiAFK.region.ZoneMode;
import clre20.antiAFK.update.UpdateChecker;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /antiafk 主指令與補全。
 */
public final class AntiAFKCommand implements CommandExecutor, TabCompleter {

    public static final String PERM_ADMIN = "antiafk.admin";
    public static final String PERM_RELOAD = "antiafk.reload";
    public static final String PERM_VERSION = "antiafk.version";

    private static final List<String> ADMIN_SUBS = List.of(
            "help", "select", "create", "delete", "set", "list", "info", "tp", "world", "check", "gui");
    private static final List<String> SET_KEYS = List.of("mode", "priority", "time", "profile");
    private static final List<String> MODES = List.of("allow", "deny");

    private final AntiAFK plugin;

    public AntiAFKCommand(AntiAFK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MessageManager msg = plugin.getMessageManager();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "reload" -> {
                if (!checkPerm(sender, PERM_RELOAD)) {
                    return true;
                }
                plugin.getGuiManager().closeAll();
                plugin.reloadAll();
                msg.send(sender, "command.reloaded");
                return true;
            }
            case "version" -> {
                if (!checkPerm(sender, PERM_VERSION)) {
                    return true;
                }
                handleVersion(sender);
                return true;
            }
            default -> {
                // 其餘子指令一律需要管理權限
            }
        }

        if (!checkPerm(sender, PERM_ADMIN)) {
            return true;
        }

        switch (sub) {
            case "help" -> sendHelp(sender, label);
            case "select" -> handleSelect(sender);
            case "create" -> handleCreate(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "set" -> handleSet(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "tp" -> handleTeleport(sender, args);
            case "world" -> handleWorld(sender, args);
            case "check" -> handleCheck(sender, args);
            case "gui" -> {
                Player player = requirePlayer(sender);
                if (player != null) {
                    plugin.getGuiManager().openList(player, 0);
                }
            }
            default -> msg.send(sender, "command.unknown", "label", label);
        }
        return true;
    }

    // ------------------------------------------------------------------ 子指令

    private void sendHelp(CommandSender sender, String label) {
        for (String line : List.of("help.header", "help.select", "help.create", "help.delete", "help.set",
                "help.list", "help.info", "help.tp", "help.world", "help.check", "help.gui",
                "help.reload", "help.version")) {
            plugin.getMessageManager().send(sender, line, "label", label);
        }
    }

    private void handleSelect(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        boolean on = plugin.getSelectionManager().toggle(player.getUniqueId());
        plugin.getMessageManager().send(player, on ? "select.enabled" : "select.disabled");
    }

    private void handleCreate(CommandSender sender, String[] args) {
        MessageManager msg = plugin.getMessageManager();
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 3) {
            msg.send(sender, "usage.create");
            return;
        }
        String name = args[1];
        if (!Region.NAME_PATTERN.matcher(name).matches()) {
            msg.send(sender, "region.invalid-name");
            return;
        }
        RegionManager rm = plugin.getRegionManager();
        if (rm.get(name) != null) {
            msg.send(sender, "region.exists", "region", name);
            return;
        }
        ZoneMode mode = ZoneMode.parse(args[2], null);
        if (mode == null) {
            msg.send(sender, "usage.create");
            return;
        }
        Integer priority = 0;
        if (args.length >= 4) {
            priority = parseInt(args[3], Region.MIN_PRIORITY, Region.MAX_PRIORITY);
            if (priority == null) {
                msg.send(sender, "command.invalid-number", "min", String.valueOf(Region.MIN_PRIORITY),
                        "max", String.valueOf(Region.MAX_PRIORITY));
                return;
            }
        }
        SelectionManager.Selection sel = plugin.getSelectionManager().getComplete(player.getUniqueId());
        if (sel == null) {
            msg.send(sender, "select.incomplete");
            return;
        }
        if (rm.countInWorld(sel.world) >= plugin.getConfigManager().getMaxRegionsPerWorld()) {
            msg.send(sender, "region.limit", "max", String.valueOf(plugin.getConfigManager().getMaxRegionsPerWorld()));
            return;
        }
        Region region = new Region(name, sel.world, sel.x1, sel.y1, sel.z1, sel.x2, sel.y2, sel.z2,
                mode, priority, Region.USE_GLOBAL_TIME, null);
        rm.add(region);
        plugin.getSelectionManager().clear(player.getUniqueId());
        msg.send(sender, "region.created", "region", name, "mode", plugin.getGuiManager().modeText(mode),
                "priority", String.valueOf(region.getPriority()));
    }

    private void handleDelete(CommandSender sender, String[] args) {
        MessageManager msg = plugin.getMessageManager();
        if (args.length < 2) {
            msg.send(sender, "usage.delete");
            return;
        }
        Region region = plugin.getRegionManager().get(args[1]);
        if (region == null) {
            msg.send(sender, "region.not-found", "region", safe(args[1]));
            return;
        }
        plugin.getRegionManager().remove(region.getName());
        msg.send(sender, "region.deleted", "region", region.getName());
    }

    private void handleSet(CommandSender sender, String[] args) {
        MessageManager msg = plugin.getMessageManager();
        ConfigManager cfg = plugin.getConfigManager();
        RegionManager rm = plugin.getRegionManager();
        if (args.length < 4) {
            msg.send(sender, "usage.set");
            return;
        }
        Region region = rm.get(args[1]);
        if (region == null) {
            msg.send(sender, "region.not-found", "region", safe(args[1]));
            return;
        }
        String key = args[2].toLowerCase(Locale.ROOT);
        String value = args[3];
        switch (key) {
            case "mode" -> {
                ZoneMode mode = ZoneMode.parse(value, null);
                if (mode == null) {
                    msg.send(sender, "usage.set");
                    return;
                }
                rm.setMode(region, mode);
            }
            case "priority" -> {
                Integer p = parseInt(value, Region.MIN_PRIORITY, Region.MAX_PRIORITY);
                if (p == null) {
                    msg.send(sender, "command.invalid-number", "min", String.valueOf(Region.MIN_PRIORITY),
                            "max", String.valueOf(Region.MAX_PRIORITY));
                    return;
                }
                rm.setPriority(region, p);
            }
            case "time" -> {
                if (value.equalsIgnoreCase("default")) {
                    rm.setAfkTime(region, Region.USE_GLOBAL_TIME);
                } else {
                    Integer t = parseInt(value, 10, 86400);
                    if (t == null) {
                        msg.send(sender, "command.invalid-number", "min", "10", "max", "86400");
                        return;
                    }
                    rm.setAfkTime(region, t);
                }
            }
            case "profile" -> {
                if (value.equalsIgnoreCase("default")) {
                    rm.setProfile(region, null);
                } else {
                    String profile = value.toLowerCase(Locale.ROOT);
                    if (!cfg.getProfileNames().contains(profile)) {
                        msg.send(sender, "region.profile-not-found", "profile", safe(value),
                                "profiles", String.join(", ", cfg.getProfileNames()));
                        return;
                    }
                    rm.setProfile(region, profile);
                }
            }
            default -> {
                msg.send(sender, "usage.set");
                return;
            }
        }
        msg.send(sender, "region.updated", "region", region.getName());
        sendInfo(sender, region);
    }

    private void handleList(CommandSender sender) {
        MessageManager msg = plugin.getMessageManager();
        List<Region> regions = plugin.getRegionManager().sorted();
        if (regions.isEmpty()) {
            msg.send(sender, "list.empty");
            return;
        }
        msg.send(sender, "list.header", "count", String.valueOf(regions.size()));
        GuiManager gui = plugin.getGuiManager();
        for (Region r : regions) {
            msg.send(sender, "list.entry", "region", r.getName(), "world", r.getWorld(),
                    "mode", gui.modeText(r.getMode()), "priority", String.valueOf(r.getPriority()));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        MessageManager msg = plugin.getMessageManager();
        if (args.length < 2) {
            msg.send(sender, "usage.info");
            return;
        }
        Region region = plugin.getRegionManager().get(args[1]);
        if (region == null) {
            msg.send(sender, "region.not-found", "region", safe(args[1]));
            return;
        }
        sendInfo(sender, region);
    }

    private void sendInfo(CommandSender sender, Region r) {
        GuiManager gui = plugin.getGuiManager();
        plugin.getMessageManager().send(sender, "info.body",
                "region", r.getName(), "world", r.getWorld(),
                "min", r.getMinX() + ", " + r.getMinY() + ", " + r.getMinZ(),
                "max", r.getMaxX() + ", " + r.getMaxY() + ", " + r.getMaxZ(),
                "mode", gui.modeText(r.getMode()), "priority", String.valueOf(r.getPriority()),
                "time", gui.timeText(r), "profile", gui.profileText(r));
    }

    private void handleTeleport(CommandSender sender, String[] args) {
        MessageManager msg = plugin.getMessageManager();
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            msg.send(sender, "usage.tp");
            return;
        }
        Region region = plugin.getRegionManager().get(args[1]);
        if (region == null) {
            msg.send(sender, "region.not-found", "region", safe(args[1]));
            return;
        }
        GuiListener.teleport(plugin, player, region);
    }

    private void handleWorld(CommandSender sender, String[] args) {
        MessageManager msg = plugin.getMessageManager();
        RegionManager rm = plugin.getRegionManager();
        if (args.length < 2) {
            msg.send(sender, "usage.world");
            return;
        }
        String worldName;
        if (args.length >= 3) {
            World w = plugin.getServer().getWorld(args[2]);
            if (w == null) {
                msg.send(sender, "region.world-missing", "world", safe(args[2]));
                return;
            }
            worldName = w.getName();
        } else if (sender instanceof Player p) {
            worldName = p.getWorld().getName();
        } else {
            msg.send(sender, "usage.world");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("reset")) {
            rm.setWorldMode(worldName, null);
        } else {
            ZoneMode mode = ZoneMode.parse(action, null);
            if (mode == null) {
                msg.send(sender, "usage.world");
                return;
            }
            rm.setWorldMode(worldName, mode);
        }
        msg.send(sender, "world.set", "world", worldName,
                "mode", plugin.getGuiManager().modeText(rm.getWorldMode(worldName)));
    }

    private void handleCheck(CommandSender sender, String[] args) {
        MessageManager msg = plugin.getMessageManager();
        Player target;
        if (args.length >= 2) {
            target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                msg.send(sender, "command.player-not-found", "player", safe(args[1]));
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            msg.send(sender, "usage.check");
            return;
        }
        Location loc = target.getLocation();
        RegionManager.ZoneResult zone = plugin.getRegionManager().resolve(loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        String where = zone.region() != null ? zone.region().getName() : msg.raw("placeholder.no-region");
        msg.send(sender, "check.result", "player", target.getName(), "world", loc.getWorld().getName(),
                "region", where, "mode", plugin.getGuiManager().modeText(zone.mode()),
                "idle", String.valueOf(plugin.getAfkManager().getIdleSeconds(target.getUniqueId())),
                "bypass", msg.raw(target.hasPermission("antiafk.bypass") ? "state.on" : "state.off"));
    }

    private void handleVersion(CommandSender sender) {
        MessageManager msg = plugin.getMessageManager();
        UpdateChecker checker = plugin.getUpdateChecker();
        msg.send(sender, "update.checking");
        checker.check(result -> {
            String latest = result.latest() == null ? "-" : result.latest();
            String key = switch (result.status()) {
                case OUTDATED -> "update.outdated";
                case UP_TO_DATE -> "update.up-to-date";
                case DEV_BUILD -> "update.dev-build";
                case NO_RELEASE -> "update.no-release";
                case FAILED -> "update.failed";
            };
            msg.send(sender, key, "current", result.current(), "latest", latest, "url", UpdateChecker.RELEASES_URL);
        });
    }

    // ------------------------------------------------------------------ 工具

    private boolean checkPerm(CommandSender sender, String perm) {
        if (sender.hasPermission(perm)) {
            return true;
        }
        plugin.getMessageManager().send(sender, "command.no-permission");
        return false;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player p) {
            return p;
        }
        plugin.getMessageManager().send(sender, "command.player-only");
        return null;
    }

    private static Integer parseInt(String s, int min, int max) {
        try {
            int v = Integer.parseInt(s);
            return v < min || v > max ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 回顯玩家輸入時：限制長度並移除色碼字元 */
    private static String safe(String input) {
        String s = MessageManager.stripInput(input);
        return s.length() > 32 ? s.substring(0, 32) + "…" : s;
    }

    // ------------------------------------------------------------------ 補全

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        boolean admin = sender.hasPermission(PERM_ADMIN);
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (admin) {
                subs.addAll(ADMIN_SUBS);
            }
            if (sender.hasPermission(PERM_RELOAD)) {
                subs.add("reload");
            }
            if (sender.hasPermission(PERM_VERSION)) {
                subs.add("version");
            }
            return filter(subs, args[0]);
        }
        if (!admin) {
            return Collections.emptyList();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        RegionManager rm = plugin.getRegionManager();
        if (args.length == 2) {
            return switch (sub) {
                case "delete", "set", "info", "tp" -> filter(rm.names(), args[1]);
                case "create" -> filter(List.of("<name>"), args[1]);
                case "world" -> filter(List.of("allow", "deny", "reset"), args[1]);
                case "check" -> filter(onlineNames(), args[1]);
                default -> Collections.emptyList();
            };
        }
        if (args.length == 3) {
            return switch (sub) {
                case "create" -> filter(MODES, args[2]);
                case "set" -> filter(SET_KEYS, args[2]);
                case "world" -> {
                    List<String> worlds = new ArrayList<>();
                    for (World w : plugin.getServer().getWorlds()) {
                        worlds.add(w.getName());
                    }
                    yield filter(worlds, args[2]);
                }
                default -> Collections.emptyList();
            };
        }
        if (args.length == 4) {
            if (sub.equals("create")) {
                return filter(List.of("0", "10", "100"), args[3]);
            }
            if (sub.equals("set")) {
                return switch (args[2].toLowerCase(Locale.ROOT)) {
                    case "mode" -> filter(MODES, args[3]);
                    case "priority" -> filter(List.of("0", "10", "100"), args[3]);
                    case "time" -> filter(List.of("default", "60", "300", "600"), args[3]);
                    case "profile" -> {
                        List<String> p = new ArrayList<>(plugin.getConfigManager().getProfileNames());
                        p.add(0, "default");
                        yield filter(p, args[3]);
                    }
                    default -> Collections.emptyList();
                };
            }
        }
        return Collections.emptyList();
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(o);
            }
        }
        return out;
    }
}
