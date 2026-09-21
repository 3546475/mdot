"""农历表算法校验：标准 lunarInfo(1900-2100) + 经典换算，先用已知权威日期验表再移植 Kotlin。

对照点全部来自国务院办公厅放假通知（春节/端午/中秋均为农历固定日）：
  2025-01-29 正月初一 · 2025-05-31 五月初五 · 2025-10-06 八月十五
  2026-02-17 正月初一 · 2026-06-19 五月初五 · 2026-09-25 八月十五
"""

LUNAR_INFO = [
    0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,  # 1900-1909
    0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,  # 1910-1919
    0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,  # 1920-1929
    0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,  # 1930-1939
    0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,  # 1940-1949
    0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0,  # 1950-1959
    0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,  # 1960-1969
    0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6,  # 1970-1979
    0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,  # 1980-1989
    0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0,  # 1990-1999
    0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,  # 2000-2009
    0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,  # 2010-2019
    0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,  # 2020-2029
    0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,  # 2030-2039
    0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,  # 2040-2049
    0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0,  # 2050-2059
    0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4,  # 2060-2069
    0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0,  # 2070-2079
    0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160,  # 2080-2089
    0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252,  # 2090-2099
    0x0d520,  # 2100
]

assert len(LUNAR_INFO) == 201, len(LUNAR_INFO)

DAY_NAMES = ["初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
             "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
             "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"]


def leap_month(y):
    return LUNAR_INFO[y - 1900] & 0xf


def leap_days(y):
    if leap_month(y) == 0:
        return 0
    return 30 if (LUNAR_INFO[y - 1900] & 0x10000) else 29


def month_days(y, m):
    return 30 if (LUNAR_INFO[y - 1900] & (0x10000 >> m)) else 29


def year_days(y):
    s = 348
    i = 0x8000
    while i > 0x8:
        if LUNAR_INFO[y - 1900] & i:
            s += 1
        i >>= 1
    return s + leap_days(y)


def solar_to_lunar(y, m, d):
    from datetime import date
    offset = (date(y, m, d) - date(1900, 1, 31)).days
    i, temp = 1900, 0
    while i < 2101 and offset > 0:
        temp = year_days(i)
        offset -= temp
        i += 1
    if offset < 0:
        offset += temp
        i -= 1
    ly = i
    leap = leap_month(ly)
    is_leap = False
    lm = 1
    while lm < 13 and offset > 0:
        if leap > 0 and lm == leap + 1 and not is_leap:
            lm -= 1
            is_leap = True
            temp = leap_days(ly)
        else:
            temp = month_days(ly, lm)
        if is_leap and lm == leap + 1:
            is_leap = False
        offset -= temp
        lm += 1
    if offset == 0 and leap > 0 and lm == leap + 1:
        if is_leap:
            is_leap = False
        else:
            is_leap = True
            lm -= 1
    if offset < 0:
        offset += temp
        lm -= 1
    return ly, lm, offset + 1, is_leap


CASES = [
    ((2025, 1, 29), (1, 1), "2025 春节"),
    ((2025, 5, 31), (5, 5), "2025 端午"),
    ((2025, 10, 6), (8, 15), "2025 中秋"),
    ((2026, 2, 17), (1, 1), "2026 春节"),
    ((2026, 2, 16), (12, 29), "2026 除夕(腊月小月)"),
    ((2026, 2, 15), (12, 28), "2026 腊月廿八"),
    ((2026, 6, 19), (5, 5), "2026 端午"),
    ((2026, 9, 25), (8, 15), "2026 中秋"),
    ((2026, 9, 26), (8, 16), "2026 八月十六"),
    ((2026, 1, 1), (11, 13), "2026 元旦"),
]

ok = True
for (gy, gm, gd), (em, ed), tag in CASES:
    ly, lm, ld, il = solar_to_lunar(gy, gm, gd)
    got = f"{'闰' if il else ''}{lm}月{DAY_NAMES[ld - 1]}"
    want = f"{'闰' if False else ''}{em}月{DAY_NAMES[ed - 1]}"
    hit = (lm, ld) == (em, ed)
    ok &= hit
    print(f"{'✓' if hit else '✗'} {gy}-{gm:02d}-{gd:02d}  得 {got}   期望 {want}   {tag}")

print()
print("2033 闰月：", leap_month(2033), "（应为 11，2033 年闰十一月）")
print("2025 闰月：", leap_month(2025), "（应为 6，2025 年闰六月）")
print("2026 闰月：", leap_month(2026), "（应为 0，无闰月）")
print()
print("连续无大年三十的年份（腊月 29 天 = 小月）：")
for y in range(2025, 2032):
    print(f"  {y} 腊月天数 = {month_days(y, 12)}")
print()
print("ALL PASS" if ok else "HAS FAILURE")
