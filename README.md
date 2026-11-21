# AntiAFK

AntiAFK 是一款專為 Paper 26.3 (標準穩定版) (Minecraft 26.1.2+) 伺服器設計的區域掛機管理插件。管理員可以用空手右鍵選取範圍，為每個範圍設定「可掛機」或「禁止掛機」，再搭配世界預設值，自由組合出「只有這裡能掛機」或「只有這裡不能掛機」的規則。玩家在禁止掛機的地方閒置過久，會先收到預警，仍不活動則自動以控制台身分執行你設定的指令（踢出、傳送、廣播等）。


> **版本檢查提示**：若伺服器有安裝 [HttpHub](https://github.com/clre20/HttpHub)，本插件自動由 HttpHub 集中處理版本檢查與管理員登入通知，避免各插件自開連線與背景執行緒。

## 功能特性

* **範圍 + 世界預設的雙層規則**：
  * 每個世界有一個預設值（可掛機 / 禁止掛機），沒有被任何範圍覆蓋的地方套用世界預設。
  * 每個範圍只設定「範圍內」可掛或不可掛，可建立多個範圍。
  * 範圍重疊時依「優先度」決定，數字大的優先。
  * 例：世界預設禁止掛機，A 區設可掛、B 區設禁止 → A 可掛、B 不可掛、其他地方不可掛。
* **防掛機機的判定方式**：
  * 只有「轉動視角」才算活動，水流、礦車、活塞推動等位置變化一律不算。
  * 視角需轉動超過門檻（預設 0.5 度）才算，微小抖動不算；慢慢轉動會累積計算。
  * 聊天與輸入指令可在 `config.yml` 選擇是否算活動。
* **兩階段處置**：
  * 閒置達設定時間（預設 5 分鐘）→ 標題 + 聊天預警。
  * 預警後再過設定時間（預設 1 分鐘）仍在掛機 → 執行動作組的指令。
  * 預警期間恢復活動會自動取消；可選擇持續掛機時每隔一段時間重複執行。
* **動作組（Action Profiles）**：
  * 在 `config.yml` 自訂多組指令，每個範圍可指定使用哪一組。
  * 支援變數 `%player%`、`%uuid%`、`%region%`、`%world%`。
  * 玩家名稱含有特殊字元時，`%player%` 會自動改用 UUID，防止指令注入。
* **空手右鍵選取**：
  * `/antiafk select` 進入選取模式後，空手右鍵點方塊輪流設定點 1、點 2，不需要額外工具。
  * 選取時會取消原本的互動，不會順便開箱子或按按鈕；不在選取模式時完全不影響遊戲。
* **箱子 UI 管理介面**：
  * 列出所有範圍，左鍵即可切換模式、調整優先度、切換動作組、調整掛機時間、傳送、刪除（需二次確認）。
  * 可直接切換目前世界的預設值與選取模式。
  * 只接受左鍵，Shift、右鍵、拖曳一律無效；介面帶有 `AntiAFKHolder` 簽署，方便其他插件辨識。
* **效能與資料安全**：
  * 事件處理皆為 O(1)，判定集中在每秒一次的排程中。
  * 範圍資料全部存在記憶體，變更後定時非同步批次寫入 `regions.yml`，關服時同步存檔。
  * 不持有 Player / World 實例，玩家離線時立即清除快取。
* **設定檔自動補齊**：
  * `config.yml` 與 `messages.yml` 缺少的項目會在啟動與重載時自動補上，保留原本的值。
  * 所有顯示文字都在 `messages.yml`，支援 `&` 色碼。
* **檢查更新**：
  * 啟動時以非同步方式向 GitHub Releases 檢查更新，結果快取 10 分鐘，不會定時輪詢。
  * 具備 `antiafk.version` 權限的管理員登入 3 秒後會收到新版本提醒，兩項功能都可在 `config.yml` 的 `update-checker` 關閉。

## 指令與權限

本插件主指令為 `/antiafk`。

| 指令 | 說明 | 執行端 | 權限節點 | 預設 |
| :--- | :--- | :--- | :--- | :--- |
| `/antiafk help` | 顯示指令說明 | 遊戲內 / 控制台 | `antiafk.admin` | OP |
| `/antiafk select` | 切換選取模式（空手右鍵點方塊） | 僅限玩家 | `antiafk.admin` | OP |
| `/antiafk create <名稱> <allow\|deny> [優先度]` | 用選取的兩點建立範圍 | 僅限玩家 | `antiafk.admin` | OP |
| `/antiafk delete <名稱>` | 刪除範圍 | 遊戲內 / 控制台 | `antiafk.admin` | OP |
| `/antiafk set <名稱> mode <allow\|deny>` | 修改範圍模式 | 遊戲內 / 控制台 | `antiafk.admin` | OP |
| `/antiafk set <名稱> priority <數字>` | 修改優先度（-1000 ~ 1000） | 遊戲內 / 控制台 | `antiafk.admin` | OP |
| `/antiafk set <名稱> time <秒數\|default>` | 修改此範圍的掛機時間（10 ~ 86400） | 遊戲內 / 控制台 | `antiafk.admin` | OP |
| `/antiafk set <名稱> profile <動作組\|default>` | 指定此範圍的動作組 | 遊戲內 / 控制台 | `antiafk.admin` | OP |
| `/antiafk list` | 列出所有範圍 | 遊戲內 / 控制台 | `antiafk.admin` | OP |
| `/antiafk info <名稱>` | 查看範圍資訊 | 遊戲內 / 控制台 | `antiafk.admin` | OP |
| `/antiafk tp <名稱>` | 傳送到範圍中心地表 | 僅限玩家 | `antiafk.admin` | OP |
| `/antiafk world <allow\|deny\|reset> [世界]` | 設定世界預設值（reset 改回 `config.yml` 的 `worlds.default-mode`） | 遊戲內 / 控制台 | `antiafk.admin` | OP |
| `/antiafk check [玩家]` | 查看玩家所在位置的判定、閒置秒數與是否豁免 | 遊戲內 / 控制台 | `antiafk.admin` | OP |
| `/antiafk gui` | 開啟箱子 UI 管理介面 | 僅限玩家 | `antiafk.admin` | OP |
| `/antiafk reload` | 重新載入 `config.yml` 與 `messages.yml` | 遊戲內 / 控制台 | `antiafk.reload` | OP |
| `/antiafk version` | 檢查更新 | 遊戲內 / 控制台 | `antiafk.version` | OP |
| — | 不受掛機限制 | — | `antiafk.bypass` | OP |

> 指令別名：`/aafk` 等同 `/antiafk`。
>
> `antiafk.*` 包含以上所有權限。OP 預設擁有 `antiafk.bypass`，測試掛機效果時請用非 OP 帳號，或用權限插件拿掉該權限。

## 使用範例

### 1. 整個世界禁止掛機，只開放掛機區
1. 把世界預設設為禁止掛機：
   ```bash
   /antiafk world deny
   ```
2. 進入選取模式，空手右鍵點掛機區的兩個對角方塊：
   ```bash
   /antiafk select
   ```
3. 建立可掛機的範圍：
   ```bash
   /antiafk create afk_room allow
   ```

### 2. 整個世界可掛機，只有某區禁止
```bash
/antiafk world allow
/antiafk select
/antiafk create boss_arena deny
```

### 3. 大範圍可掛，裡面一小塊不行（重疊）
```bash
/antiafk create town allow 0
/antiafk create town_shop deny 10
```
`town_shop` 優先度較高，所以商店內禁止掛機，城鎮其他地方可以掛機。

### 4. 自訂動作組
在 `config.yml` 新增：
```yaml
actions:
  profiles:
    to_spawn:
      commands:
        - "minecraft:tp %player% 0 80 0"
        - "tellraw %player% {\"text\":\"你因掛機被傳送回重生點\"}"
      repeat-interval: 0
```
重載後指定給範圍：
```bash
/antiafk reload
/antiafk set boss_arena profile to_spawn
```

## 設定檔

| 欄位 | 說明 | 預設 |
| :--- | :--- | :--- |
| `afk.afk-time` | 閒置多少秒後預警 | `300` |
| `afk.action-delay` | 預警後再過多少秒執行指令 | `60` |
| `afk.check-interval-ticks` | 檢查頻率（tick） | `20` |
| `afk.min-rotation` | 視角轉動超過幾度才算活動 | `0.5` |
| `afk.count-chat` / `afk.count-commands` | 聊天 / 指令是否算活動 | `true` |
| `afk.warning.title` / `chat` / `cancel-message` | 預警標題、預警訊息、取消訊息 | `true` |
| `worlds.default-mode` | 沒設定過的世界的預設值 | `allow` |
| `regions.max-per-world` | 每個世界的範圍上限 | `100` |
| `regions.auto-save-seconds` | 範圍資料寫入間隔 | `60` |
| `gui.time-step` | GUI 每次加減的秒數 | `30` |
| `actions.default-profile` | 預設動作組 | `default` |
| `update-checker.check-on-startup` | 啟動時檢查更新 | `true` |
| `update-checker.notify-on-join` | 管理員登入提醒 | `true` |

範圍與世界預設值儲存在 `plugins/AntiAFK/regions.yml`，請用指令或 GUI 修改。


# AntiAFK


> **Update Checker**: When [HttpHub](https://github.com/clre20/HttpHub) is installed, update checks and operator login alerts are handled centrally by HttpHub via virtual threads, preventing redundant background connections.

AntiAFK is a region-based AFK control plugin for Paper 26.3 (標準穩定版) (Minecraft 26.1.2+) servers. Administrators select areas by right-clicking blocks with an empty hand, mark each area as "AFK allowed" or "AFK denied", and combine them with a per-world default to build rules such as "AFK only here" or "no AFK only here". Players who idle too long in a denied area first receive a warning; if they stay inactive, the plugin runs your configured commands as the console (kick, teleport, broadcast, and so on).

## Features

* **Two-layer rules: regions + world default**:
  * Each world has a default mode (allow / deny) that applies anywhere not covered by a region.
  * Each region only defines whether AFK is allowed inside it; you can create as many as you need.
  * Overlapping regions are resolved by priority — the higher number wins.
  * Example: world default deny, region A allow, region B deny → A allowed, B denied, everywhere else denied.
* **AFK-machine-proof detection**:
  * Only camera rotation counts as activity; being pushed by water, minecarts, or pistons does not.
  * Rotation must exceed a threshold (0.5° by default), so tiny jitter is ignored while slow turning still accumulates.
  * Chat and commands can optionally count as activity.
* **Two-stage handling**:
  * Idle for the configured time (5 minutes by default) → title + chat warning.
  * Still idle after the action delay (1 minute by default) → the action profile's commands run.
  * Activity during the warning cancels it; commands can optionally repeat while the player stays idle.
* **Action profiles**:
  * Define multiple command sets in `config.yml` and assign one per region.
  * Placeholders: `%player%`, `%uuid%`, `%region%`, `%world%`.
  * If a player name contains unusual characters, `%player%` falls back to the UUID to prevent command injection.
* **Empty-hand selection**:
  * After `/antiafk select`, right-click blocks with an empty hand to set point 1 and point 2 — no extra tools needed.
  * The original interaction is cancelled while selecting; outside selection mode gameplay is unaffected.
* **Chest GUI**:
  * Lists every region; left-click to toggle mode, change priority, cycle action profile, adjust AFK time, teleport, or delete (with confirmation).
  * Toggle the current world's default and selection mode directly from the GUI.
  * Left-click only; Shift, right-click, and drag do nothing. Inventories carry an `AntiAFKHolder` signature for other plugins to identify.
* **Performance & data safety**:
  * All event handlers are O(1); detection runs in a single once-per-second task.
  * Region data lives in memory and is written to `regions.yml` asynchronously in batches, plus a synchronous save on shutdown.
  * No Player / World instances are held; caches are cleared when players quit.
* **Auto-completing config files**:
  * Missing keys in `config.yml` and `messages.yml` are added on startup and reload while keeping existing values.
  * All displayed text lives in `messages.yml` with `&` color code support.
* **Update checker**:
  * Checks GitHub Releases asynchronously on startup; results are cached for 10 minutes with no polling.
  * Admins with `antiafk.version` are notified 3 seconds after joining; both features can be disabled under `update-checker`.

## Commands & Permissions

The primary command for this plugin is `/antiafk`.

| Command | Description | Executor | Permission Node | Default |
| :--- | :--- | :--- | :--- | :--- |
| `/antiafk help` | Show command help | In-game / Console | `antiafk.admin` | OP |
| `/antiafk select` | Toggle selection mode (empty-hand right-click) | Player only | `antiafk.admin` | OP |
| `/antiafk create <name> <allow\|deny> [priority]` | Create a region from the two selected points | Player only | `antiafk.admin` | OP |
| `/antiafk delete <name>` | Delete a region | In-game / Console | `antiafk.admin` | OP |
| `/antiafk set <name> mode <allow\|deny>` | Change region mode | In-game / Console | `antiafk.admin` | OP |
| `/antiafk set <name> priority <number>` | Change priority (-1000 to 1000) | In-game / Console | `antiafk.admin` | OP |
| `/antiafk set <name> time <seconds\|default>` | Change the region's AFK time (10 to 86400) | In-game / Console | `antiafk.admin` | OP |
| `/antiafk set <name> profile <profile\|default>` | Assign an action profile | In-game / Console | `antiafk.admin` | OP |
| `/antiafk list` | List all regions | In-game / Console | `antiafk.admin` | OP |
| `/antiafk info <name>` | Show region details | In-game / Console | `antiafk.admin` | OP |
| `/antiafk tp <name>` | Teleport to the surface at the region center | Player only | `antiafk.admin` | OP |
| `/antiafk world <allow\|deny\|reset> [world]` | Set a world's default (reset returns to `worlds.default-mode`) | In-game / Console | `antiafk.admin` | OP |
| `/antiafk check [player]` | Show a player's zone result, idle seconds, and bypass status | In-game / Console | `antiafk.admin` | OP |
| `/antiafk gui` | Open the chest GUI | Player only | `antiafk.admin` | OP |
| `/antiafk reload` | Reload `config.yml` and `messages.yml` | In-game / Console | `antiafk.reload` | OP |
| `/antiafk version` | Check for updates | In-game / Console | `antiafk.version` | OP |
| — | Exempt from AFK rules | — | `antiafk.bypass` | OP |

> Command Alias: `/aafk` is an alias for `/antiafk`.
>
> `antiafk.*` grants every permission above. OPs have `antiafk.bypass` by default — test with a non-OP account or remove that permission with a permissions plugin.