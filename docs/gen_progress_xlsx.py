# -*- coding: utf-8 -*-
"""生成 LTT_GKD 项目进度跟踪 xlsx 表格。
运行: python -m pip install --target .pylibs openpyxl
      set PYTHONPATH=.pylibs && python docs/gen_progress_xlsx.py
"""
import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", ".pylibs"))

from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.utils import get_column_letter

OUT = os.path.join(os.path.dirname(__file__), "LTT_GKD_项目进度跟踪.xlsx")

# ---------- 样式 ----------
TITLE_FONT = Font(name="微软雅黑", size=14, bold=True, color="FFFFFF")
HEADER_FONT = Font(name="微软雅黑", size=11, bold=True, color="FFFFFF")
CELL_FONT = Font(name="微软雅黑", size=10)
BOLD_FONT = Font(name="微软雅黑", size=10, bold=True)
PROGRESS_FONT = Font(name="微软雅黑", size=10, bold=True, color="2F5496")

TITLE_FILL = PatternFill("solid", fgColor="2F5496")
HEADER_FILL = PatternFill("solid", fgColor="4472C4")
DONE_FILL = PatternFill("solid", fgColor="C6EFCE")
DOING_FILL = PatternFill("solid", fgColor="FFEB9C")
TODO_FILL = PatternFill("solid", fgColor="FFC7CE")
ALT_FILL = PatternFill("solid", fgColor="F2F2F2")

CENTER = Alignment(horizontal="center", vertical="center", wrap_text=True)
LEFT = Alignment(horizontal="left", vertical="center", wrap_text=True)

thin = Side(style="thin", color="BFBFBF")
BORDER = Border(left=thin, right=thin, top=thin, bottom=thin)


def style_header(ws, row, ncols):
    for c in range(1, ncols + 1):
        cell = ws.cell(row=row, column=c)
        cell.font = HEADER_FONT
        cell.fill = HEADER_FILL
        cell.alignment = CENTER
        cell.border = BORDER


def style_title(ws, row, ncols, text):
    ws.merge_cells(start_row=row, start_column=1, end_row=row, end_column=ncols)
    cell = ws.cell(row=row, column=1, value=text)
    cell.font = TITLE_FONT
    cell.fill = TITLE_FILL
    cell.alignment = CENTER
    ws.row_dimensions[row].height = 26


def write_rows(ws, start_row, rows, status_col=None):
    for i, row in enumerate(rows):
        r = start_row + i
        for c, val in enumerate(row, start=1):
            cell = ws.cell(row=r, column=c, value=val)
            cell.font = CELL_FONT
            cell.alignment = LEFT if c > 1 else CENTER
            cell.border = BORDER
            if i % 2 == 1:
                cell.fill = ALT_FILL
        if status_col:
            sc = ws.cell(row=r, column=status_col)
            sv = str(sc.value or "")
            if "已完成" in sv or "完成" in sv:
                sc.fill = DONE_FILL
            elif "进行中" in sv or "开发中" in sv:
                sc.fill = DOING_FILL
            elif "待开发" in sv or "未开始" in sv or "规划中" in sv:
                sc.fill = TODO_FILL


def set_widths(ws, widths):
    for i, w in enumerate(widths, start=1):
        ws.column_dimensions[get_column_letter(i)].width = w


wb = Workbook()

# =========================================================
# Sheet 1: 项目总览
# =========================================================
ws1 = wb.active
ws1.title = "项目总览"
style_title(ws1, 1, 2, "LTT_GKD 项目总览")

