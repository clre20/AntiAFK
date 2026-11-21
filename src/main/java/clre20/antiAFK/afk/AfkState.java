package clre20.antiAFK.afk;

/**
 * 單一玩家的掛機狀態。
 * <p>
 * {@link #lastActivity} 可能由非同步聊天事件寫入，因此為 volatile；
 * 其餘欄位只在主執行緒讀寫。
 */
final class AfkState {

    enum Stage {
        /** 正常 */
        NORMAL,
        /** 已預警，等待執行指令 */
        WARNED,
        /** 已執行過指令 */
        PUNISHED
    }

    volatile long lastActivity;

    float lastYaw;
    float lastPitch;
    boolean hasOrientation;

    Stage stage = Stage.NORMAL;
    /** 進入目前階段的時間 */
    long stageStart;
    /** 下次重複執行的時間 */
    long nextRepeatAt = Long.MAX_VALUE;

    AfkState(long now) {
        this.lastActivity = now;
    }

    void reset() {
        stage = Stage.NORMAL;
        stageStart = 0L;
        nextRepeatAt = Long.MAX_VALUE;
    }
}
