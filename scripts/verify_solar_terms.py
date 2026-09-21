"""节气算法校验（Meeus 太阳视黄经 + 牛顿迭代）。

对照点：2025 清明 4/4 · 2026 清明 4/5（国办通知里 2026-04-05 标为法定清明当天）
       2025 冬至 12/21 · 2026 秋分 9/23 · 2026 立春 2/4
"""

import math
from datetime import date, timedelta, datetime, timezone, timedelta as td

J2000 = 2451545.0
UNIX_EPOCH_JD = 2440587.5
TZ_CN = timezone(td(hours=8))

# 24 节气 → 太阳黄经（度），索引 0 = 立春
TERM_LON = [
    315, 330, 345, 0, 15, 30, 45, 60, 75, 90, 105, 120,
    135, 150, 165, 180, 195, 210, 225, 240, 255, 270, 285, 300,
]
TERM_NAMES = [
    "立春", "雨水", "惊蛰", "春分", "清明", "谷雨", "立夏", "小满", "芒种", "夏至", "小暑", "大暑",
    "立秋", "处暑", "白露", "秋分", "寒露", "霜降", "立冬", "小雪", "大雪", "冬至", "小寒", "大寒",
]


def jd_at_ut_midnight(y, m, d):
    """公历日期 → UT 0 时的儒略日"""
    a = (14 - m) // 12
    yy = y + 4800 - a
    mm = m + 12 * a - 3
    jdn = d + (153 * mm + 2) // 5 + 365 * yy + yy // 4 - yy // 100 + yy // 400 - 32045
    return jdn - 0.5


def apparent_solar_longitude(jd):
    """Meeus 低精度太阳视黄经（度，0..360）；精度 ~0.01°，对定日期绰绰有余"""
    t = (jd - J2000) / 36525.0
    l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
    m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t
    mr = math.radians(m)
    c = ((1.914602 - 0.004817 * t - 0.000014 * t * t) * math.sin(mr)
         + (0.019993 - 0.000101 * t) * math.sin(2 * mr)
         + 0.000289 * math.sin(3 * mr))
    omega = 125.04 - 1934.136 * t
    return (l0 + c - 0.00569 - 0.00478 * math.sin(math.radians(omega))) % 360.0


def delta_t_seconds(year):
    """ΔT 近似（TT - UT）；2020 年代约 69s，不修正会在交节贴近午夜时差一天"""
    if year >= 2005:
        t = year - 2000
        return 62.92 + 0.32217 * t + 0.005589 * t * t
    return 63.86 + 0.3345 * (year - 2000)


def term_datetime(year, index):
    """返回某年第 index 个节气的北京时间 datetime"""
    target = TERM_LON[index]
    # 初值：春分(0°)约在 3/20（年内第 79 天）
    guess_day = (79.0 + target / 0.9856473) % 365.2422
    jd = jd_at_ut_midnight(year, 1, 1) + guess_day
    for _ in range(8):
        diff = (target - apparent_solar_longitude(jd) + 180.0) % 360.0 - 180.0
        if abs(diff) < 1e-7:
            break
        jd += diff / 0.9856473
    # TT → UT
    jd -= delta_t_seconds(year) / 86400.0
    secs = (jd - UNIX_EPOCH_JD) * 86400.0
    return datetime.fromtimestamp(secs, tz=timezone.utc).astimezone(TZ_CN)


CASES = [
    ((2025, 4, 4), "清明"),
    ((2026, 4, 5), "清明"),
    ((2025, 12, 21), "冬至"),
    ((2026, 9, 23), "秋分"),
    ((2026, 2, 4), "立春"),
    ((2026, 6, 21), "夏至"),
]

ok = True
for (y, m, d), name in CASES:
    idx = TERM_NAMES.index(name)
    dt = term_datetime(y, idx)
    hit = (dt.date() == date(y, m, d))
    ok &= hit
    print(f"{'✓' if hit else '✗'} {name} {y}：算出 {dt:%Y-%m-%d %H:%M}(北京)  期望 {y}-{m:02d}-{d:02d}")

print()
print("2026 全年节气日期：")
for i, n in enumerate(TERM_NAMES):
    dt = term_datetime(2026, i)
    print(f"  {n} {dt:%m-%d %H:%M}", end="")
    if i % 3 == 2:
        print()
print()
print("ALL PASS" if ok else "HAS FAILURE")
