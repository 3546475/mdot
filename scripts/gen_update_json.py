#!/usr/bin/env python3
"""生成 App 自更新清单 update.json（13 文档 B2-03 / B8-04）。

字段：versionName / versionCode / tag / releaseNotes / apkUrl / apkSha256。
- releaseNotes 取 CHANGELOG 段落（notes 文件缺失时回落一句话，不阻断发版）
- apkSha256 为三包实算哈希——App 端下载后强校验，阻断 MITM 换包/降级

CI 用法（工作目录 = 仓库根，APK 已重命名为 MDOT-RELEASE-<ver>-*.apk）：
    VER=0.6.13 VC=41 \
    APK_BASE_URL=https://github.com/u/r/releases/download/v0.6.13/MDOT-RELEASE-0.6.13 \
    NOTES_FILE=release_notes.md OUT_JSON=/tmp/update.json \
    python3 scripts/gen_update_json.py

单独调试可加 --check-sha-files 让它顺带校验构建产物哈希与传入值一致。
"""
import argparse
import hashlib
import json
import os
import sys

ABI_SUFFIX = {
    "arm64-v8a": "-v8a.apk",
    "armeabi-v7a": "-v7a.apk",
    "universal": "-dual.apk",
}


def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def build(ver: str, vc: int, base_url: str, notes: str, sha: dict) -> dict:
    base = base_url.rstrip("/")
    return {
        "versionName": ver,
        "versionCode": int(vc),
        "tag": f"v{ver}",
        "releaseNotes": notes,
        "apkUrl": {abi: f"{base}{suffix}" for abi, suffix in ABI_SUFFIX.items()},
        "apkSha256": {abi: sha[abi] for abi in ABI_SUFFIX},
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--ver", default=os.environ.get("VER"))
    ap.add_argument("--vc", default=os.environ.get("VC"))
    ap.add_argument("--base-url", default=os.environ.get("APK_BASE_URL"))
    ap.add_argument("--notes-file", default=os.environ.get("NOTES_FILE", ""))
    ap.add_argument("--out", default=os.environ.get("OUT_JSON", "update.json"))
    ap.add_argument(
        "--check-sha-files",
        action="store_true",
        help="顺带用改名后的 APK 文件实算哈希并与传入值比对（本地验证用）",
    )
    args = ap.parse_args()

    missing = [k for k in ("ver", "vc", "base_url") if not getattr(args, k)]
    if missing:
        print(f"缺少必填参数：{', '.join(missing)}", file=sys.stderr)
        return 2

    notes = ""
    if args.notes_file and os.path.exists(args.notes_file):
        with open(args.notes_file, encoding="utf-8") as f:
            notes = f.read().strip()
    if not notes:
        notes = f"马的加班 v{args.ver} 已发布。"

    sha = {
        "arm64-v8a": os.environ.get("SHA_V8A", ""),
        "armeabi-v7a": os.environ.get("SHA_V7A", ""),
        "universal": os.environ.get("SHA_DUAL", ""),
    }

    if args.check_sha_files:
        for abi, suffix in ABI_SUFFIX.items():
            path = f"MDOT-RELEASE-{args.ver}{suffix}"
            if not os.path.exists(path):
                print(f"注意：{path} 不存在，跳过该校验", file=sys.stderr)
                continue
            actual = sha256_file(path)
            if sha[abi] and actual != sha[abi]:
                print(f"哈希不符：{abi} 传入 {sha[abi]} 实算 {actual}", file=sys.stderr)
                return 3
            sha[abi] = sha[abi] or actual

    data = build(args.ver, args.vc, args.base_url, notes, sha)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(json.dumps(data, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
