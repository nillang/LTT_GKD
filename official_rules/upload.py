# -*- coding: utf-8 -*-
"""
官方规则库上传脚本：把 official_rules/ 目录下的所有 .json 规则文件上传到 GitHub Gist（公开）。

用法（Windows / Linux / macOS 通用，需 Python 3）：
    GITHUB_TOKEN=你的Token python upload.py

- 首次运行：创建一个公开 Gist（description="LTT_GKD official rules"），并把 Gist ID 保存到本目录 .gist_id。
- 之后运行：更新已有 Gist（PATCH，追加/覆盖同名文件）。
- 上传成功后打印 Gist ID 和链接，把 Gist ID 填入
  app/src/main/kotlin/com/ltt/gkd/data/subscription/OfficialSource.kt 的 ADDRESS。

依赖：仅用 Python 标准库（urllib），无需 pip 安装任何包。
Token 生成：GitHub → Settings → Developer settings → Personal access tokens(Tokens classic)
→ Generate new token(classic) → 只勾选 gist 权限。
"""
import json
import os
import sys
import urllib.request
import urllib.error

DIR = os.path.dirname(os.path.abspath(__file__))  # 脚本所在目录（official_rules/）
GIST_ID_FILE = os.path.join(DIR, ".gist_id")  # 保存 Gist ID 的文件
API = "https://api.github.com/gists"  # GitHub Gist API


def load_files():
    """读取目录下所有 .json 规则文件，构造 Gist files 载荷。"""
    files = {}
    for fn in sorted(os.listdir(DIR)):
        if fn.endswith(".json"):
            with open(os.path.join(DIR, fn), encoding="utf-8") as f:
                files[fn] = {"content": f.read()}
    return files


def api(method, url, token, data=None):
    """调用 GitHub API，返回解析后的 JSON。"""
    req = urllib.request.Request(url, method=method)
    req.add_header("Authorization", "token " + token)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("User-Agent", "ltt-gkd-official-upload")
    body = None
    if data is not None:
        body = json.dumps(data).encode("utf-8")
        req.add_header("Content-Type", "application/json")
    resp = urllib.request.urlopen(req, data=body)
    return json.loads(resp.read().decode("utf-8"))


def main():
    token = os.environ.get("GITHUB_TOKEN") or (sys.argv[1] if len(sys.argv) > 1 else "")
    if not token:
        print("请先提供 GitHub Token：")
        print("  Windows: set GITHUB_TOKEN=你的Token && python upload.py")
        print("  Linux/macOS: GITHUB_TOKEN=你的Token python upload.py")
        sys.exit(1)

    files = load_files()
    if not files:
        print("official_rules/ 目录下没有 .json 规则文件")
        sys.exit(1)

    gist_id = ""
    if os.path.exists(GIST_ID_FILE):
        gist_id = open(GIST_ID_FILE, encoding="utf-8").read().strip()

    try:
        if gist_id:
            r = api("PATCH", "%s/%s" % (API, gist_id), token, {"files": files})
            print("已更新现有 Gist")
        else:
            r = api("POST", API, token, {
                "description": "LTT_GKD official rules",
                "public": True,  # 公开：订阅端匿名拉取
                "files": files,
            })
            print("已创建公开 Gist")
    except urllib.error.HTTPError as e:
        print("上传失败 HTTP %s: %s" % (e.code, e.read().decode("utf-8")[:500]))
        sys.exit(1)

    gist_id = r["id"]
    with open(GIST_ID_FILE, "w", encoding="utf-8") as f:
        f.write(gist_id)

    print("Gist ID: %s" % gist_id)
    print("Gist 链接: https://gist.github.com/%s/%s" % (r["owner"]["login"], gist_id))
    print("-> 把上面的 Gist ID 填入 OfficialSource.kt 的 ADDRESS 字段")


if __name__ == "__main__":
    main()
