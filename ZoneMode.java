package clre20.antiAFK.region;

import java.util.Locale;

/**
 * 區域 / 世界的掛機模式。
 */
public enum ZoneMode {
    /** 可以掛機 */
    ALLOW,
    /** 禁止掛機 */
    DENY;

    public static ZoneMode parse(String value, ZoneMode fallback) {
        if (value == null) {
            return fallback;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "allow", "true", "可掛" -> ALLOW;
            case "deny", "false", "不可掛" -> DENY;
            default -> fallback;
        };
    }

    public ZoneMode toggle() {
        return this == ALLOW ? DENY : ALLOW;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
