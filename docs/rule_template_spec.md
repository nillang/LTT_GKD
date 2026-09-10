# 规则模板字段说明文档

> 适用版本：LTT_GKD 自用版
> 对应代码：`model/rule/RuleTemplate.kt` + `model/rule/Rule.kt`

---

## 一、整体数据模型

```
RuleSet
 ├─ name:        String           规则集名称
 ├─ version:     Int = 1          版本号
 ├─ author:      String           作者（设备 ID）
 └─ rules:       List<Rule>       规则列表
     └─ Rule
         ├─ 基本信息
         │   ├─ id:           String        全局唯一
         │   ├─ name:         String        显示名
         │   ├─ packageName:  String        目标包名
         │   ├─ activity:     String?       限定 Activity
         │   ├─ enabled:      Boolean       是否启用
         │   ├─ priority:     Int           优先级（越大越优先）
         │   └─ throttleMs:   Long          节流窗口（毫秒）
         ├─ 元信息（共享/排序用）
         │   ├─ author:       String        作者标识
         │   ├─ createdAt:     Long          创建时间戳（毫秒）
         │   ├─ subscribers:  Int           订阅数
         │   └─ source:       RuleSource    来源（LOCAL/SUBSCRIBED/BUILT_IN）
         ├─ match: MatchTarget              匹配目标
         └─ action: MatchAction             命中后动作
```

---

## 二、Rule 字段详解

### 2.1 基本信息

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | String | 是 | - | 全局唯一标识，建议格式 `<pkg>_<scene>`，如 `com.tencent.mm_splash`。同 ID 规则在合并时后者覆盖前者（订阅 > 本地 > 内置） |
| `name` | String | 是 | - | UI 显示名，建议"应用名 + 场景"，如 `微信开屏` |
| `packageName` | String | 否 | "" | 目标应用包名。空字符串 = 通用兜底，匹配任意应用（建议 priority 设最低） |
| `activity` | String? | 否 | null | 限定 Activity 类名。支持短类名（如 `SplashActivity`）或全限定名（`com.tencent.mm.ui.Splash`）。null = 不限定 |
| `enabled` | Boolean | 否 | true | 是否启用。false 时规则不会被加载，但仍保留在文件中 |
| `priority` | Int | 否 | 0 | 优先级。**越大越优先**。同包多规则按 priority 降序匹配。建议 0-100 |
| `throttleMs` | Long | 否 | 2000 | 节流窗口。同一规则在窗口内只触发一次，避免短时重复点击。单位毫秒 |

### 2.2 元信息（共享场景使用）

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `author` | String | "" | 作者标识，上传时自动填入设备 ID（Android ID 取前 16 位）。本地规则可不填 |
| `createdAt` | Long | 0 | 创建时间戳（毫秒）。本地规则按此倒序排序。新建时自动填 `System.currentTimeMillis()` |
| `subscribers` | Int | 0 | 订阅数。订阅规则按此倒序排序。从 Gist comments 数近似获取 |
| `source` | RuleSource | LOCAL | 规则来源。**不写入 JSON**，由文件路径决定：`local/` = LOCAL，`subscribed/` = SUBSCRIBED，`assets/rules/` = BUILT_IN |

#### RuleSource 枚举

| 值 | 含义 | 路径 | 排序方式 | 可写 |
|---|---|---|---|---|
| `LOCAL` | 本地私人规则 | `filesDir/rules/local/` | createdAt 倒序 | 用户可增删 |
| `SUBSCRIBED` | 远程订阅规则 | `filesDir/rules/subscribed/` | subscribers 倒序 | 同步覆盖 |
| `BUILT_IN` | 内置规则 | `assets/rules/*.json` | priority 降序 | 只读 |

**合并优先级**：`SUBSCRIBED > LOCAL > BUILT_IN`（同 ID 时后者覆盖前者）

---

## 三、MatchTarget 字段详解