overview = [
    ("项目名称", "LTT_GKD - Android 自动跳广告工具"),
    ("项目定位", "基于无障碍服务的开屏/弹窗/Banner 广告自动跳过工具，支持本地/订阅/内置三套规则源与 OCR 兜底"),
    ("目标平台", "Android 8.0 (API 26) 及以上，targetSdk 34"),
    ("开发语言", "Kotlin + Jetpack Compose (Material3)"),
    ("核心依赖", "Moshi(JSON) · OkHttp(网络) · Coroutines(异步) · MLKit(端侧OCR) · DataStore(偏好)"),
    ("架构风格", "Package-by-feature（按功能模块组织，非传统 MVC）"),
    ("包名", "com.ltt.gkd"),
    ("源文件数", "38 个 .kt 文件"),
    ("内置规则", "13 个分类文件 / 84 条规则，覆盖社交/电商/工具/生活/社区/资讯/游戏/音乐/视频/阅读/浏览器/通用兜底"),
    ("代码仓库", "https://github.com/nillang/LTT_GKD"),
    ("创建日期", "2026-09-10"),
    ("最后更新", "2026-09-11"),
]
for i, (k, v) in enumerate(overview, start=2):
    a = ws1.cell(row=i, column=1, value=k)
    a.font = Font(name="微软雅黑", size=10, bold=True, color="FFFFFF")
    a.alignment = CENTER
    a.fill = HEADER_FILL
    a.border = BORDER
    b = ws1.cell(row=i, column=2, value=v)
    b.font = CELL_FONT
    b.alignment = LEFT
    b.border = BORDER
set_widths(ws1, [16, 80])

# 开发进度统计
r0 = len(overview) + 3
style_title(ws1, r0, 2, "开发进度统计")
total = 59
done = 48
pct = f"{done}/{total} = {done*100//total}%"
progress_rows = [
    ("总功能点数", str(total)),
    ("已完成", str(done)),
    ("待开发", str(total - done)),
    ("完成率", pct),
]
for i, (k, v) in enumerate(progress_rows):
    r = r0 + 1 + i
    a = ws1.cell(row=r, column=1, value=k)
    a.font = BOLD_FONT
    a.alignment = CENTER
    a.border = BORDER
    b = ws1.cell(row=r, column=2, value=v)
    b.font = PROGRESS_FONT if k == "完成率" else CELL_FONT
    b.alignment = CENTER
    b.border = BORDER

# 评价区
r1 = r0 + len(progress_rows) + 2
style_title(ws1, r1, 2, "项目评价（已更新）")
eval_rows = [
    ("优点", "架构清晰，职责分离到位（service/accessibility/action/gesture/ocr/data 分层明确）；Compose UI 与业务逻辑解耦；规则引擎设计灵活（多匹配类型+节流+优先级）；OCR 兜底补全了不可点击场景；单例复用与 launchSafe 等约定体现了工程纪律。"),
    ("亮点", "三套规则源（本地/订阅/内置）+ GitHub Gist 共享机制；端侧 MLKit OCR 不依赖 Google Play；Token 使用 Android Keystore AES-GCM 加密存储；应用白名单自动排除系统应用，用户可自由开关。"),
    ("已改进", "✓ 编译错误修复（3文件）；✓ 规则编辑器输入校验；✓ R8/ProGuard 混淆与压缩；✓ Gist Token 加密存储（Keystore AES-GCM）；✓ 应用白名单（WhitelistStore + 系统应用自动排除 + Switch/FilterChip UI）。"),
    ("待改进", "尚无单元测试/Instrumented 测试；订阅错误处理与重试策略较薄弱；缺少 CI 自动构建配置。"),
    ("风险点", "无障碍服务易被系统杀死，保活策略需验证；OCR 截图需前台服务+MEDIA_PROJECTION 权限，用户授权链路较长。"),
]
for i, (k, v) in enumerate(eval_rows):
    r = r1 + 1 + i
    a = ws1.cell(row=r, column=1, value=k)
    a.font = BOLD_FONT
    a.alignment = CENTER
    a.border = BORDER
    b = ws1.cell(row=r, column=2, value=v)
    b.font = CELL_FONT
    b.alignment = LEFT
    b.border = BORDER
    ws1.row_dimensions[r].height = 70

