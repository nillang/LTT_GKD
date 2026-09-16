# 官方规则库（开发者维护）

这个目录存放**开发者（小狐官方）维护的规则 JSON**，作为 APP 内置的「官方规则库」默认订阅源的内容。

## 工作流

1. 在本目录放规则 JSON（RuleSet 格式，可多个文件，参考 `example_rules.json`）。
2. 定期更新规则后，运行上传脚本把内容推到线上：
   ```
   GITHUB_TOKEN=你的Token python upload.py
   ```
3. 脚本会创建/更新一个**公开 Gist**，并打印 Gist ID。
4. 把 Gist ID 填入
   `app/src/main/kotlin/com/ltt/gkd/data/subscription/OfficialSource.kt` 的 `ADDRESS`。
5. 用户安装 APP 后首次启动会自动添加并同步这个官方源。

## 线上保存方式（演进）

- **现在**：GitHub Gist（公开）。免费、多文件、订阅端匿名零配置。
- **未来**：开发者购买云服务器后，把规则 JSON 放到服务器静态目录，把 `ADDRESS` 改成 `http(s)://` 直链即可——订阅机制不变（`SubscriptionUrls` 自动识别 GIST / URL）。

## 注意

- `example_rules.json` 是示例，正式发布前请替换为真实规则，或直接删除。
- `.gist_id` 由脚本自动生成，不要手动修改；建议加入 `.gitignore`（避免把 Gist ID 当敏感信息外泄）。
- Token 只在你本地，绝不提交到仓库、绝不公开。