```kotlin
data class MatchTarget(
    val type: MatchType = MatchType.TEXT,
    val text: List<String> = emptyList(),
    val ids: List<String> = emptyList(),
    val regex: Boolean = false,
    val caseInsensitive: Boolean = true
)
```

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `type` | MatchType | TEXT | 匹配方式，决定使用 `text` 还是 `ids` |
| `text` | List<String> | [] | TEXT/DESC/OCR 类型用，逗号分隔输入。任一关键词命中即匹配 |
| `ids` | List<String> | [] | ID 类型用。支持完整 ID（`com.tencent.mm:id/skip`）或简写（`skip` 自动后缀匹配） |
| `regex` | Boolean | false | true 时 `text` 按正则解析。如 `跳过\\s*\\d+s?` 匹配"跳过 3s" |
| `caseInsensitive` | Boolean | true | 大小写不敏感。建议保持 true，避免 "Skip" vs "skip" 漏匹配 |

### MatchType 枚举

| 值 | 匹配对象 | 使用字段 | 适用场景 |
|---|---|---|---|
| `TEXT` | `getText()` + `getContentDescription()` | text | 标准"跳过"按钮（最常用） |
| `ID` | viewId（`com.xx:id/xxx`） | ids | 已知固定布局的 App（最稳定） |
| `DESC` | 仅 `getContentDescription()` | text | 图标按钮（无文字，只有描述） |
| `OCR` | 截图识别文字坐标 | text | 控件树拿不到文字（自绘 UI），仅 Android 11+ |

**优先级建议**：`ID > TEXT > DESC > OCR`（稳定性递减）

---

## 四、MatchAction 字段详解

```kotlin
data class MatchAction(
    val type: ActionType = ActionType.CLICK_NODE,
    val delayMs: Long = 0L
)
```

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `type` | ActionType | CLICK_NODE | 命中后执行的动作类型 |
| `delayMs` | Long | 0 | 执行前延迟（毫秒）。常用于等动画播完再点 |

### ActionType 枚举

| 值 | 动作 | 适用场景 |
|---|---|---|
| `CLICK_NODE` | 调用 `performAction(ACTION_CLICK)` 点击节点本身或可点击祖先 | 标准可点击按钮（首选） |
| `CLICK_COORD` | 取节点中心坐标点击 | 节点不可点击但坐标可点（如自绘 UI） |
| `BACK` | 模拟返回键 | 全屏广告只能返回退出 |
| `GESTURE_TAP` | `dispatchGesture` 在坐标处点击 | OCR 路径兜底，模拟真实人手点击 |

---

## 五、5 个模板详解

### 5.1 SPLASH 开屏广告

| 字段 | 预填值 | 说明 |
|---|---|---|
| `priority` | 100 | 最高优先级，开屏需最快响应 |
| `throttleMs` | 5000 | 5 秒节流，开屏通常只展示一次 |
| `match.type` | TEXT | 文本匹配最通用 |
| `match.text` | ["跳过", "跳过广告", "Skip", "skip"] | 覆盖中英文常见文案 |
| `match.caseInsensitive` | true | "Skip" vs "skip" 都能匹配 |
| `action.type` | CLICK_NODE | 跳过按钮通常可点击 |

**适用场景**：App 启动时全屏 3-5 秒广告，跳过按钮位置不固定（右上/右下/中部）。

**注意事项**：
- 摇一摇开屏（传感器触发跳转）本模板**无法应对**，需配合禁用传感器权限
- 部分高对抗 App 用假跳过按钮诱导误触，需用 `ID` 精确匹配
- 倒计时未结束时按钮可能不可点，可加 `action.delayMs=2000` 等倒计时

---

### 5.2 POPUP 插屏弹窗

| 字段 | 预填值 | 说明 |
|---|---|---|
| `priority` | 80 | 中高优先级 |
| `throttleMs` | 3000 | 3 秒节流，避免连续弹窗重复点 |
| `match.type` | TEXT | - |
| `match.text` | ["关闭", "关闭广告", "×", "不再提示"] | 覆盖常见关闭文案 |
| `action.type` | CLICK_NODE | 关闭按钮通常可点击 |

**适用场景**：页面切换、操作触发的全屏弹窗，"×"关闭按钮常在右上角。

**注意事项**：
- "×" 实际是特殊字符，部分 App 用图标按钮，建议改用 `DESC` 匹配
- "不再提示"是复选框，点了可能反而关闭主功能，建议慎用

---

### 5.3 BANNER 横幅

| 字段 | 预填值 | 说明 |
|---|---|---|
| `priority` | 60 | 中等优先级 |
| `throttleMs` | 3000 | 3 秒节流 |
| `match.type` | TEXT | - |
| `match.text` | ["关闭", "不感兴趣", "×"] | 信息流广告常见选项 |
| `action.type` | CLICK_COORD | Banner 关闭按钮常不可点击，需坐标点 |