# =========================================================
# Sheet 2: 功能清单（核心跟踪表）— 新增"实现目录"和"进度"列
# =========================================================
ws2 = wb.create_sheet("功能清单")
NCOLS2 = 8
style_title(ws2, 1, NCOLS2, "功能清单与完成进度（每完成一个功能在此更新状态）")
headers = ["序号", "模块", "功能点", "实现目录", "状态", "完成日期", "进度", "备注"]
for c, h in enumerate(headers, start=1):
    ws2.cell(row=2, column=c, value=h)
style_header(ws2, 2, NCOLS2)

# 格式: (模块, 功能点, 实现目录, 状态, 完成日期, 进度, 备注)
features = [
    # ---- 已完成 ----
    ("无障碍服务", "SkipAccessibilityService 核心服务", "service/SkipAccessibilityService.kt", "已完成", "2026-09-10", "100%", "事件分发→WindowEventProcessor"),
    ("无障碍服务", "前台服务保活 RuleSubscriptionService", "service/RuleSubscriptionService.kt", "已完成", "2026-09-10", "100%", "specialUse FGS"),
    ("规则引擎", "Rule 数据模型", "data/rule/Rule.kt", "已完成", "2026-09-10", "100%", "Moshi @JsonClass"),
    ("规则引擎", "RuleEngine 候选筛选+节流", "data/rule/RuleEngine.kt", "已完成", "2026-09-10", "100%", "throttleMap + priority"),
    ("规则引擎", "RuleMatcher 节点匹配", "data/rule/RuleMatcher.kt", "已完成", "2026-09-10", "100%", "TEXT/ID/DESC + NodeUtils"),
    ("规则引擎", "RuleRepository 三源规则加载", "data/rule/RuleRepository.kt", "已完成", "2026-09-10", "100%", "local/subscribed/built-in"),
    ("匹配机制", "OCR 截图识别兜底（MLKit 中文）", "ocr/OcrManager.kt", "已完成", "2026-09-11", "100%", "修复: takeScreenshot 3参数+HardwareBuffer"),
    ("动作执行", "ActionExecutor 动作执行", "action/ActionExecutor.kt", "已完成", "2026-09-10", "100%", "CLICK_NODE/COORD/BACK/GESTURE_TAP"),
    ("动作执行", "GestureSimulator 手势模拟", "gesture/GestureSimulator.kt", "已完成", "2026-09-10", "100%", "dispatchGesture"),
    ("窗口处理", "WindowEventProcessor 事件处理", "accessibility/WindowEventProcessor.kt", "已完成", "2026-09-10", "100%", "Mutex 串行化"),
    ("窗口处理", "SkipNotifier 跳过通知", "accessibility/SkipNotifier.kt", "已完成", "2026-09-10", "100%", "可配置开关"),
    ("UI-主界面", "MainActivity 主界面", "ui/main/MainActivity.kt + MainScreen.kt", "已完成", "2026-09-10", "100%", "状态卡片+统计+导航"),
    ("UI-规则", "RuleListActivity 规则列表", "ui/rule/RuleListActivity.kt + RuleListScreen.kt", "已完成", "2026-09-10", "100%", "按来源分组"),
    ("UI-规则", "RuleEditActivity 规则编辑", "ui/rule/RuleEditActivity.kt + RuleEditScreen.kt", "已完成", "2026-09-11", "100%", "新增输入校验 validateRule"),
    ("UI-应用", "AppListActivity 应用列表", "ui/app/AppListActivity.kt + AppListScreen.kt", "已完成", "2026-09-10", "100%", "缓存+refresh"),
    ("UI-日志", "LogViewerActivity 日志查看", "ui/log/LogViewerActivity.kt + LogViewerScreen.kt", "已完成", "2026-09-10", "100%", "LazyColumn keys 优化"),
    ("UI-设置", "SettingsActivity 设置面板", "ui/settings/SettingsActivity.kt + SettingsScreen.kt", "已完成", "2026-09-11", "100%", "Token 加密说明已更新"),
    ("设置存储", "SettingsStore DataStore 偏好", "data/prefs/SettingsStore.kt", "已完成", "2026-09-11", "100%", "Token 加密存储 TokenCipher"),
    ("规则订阅", "GistClient GitHub Gist", "data/subscription/GistClient.kt", "已完成", "2026-09-11", "100%", "修复: JSONObject 导入+类型推断"),
    ("工具类", "Logger 日志", "util/Logger.kt", "已完成", "2026-09-10", "100%", "异步初始化+按日期分文件"),
    ("工具类", "NodeUtils 节点处理", "util/NodeUtils.kt", "已完成", "2026-09-10", "100%", "findFirst/safeRecycle 防回收"),
    ("工具类", "HttpClientHolder/GlobalMoshi/LaunchExt", "util/HttpClientHolder.kt + GlobalMoshi.kt + LaunchExt.kt", "已完成", "2026-09-10", "100%", "连接池复用+launchSafe"),
    ("工具类", "DeviceIdProvider 设备标识", "util/DeviceIdProvider.kt", "已完成", "2026-09-10", "100%", ""),
    ("工具类", "TokenCipher Token 加密", "util/TokenCipher.kt", "已完成", "2026-09-11", "100%", "Android Keystore AES-GCM"),
    ("内置规则", "general 通用兜底（3 条）", "assets/rules/general.json", "已完成", "2026-09-10", "100%", ""),
    ("内置规则", "social 社交（9 条）", "assets/rules/social.json", "已完成", "2026-09-10", "100%", ""),
    ("内置规则", "ecommerce 电商（7 条）", "assets/rules/ecommerce.json", "已完成", "2026-09-10", "100%", ""),
    ("内置规则", "tools 工具（4 条）", "assets/rules/tools.json", "已完成", "2026-09-10", "100%", ""),
    ("内置规则", "tools_extra 扩展工具（8 条）", "assets/rules/tools_extra.json", "已完成", "2026-09-10", "100%", ""),
    ("内置规则", "life 生活（7 条）", "assets/rules/life.json", "已完成", "2026-09-10", "100%", ""),
    ("内置规则", "community 社区（6 条）", "assets/rules/community.json", "已完成", "2026-09-10", "100%", ""),
    ("内置规则", "news 资讯（7 条）", "assets/rules/news.json", "已完成", "2026-09-10", "100%", ""),
    ("内置规则", "game 游戏（9 条）", "assets/rules/game.json", "已完成", "2026-09-10", "100%", ""),
    ("内置规则", "music 音乐（7 条）", "assets/rules/music.json", "已完成", "2026-09-10", "100%", ""),
    ("内置规则", "video 视频（6 条）", "assets/rules/video.json", "已完成", "2026-09-10", "100%", ""),
    ("内置规则", "reading 阅读（6 条）", "assets/rules/reading.json", "已完成", "2026-09-10", "100%", ""),
    ("内置规则", "browser 浏览器（5 条）", "assets/rules/browser.json", "已完成", "2026-09-10", "100%", ""),
    ("工程优化", "ANR 风险修复（runBlocking→异步）", "util/Logger.kt + ui/rule/RuleEditActivity.kt", "已完成", "2026-09-10", "100%", "Logger.init / RuleEditActivity"),
    ("工程优化", "package-by-feature 结构重构", "（全局 36 文件迁移）", "已完成", "2026-09-10", "100%", "清理 17 旧空目录"),
    ("工程优化", "代码质量优化（单例/扩展/缓存/keys）", "util/*.kt + ui/log/LogViewerScreen.kt", "已完成", "2026-09-10", "100%", "12 项问题全部解决"),
    ("版本管理", "推送 GitHub 远程仓库", "（git remote + push）", "已完成", "2026-09-10", "100%", "nillang/LTT_GKD"),
    # ---- 新增已完成（本次修复） ----
    ("编译修复", "GistClient JSONObject 导入+类型推断修复", "data/subscription/GistClient.kt", "已完成", "2026-09-11", "100%", "添加 org.json 导入+return@runCatching"),
    ("编译修复", "OcrManager takeScreenshot API 适配", "ocr/OcrManager.kt", "已完成", "2026-09-11", "100%", "3参数签名+HardwareBuffer→Bitmap"),
    ("编译修复", "SkipAccessibilityService smart cast 修复", "service/SkipAccessibilityService.kt", "已完成", "2026-09-11", "100%", "局部 val 消除可变属性限制"),
    ("安全", "Gist Token 加密存储（Android Keystore）", "util/TokenCipher.kt + data/prefs/SettingsStore.kt", "已完成", "2026-09-11", "100%", "AES-GCM，兼容旧明文降级"),
    ("构建", "R8/ProGuard 混淆与压缩", "app/build.gradle.kts + proguard-rules.pro", "已完成", "2026-09-11", "100%", "isMinifyEnabled=true + isShrinkResources"),
    ("规则编辑", "编辑器输入校验", "ui/rule/RuleEditScreen.kt", "已完成", "2026-09-11", "100%", "validateRule: ID/名称/包名/关键词"),
    # ---- 白名单功能 ----
    ("白名单", "WhitelistStore 数据层（DataStore 持久化 + StateFlow + 系统应用自动初始化）", "data/app/WhitelistStore.kt", "已完成", "2026-09-11", "100%", "O(1) HashSet 查询 + 订阅者同步"),
    ("白名单", "WindowEventProcessor 白名单前置检查", "accessibility/WindowEventProcessor.kt", "已完成", "2026-09-11", "100%", "processEvent 开头短路 return"),
    ("白名单", "App 全局注入 WhitelistStore 单例", "App.kt", "已完成", "2026-09-11", "100%", "UI 和 Service 共享同一实例"),
    ("白名单", "AppListScreen 白名单 UI（Switch + FilterChip 三选一）", "ui/app/AppListScreen.kt", "已完成", "2026-09-11", "100%", "全部/已白名单/待跳过 + 视觉区分 + 重置"),
    # ---- 待开发 ----
    ("测试", "单元测试（RuleEngine/RuleMatcher）", "（待建）test/", "待开发", "", "0%", "建议 JUnit"),
    ("测试", "Instrumented 测试（无障碍服务集成）", "（待建）androidTest/", "待开发", "", "0%", ""),
    ("规则订阅", "订阅失败重试与错误提示", "data/subscription/GistClient.kt", "待开发", "", "0%", "需添加重试策略"),
    ("构建", "CI 自动构建（GitHub Actions）", "（待建）.github/workflows/", "待开发", "", "0%", ""),
    ("功能", "规则导入/导出（JSON 文件）", "（待建）ui/rule/", "待开发", "", "0%", ""),
    ("功能", "规则云端共享社区", "（待建）data/subscription/", "待开发", "", "0%", "浏览/点赞/下载"),
    ("功能", "免更新规则热更新", "data/subscription/GistClient.kt", "待开发", "", "0%", "订阅增量同步"),
    ("体验", "跳过统计图表与历史趋势", "（待建）ui/main/", "待开发", "", "0%", ""),
    ("体验", "深色模式适配验证", "ui/**/*.kt", "待开发", "", "0%", "Material3 已支持，需验证"),
]

