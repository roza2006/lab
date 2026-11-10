"""生成 24 张表总览图（PNG + SVG）。

用法：python scripts/render_table_overview.py
输出：docs/database/diagrams/table-overview.png / .svg
"""

from __future__ import annotations

import os
from math import ceil
from typing import List, Tuple

from diagram_kit import (
    BG, BLUE, BORDER, GOLD, GRAY, LIGHT, MUTED, NAVY, TEAL, TEXT,
    Canvas, title_block,
)

W, H = 2600, 1000

Domains: List[Tuple[str, str, List[Tuple[str, str]]]] = [
    ("身份与权限", NAVY, [
        ("用户账号", "sys_user"), ("角色", "sys_role"), ("权限点", "sys_permission"),
        ("用户角色", "sys_user_role"), ("角色权限", "sys_role_permission"),
    ]),
    ("档案与主数据", TEAL, [
        ("患者档案", "patient_profile"), ("医生档案", "doctor_profile"),
        ("药师档案", "pharmacist_profile"), ("科室", "department"), ("药品分类", "drug_category"),
    ]),
    ("问诊", BLUE, [
        ("问诊会话", "consultation_session"), ("问诊消息", "consultation_message"),
        ("电子病历", "medical_record"),
    ]),
    ("药品与药房", GOLD, [
        ("药品目录", "drug"), ("合作药房", "pharmacy"),
    ]),
    ("处方与流转", GOLD, [
        ("电子处方", "prescription"), ("处方明细", "prescription_item"),
        ("处方流转单", "circulation_order"), ("发药核销", "dispense_record"),
    ]),
    ("支撑与治理", GRAY, [
        ("审计日志", "audit_log"), ("知情同意", "consent_record"), ("通知", "notification"),
        ("事务发件箱", "outbox_event"), ("幂等记录", "idempotency_record"),
    ]),
]


def panel(cv: Canvas, x: int, y: int, w: int, title: str, color: str,
          items: List[Tuple[str, str]], cols: int = 2, min_rows: int = 0) -> int:
    chip_w, chip_h, gap = (w - 32 - (cols - 1) * 16) // cols, 62, 16
    rows = max(ceil(len(items) / cols), min_rows)
    h = 54 + rows * (chip_h + 14) + 8

    cv.rect(x, y, w, h, fill="#FAFCFE", outline=BORDER, width=2, radius=14)
    cv.rect(x, y, w, 54, fill=color, outline=None, width=0, radius=14)
    cv.rect(x, y + 40, w, 14, fill=color, outline=None, width=0, radius=0)
    cv.text(x + 20, y + 36, title, 21, "#FFFFFF", bold=True)
    cv.text(x + w - 20, y + 36, f"{len(items)} 张", 15, "#E3EEF8", anchor="rs")

    for i, (cn, en) in enumerate(items):
        r, c = divmod(i, cols)
        cx = x + 16 + c * (chip_w + 16)
        cy = y + 68 + r * (chip_h + 14)
        cv.rect(cx, cy, chip_w, chip_h, fill=BG, outline=BORDER, width=1, radius=8)
        cv.rect(cx, cy, 6, chip_h, fill=color, outline=None, width=0, radius=3)
        cv.text(cx + 18, cy + 26, cn, 18, TEXT, bold=True)
        cv.text(cx + 18, cy + 48, en, 13, MUTED)
    return h


def build() -> Canvas:
    cv = Canvas(W, H)
    title_block(
        cv,
        "数据库表总览（24 张）",
        "按业务域分组：核心表 12 张、支撑表 12 张。字段、索引与约束见 database/schema.sql",
    )

    # 第一行
    y1 = 200
    panel(cv, 100, y1, 580, Domains[0][0], Domains[0][1], Domains[0][2], cols=2, min_rows=3)
    panel(cv, 720, y1, 580, Domains[1][0], Domains[1][1], Domains[1][2], cols=2, min_rows=3)
    panel(cv, 1340, y1, 560, Domains[2][0], Domains[2][1], Domains[2][2], cols=2, min_rows=3)
    panel(cv, 1940, y1, 560, Domains[3][0], Domains[3][1], Domains[3][2], cols=2, min_rows=3)

    # 第二行
    y2 = 520
    panel(cv, 100, y2, 780, Domains[4][0], Domains[4][1], Domains[4][2], cols=2, min_rows=3)
    panel(cv, 920, y2, 780, Domains[5][0], Domains[5][1], Domains[5][2], cols=2, min_rows=3)

    # 统计卡
    x, y, w, h = 1740, y2, 760, 290
    cv.rect(x, y, w, h, fill="#F4F8FC", outline=BORDER, width=2, radius=14)
    cv.text(x + 24, y + 44, "规模与约束统计", 21, NAVY, bold=True)
    stats = [
        ("24", "张表", "核心 12 + 支撑 12"),
        ("29", "条外键", "核心表建物理外键"),
        ("27", "个唯一键", "业务唯一性由库保证"),
        ("30", "条 CHECK", "状态列枚举约束"),
    ]
    for i, (num, unit, note) in enumerate(stats):
        r, c = divmod(i, 2)
        sx = x + 24 + c * 360
        sy = y + 90 + r * 120
        cv.text(sx, sy + 34, num, 40, GOLD, bold=True)
        cv.text(sx + 76, sy + 30, unit, 18, TEXT, bold=True)
        cv.text(sx + 76, sy + 56, note, 14, MUTED)

    cv.text(
        100, 900,
        "统一约定：主键 BIGINT 自增；时间 DATETIME(3)；状态 VARCHAR + CHECK 枚举；"
        "敏感字段 _enc 密文 + _hash 盲索引；审计/事件/幂等表不建外键。",
        16, MUTED,
    )
    cv.text(2500, 900, "R1 · v0.1", 15, MUTED, anchor="rs")

    return cv


def main() -> int:
    out_dir = os.path.join("docs", "database", "diagrams")
    os.makedirs(out_dir, exist_ok=True)
    cv = build()
    cv.save_png(os.path.join(out_dir, "table-overview.png"))
    cv.save_svg(
        os.path.join(out_dir, "table-overview.svg"),
        "数据库表总览（24 张）",
        "按业务域分组的 24 张表清单，含规模与约束统计。",
    )
    print("png :", os.path.getsize(os.path.join(out_dir, "table-overview.png")), "bytes")
    print("svg :", os.path.getsize(os.path.join(out_dir, "table-overview.svg")), "bytes")
    print("warn:", cv.warnings or "none")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
