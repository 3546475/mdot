#!/usr/bin/env python3
"""把 master 的 README（GitHub 版）改写成 CNB 版。

公开仓有两份，README 不能一份通用：

- ``origin/main``（GitHub）→ README 保持 GitHub 链接；
- ``cnb/main``（CNB）→ 面向国内用户，链接应指向 ``cnb.cool``，连 GitHub API 徽章
  （Release 版本号，打的是 GitHub 的接口）也不该再出现。

发版同步到 CNB 仓时跑一次本脚本即可（见 AGENTS.md「发布 / 开源仓库」发版步骤 ③）。

用法::

    python scripts/gen_readme_cnb.py README.md              # 打印改写结果（stdout）
    python scripts/gen_readme_cnb.py --inplace README.md    # 原地改写
    python scripts/gen_readme_cnb.py --check README.md       # 校验「已是 CNB 版」，不是则退出码 1

⚠️ 规则是**单向**的（GitHub → CNB），所以重复跑幂等；`--check` 判据 =
「文件里不再出现 ``github.com/3546475``」。
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

GITHUB_OWNER_REPO = "https://github.com/3546475/mdot"
CNB_OWNER_REPO = "https://cnb.cool/nulxiel/mdot"

# (模式, 说明) —— 顺序敏感：先特例后兜底
RULES: list[tuple[str, str, str]] = [
    (
        "- 在 [Releases]({gh}/releases) 页面下载最新 APK（国内可在 [CNB 镜像]({cnb}/-/releases) 下载，"
        "应用内检查更新默认走该镜像）。".format(gh=GITHUB_OWNER_REPO, cnb=CNB_OWNER_REPO),
        "- 在 [Releases]({cnb}/-/releases) 页面下载最新 APK（应用内检查更新默认走该镜像）。".format(
            cnb=CNB_OWNER_REPO
        ),
        "下载安装那条整句换掉（否则会变成「在 Releases 下载（国内可在 CNB 镜像下载）」的废话）",
    ),
    (
        "[![Release](https://img.shields.io/github/v/release/3546475/mdot.svg)]({gh}/releases)".format(
            gh=GITHUB_OWNER_REPO
        ),
        "[![Release](https://img.shields.io/badge/Release-CNB%20%E9%95%9C%E5%83%8F-blue.svg)]({cnb}/-/releases)".format(
            cnb=CNB_OWNER_REPO
        ),
        "Release 徽章：shields 的 GitHub API 徽章在 CNB 上没意义，换成指向 CNB releases 的静态徽章",
    ),
    (
        "{gh}/blob/main/".format(gh=GITHUB_OWNER_REPO),
        "{cnb}/-/git/raw/main/".format(cnb=CNB_OWNER_REPO),
        "仓库内文件链接（LICENSE / NOTICE）→ CNB raw",
    ),
    (
        "{gh}/releases".format(gh=GITHUB_OWNER_REPO),
        "{cnb}/-/releases".format(cnb=CNB_OWNER_REPO),
        "Releases 页 → CNB releases 页",
    ),
    (
        GITHUB_OWNER_REPO,
        CNB_OWNER_REPO,
        "仓库首页（兜底）",
    ),
]


def convert(text: str) -> str:
    for src, dst, _why in RULES:
        text = text.replace(src, dst)
    return text


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="README 的 CNB 版改写")
    parser.add_argument("path", help="README 路径")
    parser.add_argument("--inplace", action="store_true", help="原地改写（默认打印到 stdout）")
    parser.add_argument("--check", action="store_true", help="校验是否已是 CNB 版")
    args = parser.parse_args(argv)

    path = Path(args.path)
    text = path.read_text(encoding="utf-8")

    if args.check:
        if "github.com/3546475" in text:
            print(
                "✗ {} 还含 GitHub 链接，不是 CNB 版；先跑 --inplace".format(path),
                file=sys.stderr,
            )
            return 1
        print("✓ {} 已是 CNB 版".format(path))
        return 0

    out = convert(text)
    if args.inplace:
        path.write_text(out, encoding="utf-8", newline="")
        print("→ {} 已改写成 CNB 版".format(path))
    else:
        sys.stdout.write(out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