# 写入功能行（增加序号）
data_rows = []
for idx, f in enumerate(features, 1):
    data_rows.append((idx, f[0], f[1], f[2], f[3], f[4], f[5], f[6]))
write_rows(ws2, 3, data_rows, status_col=5)
for i in range(len(data_rows)):
    ws2.row_dimensions[3 + i].height = 28
set_widths(ws2, [6, 12, 38, 42, 10, 14, 8, 30])
ws2.freeze_panes = "A3"

# =========================================================
# Sheet 3: 开发步骤（按时间线）
# =========================================================
ws3 = wb.create_sheet("开发步骤")
style_title(ws3, 1, 5, "开发步骤时间线")
for c, h in enumerate(["阶段", "步骤", "实现目录", "状态", "产出/说明"], start=1):
    ws3.cell(row=2, column=c, value=h)
style_header(ws3, 2, 5)

steps = [
    ("一·基础搭建", "初始化 Android 项目（Kotlin+Compose+Material3）", "build.gradle.kts", "已完成", "项目骨架"),
    ("一·基础搭建", "定义 Rule/MatchTarget/MatchAction 数据模型", "data/rule/Rule.kt", "已完成", "Moshi @JsonClass"),
    ("一·基础搭建", "搭建 package-by-feature 目录结构", "ui/<feature> + data/<feature>", "已完成", "按功能模块组织"),
    ("二·核心引擎", "实现 RuleRepository 三源规则加载", "data/rule/RuleRepository.kt", "已完成", "local/subscribed/built-in"),
    ("二·核心引擎", "实现 RuleEngine 候选筛选+节流", "data/rule/RuleEngine.kt", "已完成", "throttleMap + priority"),
    ("二·核心引擎", "实现 RuleMatcher 节点匹配", "data/rule/RuleMatcher.kt", "已完成", "TEXT/ID/DESC + NodeUtils"),
    ("二·核心引擎", "实现 ActionExecutor + GestureSimulator", "action/ + gesture/", "已完成", "CLICK_NODE/COORD/BACK/GESTURE_TAP"),
    ("三·无障碍服务", "实现 SkipAccessibilityService", "service/SkipAccessibilityService.kt", "已完成", "事件分发→WindowEventProcessor"),
    ("三·无障碍服务", "实现 WindowEventProcessor 处理流程", "accessibility/WindowEventProcessor.kt", "已完成", "解析→筛选→匹配→执行→通知→OCR兜底"),
    ("三·无障碍服务", "实现 OcrManager（MLKit OCR 兜底）", "ocr/OcrManager.kt", "已完成", "截图识别+手势点击"),
    ("三·无障碍服务", "实现 SkipNotifier 跳过通知", "accessibility/SkipNotifier.kt", "已完成", "可配置开关"),
    ("四·UI 层", "MainActivity 主界面", "ui/main/", "已完成", "Compose + Flow"),
    ("四·UI 层", "RuleListActivity 规则列表", "ui/rule/", "已完成", "按来源分组"),
    ("四·UI 层", "RuleEditActivity 规则编辑", "ui/rule/", "已完成", "新增/修改/删除+输入校验"),
    ("四·UI 层", "AppListActivity 应用列表", "ui/app/", "已完成", "缓存+refresh"),
    ("四·UI 层", "LogViewerActivity 日志查看", "ui/log/", "已完成", "按日期分文件"),
    ("四·UI 层", "SettingsActivity 设置面板", "ui/settings/", "已完成", "OCR/日志/订阅/通知/Token"),
    ("五·规则订阅", "实现 GistClient（Gist 拉取/上传）", "data/subscription/GistClient.kt", "已完成", "HttpClientHolder 单例"),
    ("五·规则订阅", "实现 RuleSubscriptionService 前台保活", "service/RuleSubscriptionService.kt", "已完成", "specialUse FGS"),
    ("五·规则订阅", "实现 SettingsStore 偏好存储", "data/prefs/SettingsStore.kt", "已完成", "DataStore + Token 加密"),
    ("六·内置规则", "编写 general 通用兜底规则", "assets/rules/general.json", "已完成", "3 条"),
    ("六·内置规则", "编写 social/ecommerce/tools 等分类", "assets/rules/", "已完成", "基础分类"),
    ("六·内置规则", "扩充 music/video/reading/browser", "assets/rules/", "已完成", "4 新分类"),
    ("六·内置规则", "扩充 life/community/news/game/tools_extra", "assets/rules/", "已完成", "5 新分类"),
    ("七·工程优化", "修复 ANR（runBlocking→异步）", "util/Logger.kt + ui/rule/RuleEditActivity.kt", "已完成", "Logger.init / RuleEditActivity"),
    ("七·工程优化", "提取共享工具 launchSafe/HttpClientHolder/NodeUtils", "util/", "已完成", "防回收+连接池复用"),
    ("七·工程优化", "结构重构 MVC→package-by-feature", "（全局迁移）", "已完成", "36 文件迁移，17 空目录清理"),
    ("八·版本管理", "初始化 Git 并推送到 GitHub", "（git remote + push）", "已完成", "nillang/LTT_GKD"),
    ("九·改进项", "编译错误修复（3 文件）", "GistClient/OcrManager/SkipAccessibilityService", "已完成", "JSONObject+takeScreenshot+smart cast"),
    ("九·改进项", "规则编辑器输入校验", "ui/rule/RuleEditScreen.kt", "已完成", "validateRule 函数"),
    ("九·改进项", "R8/ProGuard 混淆与资源压缩", "build.gradle.kts + proguard-rules.pro", "已完成", "isMinifyEnabled=true"),
    ("九·改进项", "Token 加密存储（Android Keystore）", "util/TokenCipher.kt", "已完成", "AES-GCM 加解密"),
    ("十·测试与发布", "编写单元测试", "（待建）test/", "待开发", "RuleEngine/RuleMatcher"),
    ("十·测试与发布", "配置 CI 自动构建", "（待建）.github/workflows/", "待开发", "GitHub Actions"),
    ("十·测试与发布", "Release 签名打包发布", "（待建）", "待开发", ""),
]
write_rows(ws3, 3, steps, status_col=4)
for i in range(len(steps)):
    ws3.row_dimensions[3 + i].height = 26
