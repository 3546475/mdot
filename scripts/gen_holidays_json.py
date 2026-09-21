#!/usr/bin/env python3
"""从 holiday-cn 生成 `app/src/main/assets/holidays.json`（schemaVersion 2）。

背景（调研文档《节假日与农历数据源调研》§2 / §5）：
    日历/结算需要的四类数据里，只有「国务院今年怎么调休」是不可预测、必须每年拉的，
    所以云端 JSON 只承载这一件事。本脚本把来源数据加工成 v2 契约，其中：

    - `type=legal`         法定节假日（13 天口径，见下 LEGAL）
    - `type=transfer_holiday` 调休凑出的连休日（含连休里的周末、被调成休息的工作日）
    - `type=workday_makeup` 调休补班日（周末上班）

    `statutory` 是「是不是法定节假日」，与「放不放假」是两件事——
    档位判定（法定 3 倍 / 休息日 2 倍 / 平时 1.5 倍）必须看 statutory，
    只看「当天放假」会把调休拼出来的连休日也算成法定 3 倍（调研文档 §2.4 坑三）。

用法：
    python scripts/gen_holidays_json.py                 # 生成内置资产
    python scripts/gen_holidays_json.py --years 2025 2026
    python scripts/gen_holidays_json.py --data-version official-2026-02
    python scripts/gen_holidays_json.py --print         # 只打印不写文件

数据来源：NateScarlet/holiday-cn（每日抓取国务院公告，含 papers 溯源字段）。
"""
from __future__ import annotations

import argparse
import json
import sys
import urllib.request
from datetime import date, datetime, timedelta, timezone

SOURCE_TMPL = "https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/{year}.json"
ASSET_PATH = "app/src/main/assets/holidays.json"
CST = timezone(timedelta(hours=8))

# 法定节假日（《全国年节及纪念日放假办法》第二条，2025-01-01 起施行：全体公民放假 13 天）。
# 春节含除夕、初一至初三 4 天；劳动节 2 天 —— 这两项是 2024 年第四次修订（国务院令第 795 号）新增的。
# 农历类节日（除夕/春节/端午/中秋）的日期逐年在国办通知里确定，故按年内置；公历类固定月日。
LEGAL: dict[int, dict[str, list[str]]] = {
    2025: {
        "元旦": ["01-01"],
        "春节": ["01-28", "01-29", "01-30", "01-31"],  # 除夕 ~ 初三
        "清明节": ["04-04"],
        "劳动节": ["05-01", "05-02"],
        "端午节": ["05-31"],
        "中秋节": ["10-06"],
        "国庆节": ["10-01", "10-02", "10-03"],
    },
    2026: {
        "元旦": ["01-01"],
        "春节": ["02-16", "02-17", "02-18", "02-19"],  # 除夕(十二月廿九) ~ 初三
        "清明节": ["04-05"],
        "劳动节": ["05-01", "05-02"],
        "端午节": ["06-19"],
        "中秋节": ["09-25"],
        "国庆节": ["10-01", "10-02", "10-03"],
    },
}

# 每个放假片段的节日名（holiday-cn 的 name 会把「国庆节、中秋节」这类合并在一起，
# 逐日角标需要一个确定的节日名，故按日指定；未列出的日子沿用片段节日名）。
DAY_NAME_OVERRIDE: dict[str, str] = {
    "2025-10-06": "中秋节",  # 2025 中秋与国庆连休：10/6 当天为中秋
}


def fetch_year(year: int) -> dict:
    url = SOURCE_TMPL.format(year=year)
    with urllib.request.urlopen(url, timeout=30) as resp:  # noqa: S310 - 固定 https 白名单源
        return json.loads(resp.read().decode("utf-8"))


def legal_map(year: int, days: list[dict]) -> dict[str, str]:
    """法定日 → 节日名；同时校验内置表与该年实际放假日历不冲突。"""
    table = LEGAL.get(year)
    if not table:
        raise SystemExit(f"LEGAL 未定义 {year} 年的法定日，请先补表（来源：国办当年通知）")
    out: dict[str, str] = {}
    off_dates = {d["date"] for d in days if d["isOffDay"]}
    for name, mmdd_list in table.items():
        for mmdd in mmdd_list:
            iso = f"{year}-{mmdd}"
            if iso not in off_dates:
                raise SystemExit(f"{iso}（{name}）在内置法定表里，但来源数据未标为放假日，请核对")
            out[iso] = name
    total = len(out)
    if total != 13:
        raise SystemExit(f"{year} 法定节假日应为 13 天，内置表算出 {total} 天")
    return out


def build_year(year: int, days: list[dict]) -> list[dict]:
    legal = legal_map(year, days)
    entries: list[dict] = []
    for day in days:
        iso = day["date"]
        # holiday-cn 对连休会把节日名合并（「国庆节、中秋节」），逐日角标只认一个节日名，取首个
        name = DAY_NAME_OVERRIDE.get(iso) or day["name"].split("、")[0]
        if day["isOffDay"]:
            if iso in legal:
                entries.append({
                    "date": iso, "type": "legal",
                    "holiday": legal[iso], "label": legal[iso], "statutory": True,
                })
            else:
                entries.append({
                    "date": iso, "type": "transfer_holiday",
                    "holiday": name, "label": name, "statutory": False,
                })
        else:
            entries.append({
                "date": iso, "type": "workday_makeup",
                "holiday": name, "label": "调休上班", "statutory": False,
            })
    entries.sort(key=lambda e: e["date"])
    return entries


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--years", type=int, nargs="+", default=sorted(LEGAL))
    ap.add_argument("--data-version", default="official-2026-02")
    ap.add_argument("--out", default=ASSET_PATH)
    ap.add_argument("--print", action="store_true", dest="dry_run")
    args = ap.parse_args()

    years: dict[str, list[dict]] = {}
    papers: list[dict] = []
    for year in args.years:
        raw = fetch_year(year)
        for paper in raw.get("papers", []):
            papers.append({"title": f"{year} 年部分节假日安排的通知", "url": paper})
        years[str(year)] = build_year(year, raw["days"])

    doc = {
        "schemaVersion": 2,
        "region": "CN",
        "dataVersion": args.data_version,
        "generatedAt": datetime.now(CST).replace(microsecond=0).isoformat(),
        "sources": papers,
        "years": years,
    }
    text = json.dumps(doc, ensure_ascii=False, indent=2) + "\n"
    if args.dry_run:
        print(text)
        return 0
    with open(args.out, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    counts = {y: len(v) for y, v in years.items()}
    print(f"wrote {args.out}: {counts} dataVersion={args.data_version}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
