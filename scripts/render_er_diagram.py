"""生成核心 ER 图（PNG + SVG）。

数据来源：database/schema.sql 与 docs/database/er.md
配色：报告约定 深蓝 #1F4E79 / 中蓝 #2E75B6 / 浅蓝 #DEEAF6 / 青绿 #2F8F83 / 金色 #BF9000

用法：
    python scripts/render_er_diagram.py
输出：
    docs/database/diagrams/er-core.png
    docs/database/diagrams/er-core.svg
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from typing import List, Sequence, Tuple

from PIL import Image, ImageDraw, ImageFont

W, H = 2600, 1480
FONT_REG = r"C:\Windows\Fonts\msyh.ttc"
FONT_BOLD = r"C:\Windows\Fonts\msyhbd.ttc"

NAVY = "#1F4E79"
BLUE = "#2E75B6"
LIGHT = "#DEEAF6"
TEAL = "#2F8F83"
GOLD = "#BF9000"
GRAY = "#5B6B7A"
BORDER = "#C4D6E7"
ROWL = "#EAF1F8"
TEXT = "#1F2D3D"
MUTED = "#7A8B9A"
BG = "#FFFFFF"
SHADOW = "#E8EEF5"

BADGE = {"PK": GOLD, "FK": TEAL, "UK": BLUE}

# 6 列 3 行网格
COL_X = [100, 520, 940, 1360, 1780, 2200]
ROW_Y = [180, 480, 780]
CW, CH = 300, 230


@dataclass
class Card:
    cn: str
    en: str
    col: int
    row: int
    color: str
    fields: List[Tuple[str, str, str]]

    @property
    def x(self) -> int:
        return COL_X[self.col]

    @property
    def y(self) -> int:
        return ROW_Y[self.row]

    @property
    def right(self) -> int:
        return self.x + CW

    @property
    def bottom(self) -> int:
        return self.y + CH

    @property
    def cx(self) -> int:
        return self.x + CW // 2

    @property
    def cy(self) -> int:
        return self.y + CH // 2


class Canvas:
    def __init__(self, width: int, height: int):
        self.img = Image.new("RGB", (width, height), BG)
        self.draw = ImageDraw.Draw(self.img)
        self.svg: List[str] = []
        self.warnings: List[str] = []
        self._fonts: dict = {}

    def font(self, size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
        key = (size, bold)
        if key not in self._fonts:
            self._fonts[key] = ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)
        return self._fonts[key]

    def text(self, x, y, s, size, color=TEXT, bold=False, anchor="ls") -> None:
        self.draw.text((x, y), s, font=self.font(size, bold), fill=color, anchor=anchor)
        ta = {"ls": "start", "ms": "middle", "rs": "end"}[anchor]
        weight = "bold" if bold else "normal"
        self.svg.append(
            f'<text x="{x}" y="{y}" font-family="Microsoft YaHei,Segoe UI,sans-serif" '
            f'font-size="{size}" font-weight="{weight}" fill="{color}" '
            f'text-anchor="{ta}">{_esc(s)}</text>'
        )

    def text_width(self, s: str, size: int, bold: bool = False) -> float:
        return self.draw.textlength(s, font=self.font(size, bold))

    def rect(self, x, y, w, h, fill="none", outline=None, width=2, radius=10) -> None:
        self.draw.rounded_rectangle(
            (x, y, x + w, y + h), radius=radius, fill=fill, outline=outline, width=width
        )
        stroke = f' stroke="{outline}" stroke-width="{width}"' if outline else ""
        fill_attr = fill if fill != "none" else "none"
        self.svg.append(
            f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" ry="{radius}" '
            f'fill="{fill_attr}"{stroke}/>'
        )

    def line(self, pts: Sequence[Tuple[float, float]], color=BLUE, width=2) -> None:
        self.draw.line(list(pts), fill=color, width=width, joint="curve")
        coords = " ".join(f"{x},{y}" for x, y in pts)
        self.svg.append(
            f'<polyline points="{coords}" fill="none" stroke="{color}" '
            f'stroke-width="{width}" stroke-linejoin="round"/>'
        )

    def circle(self, cx, cy, r, fill, outline, width=2) -> None:
        self.draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=fill, outline=outline, width=width)
        self.svg.append(
            f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{fill}" stroke="{outline}" stroke-width="{width}"/>'
        )


def _esc(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def draw_card(cv: Canvas, c: Card) -> None:
    cv.rect(c.x + 5, c.y + 6, CW, CH, fill=SHADOW, outline=None, width=0, radius=12)
    cv.rect(c.x, c.y, CW, CH, fill=BG, outline=BORDER, width=2, radius=12)

    cv.rect(c.x, c.y, CW, 56, fill=c.color, outline=None, width=0, radius=12)
    cv.rect(c.x, c.y + 40, CW, 16, fill=c.color, outline=None, width=0, radius=0)
    cv.text(c.x + 16, c.y + 31, c.cn, 21, "#FFFFFF", bold=True)
    cv.text(c.x + 16, c.y + 49, c.en, 13, "#DCE9F5")

    row_h = 32
    y0 = c.y + 60
    for i, (name, typ, key) in enumerate(c.fields):
        ry = y0 + i * row_h
        if i % 2 == 1:
            cv.rect(c.x + 2, ry, CW - 4, row_h, fill="#F7FAFD", outline=None, width=0, radius=0)
        if i:
            cv.line([(c.x + 10, ry), (c.right - 10, ry)], ROWL, 1)
        cv.text(c.x + 16, ry + 21, name, 14, TEXT)

        type_right = c.right - 14
        if key:
            bw, bh = 34, 18
            bx = c.right - 14 - bw
            by = ry + 7
            cv.rect(bx, by, bw, bh, fill=BADGE[key], outline=None, width=0, radius=4)
            cv.text(bx + bw / 2, by + 14, key, 11, "#FFFFFF", bold=True, anchor="ms")
            type_right = bx - 10

        if typ:
            cv.text(type_right, ry + 21, typ, 13, MUTED, anchor="rs")

        name_w = cv.text_width(name, 14)
        type_w = cv.text_width(typ, 13) if typ else 0
        if name_w + type_w + (44 if key else 10) > CW - 20:
            cv.warnings.append(f"{c.en}.{name}: 行内容偏宽")


def _unit(p, q):
    dx, dy = q[0] - p[0], q[1] - p[1]
    n = (dx * dx + dy * dy) ** 0.5 or 1.0
    return dx / n, dy / n


def end_mark(cv: Canvas, p, dx, dy, card: str, color: str) -> None:
    """在 p 处画基数符号；(dx,dy) 指向实体内部。"""
    if card == "N":
        cx, cy = p[0] - 13 * dx, p[1] - 13 * dy
        ox, oy = -dy, dx
        cv.line([(cx, cy), (p[0] + 7 * ox, p[1] + 7 * oy)], color, 2)
        cv.line([(cx, cy), (p[0], p[1])], color, 2)
        cv.line([(cx, cy), (p[0] - 7 * ox, p[1] - 7 * oy)], color, 2)
    elif card == "1":
        cx, cy = p[0] - 13 * dx, p[1] - 13 * dy
        ox, oy = -dy, dx
        cv.line([(cx + 9 * ox, cy + 9 * oy), (cx - 9 * ox, cy - 9 * oy)], color, 3)
    elif card == "0..1":
        ox, oy = -dy, dx
        cv.circle(p[0] - 11 * dx, p[1] - 11 * dy, 5, BG, color, 2)
        cx, cy = p[0] - 27 * dx, p[1] - 27 * dy
        cv.line([(cx + 9 * ox, cy + 9 * oy), (cx - 9 * ox, cy - 9 * oy)], color, 3)


def relation(cv: Canvas, a, b, a_card: str, b_card: str) -> None:
    cv.line([a, b], BLUE, 3)
    ux, uy = _unit(a, b)
    end_mark(cv, a, -ux, -uy, a_card, BLUE)
    end_mark(cv, b, ux, uy, b_card, BLUE)


def build() -> Canvas:
    cv = Canvas(W, H)

    cv.text(100, 78, "智慧医疗在线问诊与处方流转平台 · 核心 ER 图", 46, NAVY, bold=True)
    cv.text(
        100, 118,
        "第 1 轮 E｜数据库设计基线 v0.1｜14 张核心表 + 9 张支撑表｜完整 24 表字段见 database/schema.sql",
        18, MUTED,
    )
    cv.line([(100, 138), (2500, 138)], LIGHT, 2)

    cards: List[Card] = [
        Card("用户账号", "sys_user", 0, 0, NAVY, [
            ("id", "BIGINT", "PK"), ("username", "VARCHAR(64)", "UK"),
            ("phone_hash", "CHAR(64)", "UK"), ("role_code", "VARCHAR(32)", ""),
            ("status", "TINYINT", ""),
        ]),
        Card("患者档案", "patient_profile", 1, 0, NAVY, [
            ("id", "BIGINT", "PK"), ("user_id", "BIGINT", "FK"),
            ("id_card_hash", "CHAR(64)", "UK"), ("allergy_history", "VARCHAR(1000)", ""),
            ("consent_status", "TINYINT", ""),
        ]),
        Card("问诊会话", "consultation_session", 2, 0, TEAL, [
            ("id", "BIGINT", "PK"), ("patient_id", "BIGINT", "FK"),
            ("doctor_id", "BIGINT", "FK"), ("status", "VARCHAR(24)", ""),
            ("version", "INT", ""),
        ]),
        Card("电子处方", "prescription", 3, 0, GOLD, [
            ("id", "BIGINT", "PK"), ("rx_no", "VARCHAR(32)", "UK"),
            ("session_id", "BIGINT", "FK"), ("status", "VARCHAR(32)", ""),
            ("version", "INT", ""),
        ]),
        Card("处方流转单", "circulation_order", 4, 0, BLUE, [
            ("id", "BIGINT", "PK"), ("rx_id", "BIGINT", "FK"),
            ("pharmacy_id", "BIGINT", "FK"), ("status", "VARCHAR(24)", ""),
            ("idempotency_key", "VARCHAR(64)", "UK"),
        ]),
        Card("发药核销", "dispense_record", 5, 0, BLUE, [
            ("id", "BIGINT", "PK"), ("circulation_id", "BIGINT", "FK"),
            ("operator_id", "BIGINT", "FK"), ("batch_no", "VARCHAR(64)", ""),
            ("redeemed_at", "DATETIME(3)", ""),
        ]),

        Card("医生档案", "doctor_profile", 0, 1, NAVY, [
            ("id", "BIGINT", "PK"), ("user_id", "BIGINT", "FK"),
            ("department_id", "BIGINT", "FK"), ("license_no", "VARCHAR(64)", "UK"),
            ("rx_right", "TINYINT", ""),
        ]),
        Card("问诊消息", "consultation_message", 1, 1, TEAL, [
            ("id", "BIGINT", "PK"), ("session_id", "BIGINT", "FK"),
            ("sender_id", "BIGINT", ""), ("client_msg_id", "VARCHAR(64)", "UK"),
            ("sent_at", "DATETIME(3)", ""),
        ]),
        Card("电子病历", "medical_record", 2, 1, TEAL, [
            ("id", "BIGINT", "PK"), ("session_id", "BIGINT", "FK"),
            ("diagnosis", "VARCHAR(1000)", ""), ("signed_at", "DATETIME(3)", ""),
            ("signed_hash", "CHAR(64)", ""),
        ]),
        Card("处方明细", "prescription_item", 3, 1, GOLD, [
            ("id", "BIGINT", "PK"), ("prescription_id", "BIGINT", "FK"),
            ("drug_id", "BIGINT", "FK"), ("dosage", "DECIMAL(10,3)", ""),
            ("days", "INT", ""),
        ]),
        Card("合作药房", "pharmacy", 4, 1, BLUE, [
            ("id", "BIGINT", "PK"), ("pharmacy_code", "VARCHAR(32)", "UK"),
            ("license_no", "VARCHAR(64)", "UK"), ("license_expire_at", "DATE", ""),
            ("status", "TINYINT", ""),
        ]),

        Card("科室", "department", 0, 2, NAVY, [
            ("id", "BIGINT", "PK"), ("dept_code", "VARCHAR(32)", "UK"),
            ("dept_name", "VARCHAR(64)", ""), ("parent_id", "BIGINT", "FK"),
            ("status", "TINYINT", ""),
        ]),
        Card("药品分类", "drug_category", 1, 2, GOLD, [
            ("id", "BIGINT", "PK"), ("category_code", "VARCHAR(32)", "UK"),
            ("category_name", "VARCHAR(64)", ""), ("parent_id", "BIGINT", "FK"),
            ("status", "TINYINT", ""),
        ]),
        Card("药品目录", "drug", 2, 2, GOLD, [
            ("id", "BIGINT", "PK"), ("drug_code", "VARCHAR(64)", "UK"),
            ("category_id", "BIGINT", "FK"), ("controlled_level", "VARCHAR(16)", ""),
            ("status", "TINYINT", ""),
        ]),
        Card("药师档案", "pharmacist_profile", 3, 2, BLUE, [
            ("id", "BIGINT", "PK"), ("user_id", "BIGINT", "FK"),
            ("pharmacy_id", "BIGINT", "FK"), ("license_no", "VARCHAR(64)", "UK"),
            ("review_right", "TINYINT", ""),
        ]),
    ]

    idx = {c.en: c for c in cards}

    def rel(a_en, a_side, a_at, b_en, b_side, b_at, ca, cb):
        def point(card: Card, side: str, at: float):
            if side == "l":
                return (card.x, round(card.y + CH * at))
            if side == "r":
                return (card.right, round(card.y + CH * at))
            if side == "t":
                return (round(card.x + CW * at), card.y)
            return (round(card.x + CW * at), card.bottom)

        relation(cv, point(idx[a_en], a_side, a_at), point(idx[b_en], b_side, b_at), ca, cb)

    # 身份与档案
    rel("sys_user", "r", 0.42, "patient_profile", "l", 0.42, "1", "0..1")
    rel("sys_user", "b", 0.50, "doctor_profile", "t", 0.50, "1", "0..1")
    rel("department", "t", 0.50, "doctor_profile", "b", 0.50, "1", "N")
    # 问诊
    rel("patient_profile", "r", 0.42, "consultation_session", "l", 0.42, "1", "N")
    rel("doctor_profile", "t", 0.70, "consultation_session", "b", 0.15, "1", "N")
    rel("consultation_session", "b", 0.50, "consultation_message", "t", 0.62, "1", "N")
    rel("consultation_session", "b", 0.80, "medical_record", "t", 0.55, "1", "0..1")
    # 处方
    rel("consultation_session", "r", 0.42, "prescription", "l", 0.42, "1", "N")
    rel("prescription", "b", 0.50, "prescription_item", "t", 0.50, "1", "N")
    rel("prescription_item", "l", 0.55, "drug", "r", 0.30, "N", "1")
    rel("drug", "l", 0.62, "drug_category", "r", 0.62, "N", "1")
    # 流转
    rel("prescription", "r", 0.42, "circulation_order", "l", 0.42, "1", "0..1")
    rel("pharmacy", "t", 0.50, "circulation_order", "b", 0.50, "1", "N")
    rel("circulation_order", "r", 0.42, "dispense_record", "l", 0.42, "1", "0..1")
    rel("pharmacy", "l", 0.55, "pharmacist_profile", "r", 0.40, "1", "N")

    for c in cards:
        draw_card(cv, c)

    # 支撑表
    support = [
        ("sys_role", "角色"), ("sys_permission", "权限点"), ("sys_user_role", "用户角色"),
        ("sys_role_permission", "角色权限"), ("audit_log", "审计日志"),
        ("consent_record", "知情同意"), ("notification", "通知"),
        ("outbox_event", "事务发件箱"), ("idempotency_record", "幂等记录"),
    ]
    cv.text(100, 1082, "支撑表（9 张 · 逻辑关联，不建物理外键；字段见 schema.sql）", 20, GRAY, bold=True)

    def small_card(x, y, en, cn):
        w, h = 300, 86
        cv.rect(x, y, w, h, fill="#FAFCFE", outline=BORDER, width=2, radius=10)
        cv.rect(x, y, 6, h, fill=GRAY, outline=None, width=0, radius=3)
        cv.text(x + 20, y + 36, cn, 18, TEXT, bold=True)
        cv.text(x + 20, y + 62, en, 13, MUTED)

    for i, (en, cn) in enumerate(support[:5]):
        small_card(430 + i * 360, 1104, en, cn)
    for i, (en, cn) in enumerate(support[5:]):
        small_card(610 + i * 360, 1216, en, cn)

    # 图例
    lx, ly = 100, 1352
    cv.line([(lx, ly), (lx + 60, ly)], BLUE, 3)
    cv.text(lx + 68, ly + 6, "1 : N 一对多", 16, TEXT)
    cv.line([(lx + 230, ly), (lx + 290, ly)], BLUE, 3)
    cv.text(lx + 298, ly + 6, "1 : 0..1 一对零或一", 16, TEXT)

    bx = lx + 600
    for key, label in (("PK", "主键"), ("FK", "外键"), ("UK", "唯一键")):
        cv.rect(bx, ly - 14, 34, 18, fill=BADGE[key], outline=None, width=0, radius=4)
        cv.text(bx + 17, ly, key, 11, "#FFFFFF", bold=True, anchor="ms")
        cv.text(bx + 42, ly + 6, label, 16, TEXT)
        bx += 130

    cv.text(
        100, 1396,
        "线端标注：竖线「│」＝ 1，分叉「<」＝ 多，圆圈「○」＝ 可为空（0..1）；卡片内 PK/FK/UK 为键类型。",
        15, MUTED,
    )
    cv.text(
        100, 1426,
        "说明：患者/医生/药师档案经 user_id 关联 sys_user；prescription.reviewer_id 关联药师档案。"
        "为保持图面清晰，这两条关系以文字说明代替连线。",
        15, MUTED,
    )
    cv.text(2500, 1426, "R1-E · v0.1", 15, MUTED, anchor="rs")

    return cv


def main() -> int:
    out_dir = os.path.join("docs", "database", "diagrams")
    os.makedirs(out_dir, exist_ok=True)

    cv = build()

    png_path = os.path.join(out_dir, "er-core.png")
    cv.img.save(png_path, "PNG", optimize=True)

    svg_path = os.path.join(out_dir, "er-core.svg")
    body = "\n  ".join(cv.svg)
    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">\n'
        f"  <title>智慧医疗在线问诊与处方流转平台 · 核心 ER 图</title>\n"
        f"  <desc>R1-E 数据库设计：14 张核心表与 9 张支撑表，含主外键与基数标注。</desc>\n"
        f'  <rect width="{W}" height="{H}" fill="{BG}"/>\n'
        f"  {body}\n</svg>\n"
    )
    with open(svg_path, "w", encoding="utf-8") as fh:
        fh.write(svg)

    print(f"png : {png_path} ({os.path.getsize(png_path)} bytes)")
    print(f"svg : {svg_path} ({os.path.getsize(svg_path)} bytes)")
    if cv.warnings:
        print("warn:")
        for w in cv.warnings:
            print(f"  - {w}")
    else:
        print("warn: none")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