set_widths(ws3, [16, 40, 42, 10, 36])
ws3.freeze_panes = "A3"

# =========================================================
# Sheet 4: 规则统计
# =========================================================
ws4 = wb.create_sheet("规则统计")
style_title(ws4, 1, 4, "内置规则统计")
for c, h in enumerate(["分类文件", "分类名称", "规则数", "覆盖应用数"], start=1):
    ws4.cell(row=2, column=c, value=h)
style_header(ws4, 2, 4)

rules = [
    ("general.json", "通用兜底", 3, "通用"),
    ("social.json", "社交", 9, "微信/QQ/微博等"),
    ("ecommerce.json", "电商", 7, "淘宝/京东/拼多多等"),
    ("tools.json", "工具", 4, "实用工具类"),
    ("tools_extra.json", "扩展工具", 8, "扩展工具类"),
    ("life.json", "生活", 7, "出行/外卖/支付等"),
    ("community.json", "社区", 6, "知乎/贴吧等"),
    ("news.json", "资讯", 7, "新闻类应用"),
    ("game.json", "游戏", 9, "游戏开屏/弹窗"),
    ("music.json", "音乐", 7, "音乐类应用"),
    ("video.json", "视频", 6, "视频类应用"),
    ("reading.json", "阅读", 6, "阅读类应用"),
    ("browser.json", "浏览器", 5, "主流浏览器"),
]
write_rows(ws4, 3, rules)
total_row = 3 + len(rules)
ws4.cell(row=total_row, column=1, value="合计").font = BOLD_FONT
ws4.cell(row=total_row, column=1).fill = PatternFill("solid", fgColor="D9E1F2")
ws4.cell(row=total_row, column=1).border = BORDER
ws4.cell(row=total_row, column=1).alignment = CENTER
ws4.cell(row=total_row, column=2, value="13 个分类").font = BOLD_FONT
ws4.cell(row=total_row, column=2).fill = PatternFill("solid", fgColor="D9E1F2")
ws4.cell(row=total_row, column=2).border = BORDER
ws4.cell(row=total_row, column=2).alignment = CENTER
ws4.cell(row=total_row, column=3, value=sum(r[2] for r in rules)).font = BOLD_FONT
ws4.cell(row=total_row, column=3).fill = PatternFill("solid", fgColor="D9E1F2")
ws4.cell(row=total_row, column=3).border = BORDER
ws4.cell(row=total_row, column=3).alignment = CENTER
ws4.cell(row=total_row, column=4, value="84 条规则").font = BOLD_FONT
ws4.cell(row=total_row, column=4).fill = PatternFill("solid", fgColor="D9E1F2")
ws4.cell(row=total_row, column=4).border = BORDER
ws4.cell(row=total_row, column=4).alignment = CENTER
set_widths(ws4, [18, 16, 10, 30])
ws4.freeze_panes = "A3"