**适用场景**：页面顶部/底部条状广告，关闭按钮藏在小三角里。

**注意事项**：
- CLICK_COORD 需要 NodeUtils 计算节点中心坐标
- "不感兴趣"通常弹二级菜单，可能误点；建议先单独匹配"关闭"

---

### 5.4 REWARD_VIDEO 激励视频

| 字段 | 预填值 | 说明 |
|---|---|---|
| `priority` | 70 | 中高优先级 |
| `throttleMs` | 30000 | **30 秒节流**，激励视频通常 15-30 秒才出关闭按钮 |
| `match.type` | TEXT | - |
| `match.text` | ["关闭", "领取奖励", "跳过"] | 倒计时结束后常见文案 |
| `action.type` | CLICK_NODE | - |

**适用场景**：看广告换奖励（游戏复活、领金币等），倒计时结束才出现关闭按钮。

**注意事项**：
- **不能用于强行跳过未结束的视频**，会被 App 检测并封号
- 倒计时期间点关闭可能触发"放弃奖励"确认弹窗
- 建议保留 `throttleMs=30000`，避免节流窗口内重复尝试

---

### 5.5 UNIVERSAL 通用兜底

| 字段 | 预填值 | 说明 |
|---|---|---|
| `id` | `universal_<时间戳>` | 自动生成，避免与具体 App 规则冲突 |
| `packageName` | "" | **空 = 任意应用** |
| `priority` | 1 | **最低优先级**，避免提前命中导致针对性规则失效 |
| `throttleMs` | 5000 | 5 秒节流 |
| `match.type` | TEXT | - |
| `match.text` | ["跳过", "Skip"] | 仅匹配最通用的跳过文案 |
| `action.type` | CLICK_NODE | - |

**适用场景**：作为最后一道防线，匹配所有未配置专用规则的 App。

**注意事项**：
- priority 必须设最低（1），否则会拦截针对性规则
- 关键词必须保守（仅"跳过""Skip"），否则会误触正常按钮
- 建议每条 App 都配专用规则，通用兜底仅作为未覆盖场景的保险

---

## 六、模板组合推荐

### 6.1 新 App 接入流程

```
1. 应用列表 → 复制包名
2. 新建规则 → 选 SPLASH 模板 → 粘贴包名 → 测试
3. 若命中失败 → 改 ID 匹配（用 layout inspector 查 viewId）
4. 若 ID 仍失败 → 改 OCR 兜底
5. 验证稳定后 → 上传共享
```

### 6.2 复杂 App 多规则组合

某 App 同时有开屏 + 弹窗 + Banner，建议配置 3 条规则：

```json
{
  "rules": [
    {
      "id": "com.example.app_splash",
      "priority": 100,
      "match": { "type": "TEXT", "text": ["跳过"] },
      "action": { "type": "CLICK_NODE" }
    },
    {
      "id": "com.example.app_popup",
      "priority": 80,
      "match": { "type": "DESC", "text": ["关闭"] },
      "action": { "type": "CLICK_NODE" }
    },
    {
      "id": "com.example.app_banner",
      "priority": 60,
      "match": { "type": "TEXT", "text": ["不感兴趣"] },
      "action": { "type": "CLICK_COORD" }
    }
  ]
}
```

### 6.3 正则匹配示例

```
跳过\s*\d+s?      匹配 "跳过 3s" "跳过 5 s"
skip\s*\d+         匹配 "skip 5"
^\d+$              匹配纯数字倒计时（慎用）
```

---

## 七、字段速查总表

| 字段路径 | 类型 | 必填 | 默认 | 取值范围 / 说明 |
|---|---|---|---|---|
| `id` | String | 是 | - | 全局唯一，建议 `<pkg>_<scene>` |
| `name` | String | 是 | - | 显示名 |
| `packageName` | String | 否 | "" | 空 = 通用兜底 |
| `activity` | String? | 否 | null | 短类名或全限定名 |
| `enabled` | Boolean | 否 | true | 是否启用 |
| `priority` | Int | 否 | 0 | 越大越优先 |
| `throttleMs` | Long | 否 | 2000 | 节流窗口（ms） |
| `author` | String | 否 | "" | 作者标识 |
| `createdAt` | Long | 否 | 0 | 创建时间戳 |
| `subscribers` | Int | 否 | 0 | 订阅数 |
| `source` | RuleSource | 否 | LOCAL | LOCAL/SUBSCRIBED/BUILT_IN（不入 JSON） |
| `match.type` | MatchType | 是 | TEXT | TEXT/ID/DESC/OCR |
| `match.text` | List<String> | 视情况 | [] | TEXT/DESC/OCR 用 |
| `match.ids` | List<String> | 视情况 | [] | ID 用，支持简写 |
| `match.regex` | Boolean | 否 | false | text 是否按正则解析 |
| `match.caseInsensitive` | Boolean | 否 | true | 大小写不敏感 |
| `action.type` | ActionType | 是 | CLICK_NODE | CLICK_NODE/CLICK_COORD/BACK/GESTURE_TAP |
| `action.delayMs` | Long | 否 | 0 | 执行前延迟（ms） |

---

## 八、模板与字段推荐搭配矩阵

| 模板 | priority | throttleMs | match.type | match.text 推荐 | action.type |
|---|---|---|---|---|---|
| SPLASH | 100 | 5000 | TEXT | 跳过, 跳过广告, Skip | CLICK_NODE |
| POPUP | 80 | 3000 | TEXT 或 DESC | 关闭, ×, 不再提示 | CLICK_NODE |
| BANNER | 60 | 3000 | TEXT | 关闭, 不感兴趣 | CLICK_COORD |
| REWARD_VIDEO | 70 | 30000 | TEXT | 关闭, 领取奖励 | CLICK_NODE |
| UNIVERSAL | 1 | 5000 | TEXT | 跳过, Skip | CLICK_NODE |

---

## 九、规则存储路径

| 来源 | 路径 | 文件名约定 |
|---|---|---|
| 本地 | `filesDir/rules/local/` | `manual_<safe_id>.json`（safe_id 已过滤非法字符） |
| 订阅 | `filesDir/rules/subscribed/` | `<author>_<rule_id>.json`（来自 Gist 文件名） |
| 内置 | `assets/rules/` | 按类别：`social.json` / `shopping.json` / `tools.json` 等 |

---

## 十、上传订阅流程

```
[本地规则]
    ↓ 点击"上传共享"
    ↓
[DeviceIdProvider 取设备 ID]
    ↓
[RuleSet JSON 包装]
    ↓
[GistClient.uploadRuleSet]
    ↓
首次：POST /gists → 返回 gistId
后续：PATCH /gists/{gistId} → 覆盖同名文件
    ↓
[自动保存 gistId 到 DataStore]
    ↓
[同时保存一份到本地 local/]
```

```
[订阅方]
    ↓ 设置页填 Gist ID
    ↓ 点击"同步订阅"
    ↓
[GistClient.fetchRuleSets] → 拉取所有 .json
    ↓
[GistClient.fetchSubscriberCount] → 取 comments 数
    ↓
[写入 filesDir/rules/subscribed/]
    ↓
[RuleRepository.reload] → 合并加载
    ↓
[订阅 Tab 按 subscribers 倒序展示]
```

---

## 十一、常见陷阱与避坑

1. **节流作用域**：`throttleMs` 按**规则 ID**单独计时，不会互相影响
2. **通用兜底必须最低优先级**：`priority=1`，否则会拦截针对性规则
3. **OCR 仅 Android 11+**：低版本自动跳过 OCR 路径
4. **regex 误用**：开启后 `text` 中所有特殊字符都会被解释，普通文本可能匹配失败
5. **CLICK_COORD 与 GESTURE_TAP 区别**：前者需要节点存在（取坐标），后者完全脱离节点（OCR 路径用）
6. **同 ID 覆盖逻辑**：订阅 > 本地 > 内置，用户用本地版本可覆盖内置默认
7. **subscribers 不实时更新**：仅在"同步订阅"时拉取一次，不上传时不会变
8. **设备 ID 隐私**：仅 Android ID 前 16 位，不包含 IMEI 等强标识符
9. **Token 安全**：仅存本地 DataStore，不进入日志，不上传
10. **Gist 私有性**：上传时 `public=false`，不会被随机爬虫发现