# =========================================================
# Sheet 5: 源文件清单（更新）
# =========================================================
ws5 = wb.create_sheet("源文件清单")
style_title(ws5, 1, 3, "核心源文件清单")
for c, h in enumerate(["包/路径", "文件", "职责"], start=1):
    ws5.cell(row=2, column=c, value=h)
style_header(ws5, 2, 3)

files = [
    ("App", "App.kt", "Application 入口，初始化全局组件"),
    ("service", "SkipAccessibilityService.kt", "核心无障碍服务"),
    ("service", "RuleSubscriptionService.kt", "规则订阅前台服务"),
    ("accessibility", "WindowEventProcessor.kt", "窗口事件处理流程"),
    ("accessibility", "SkipNotifier.kt", "跳过成功通知"),
    ("action", "ActionExecutor.kt", "动作执行（点击/返回/手势）"),
    ("gesture", "GestureSimulator.kt", "手势模拟"),
    ("ocr", "OcrManager.kt", "MLKit OCR 兜底（takeScreenshot API 适配）"),
    ("data.rule", "Rule.kt", "规则数据模型"),
    ("data.rule", "RuleEngine.kt", "候选筛选+节流"),
    ("data.rule", "RuleMatcher.kt", "节点匹配"),
    ("data.rule", "RuleRepository.kt", "三源规则加载"),
    ("data.rule", "RuleTemplate.kt", "规则模板"),
    ("data.subscription", "GistClient.kt", "Gist 订阅客户端"),
    ("data.prefs", "SettingsStore.kt", "DataStore 偏好（Token 加密存储）"),
    ("data.app", "AppListRepository.kt / AppInfo.kt", "应用列表仓库"),
    ("ui.main", "MainActivity.kt / MainScreen.kt", "主界面"),
    ("ui.rule", "RuleListActivity.kt / RuleListScreen.kt", "规则列表"),
    ("ui.rule", "RuleEditActivity.kt / RuleEditScreen.kt", "规则编辑（含输入校验）"),
    ("ui.app", "AppListActivity.kt / AppListScreen.kt", "应用列表"),
    ("ui.log", "LogViewerActivity.kt / LogViewerScreen.kt", "日志查看"),
    ("ui.settings", "SettingsActivity.kt / SettingsScreen.kt", "设置"),
    ("util", "Logger.kt", "日志（按日期分文件）"),
    ("util", "NodeUtils.kt", "节点处理（防回收）"),
    ("util", "HttpClientHolder.kt", "OkHttp 单例复用"),
    ("util", "LaunchExt.kt", "launchSafe 协程扩展"),
    ("util", "PatternUtils.kt", "正则工具"),
    ("util", "DeviceIdProvider.kt", "设备 ID"),
    ("util", "GlobalMoshi.kt", "Moshi 单例"),
    ("util", "TokenCipher.kt", "Token 加密（Android Keystore AES-GCM）"),
]
write_rows(ws5, 3, files)
set_widths(ws5, [20, 42, 44])
ws5.freeze_panes = "A3"

wb.save(OUT)
print("已生成:", OUT)
