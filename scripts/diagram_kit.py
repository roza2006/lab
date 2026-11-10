"""绘图工具箱：PIL 画 PNG + 同步产出 SVG，供各图脚本复用。

约定坐标系与字号，保证所有图风格一致（配色取自报告规范）。
"""

from __future__ import annotations

import math
from typing import List, Sequence, Tuple

from PIL import Image, ImageDraw, ImageFont

FONT_REG = r"C:\Windows\Fonts\msyh.ttc"
FONT_BOLD = r"C:\Windows\Fonts\msyhbd.ttc"

# 报告配色
NAVY = "#1F4E79"
BLUE = "#2E75B6"
LIGHT = "#DEEAF6"
TEAL = "#2F8F83"
GOLD = "#BF9000"
RED = "#B02418"
GRAY = "#5B6B7A"
BORDER = "#C4D6E7"
ROWL = "#EAF1F8"
TEXT = "#1F2D3D"
MUTED = "#7A8B9A"
BG = "#FFFFFF"
PANEL = "#F7FAFD"
SHADOW = "#E8EEF5"

BADGE = {"PK": GOLD, "FK": TEAL, "UK": BLUE}


def esc(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


class Canvas:
    def __init__(self, width: int, height: int):
        self.w = width
        self.h = height
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
            f'text-anchor="{ta}">{esc(s)}</text>'
        )

    def text_width(self, s: str, size: int, bold: bool = False) -> float:
        return self.draw.textlength(s, font=self.font(size, bold))

    def rect(self, x, y, w, h, fill="none", outline=None, width=2, radius=10) -> None:
        if radius <= 0:
            self.draw.rectangle((x, y, x + w, y + h), fill=fill, outline=outline, width=width)
            stroke = f' stroke="{outline}" stroke-width="{width}"' if outline else ""
            self.svg.append(
                f'<rect x="{x}" y="{y}" width="{w}" height="{h}" '
                f'fill="{fill if fill != "none" else "none"}"{stroke}/>'
            )
            return
        self.draw.rounded_rectangle(
            (x, y, x + w, y + h), radius=radius, fill=fill, outline=outline, width=width
        )
        stroke = f' stroke="{outline}" stroke-width="{width}"' if outline else ""
        self.svg.append(
            f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" ry="{radius}" '
            f'fill="{fill if fill != "none" else "none"}"{stroke}/>'
        )

    def line(self, pts: Sequence[Tuple[float, float]], color=BLUE, width=2, dash=False) -> None:
        self.draw.line(list(pts), fill=color, width=width, joint="curve")
        coords = " ".join(f"{x},{y}" for x, y in pts)
        d = ' stroke-dasharray="10 6"' if dash else ""
        self.svg.append(
            f'<polyline points="{coords}" fill="none" stroke="{color}" '
            f'stroke-width="{width}" stroke-linejoin="round"{d}/>'
        )

    def circle(self, cx, cy, r, fill, outline, width=2) -> None:
        self.draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=fill, outline=outline, width=width)
        self.svg.append(
            f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{fill}" stroke="{outline}" stroke-width="{width}"/>'
        )

    def arrow(self, pts: Sequence[Tuple[float, float]], color=BLUE, width=3, head=13) -> None:
        """折线箭头：最后一段带箭头。"""
        self.line(pts, color, width)
        (x1, y1), (x2, y2) = pts[-2], pts[-1]
        ang = math.atan2(y2 - y1, x2 - x1)
        left = (x2 - head * math.cos(ang - 0.42), y2 - head * math.sin(ang - 0.42))
        right = (x2 - head * math.cos(ang + 0.42), y2 - head * math.sin(ang + 0.42))
        self.draw.polygon([(x2, y2), left, right], fill=color)
        self.svg.append(
            f'<polygon points="{x2},{y2} {left[0]:.1f},{left[1]:.1f} {right[0]:.1f},{right[1]:.1f}" '
            f'fill="{color}"/>'
        )

    def label(self, x, y, s, color=MUTED, size=14, anchor="ms") -> None:
        """关系/连线说明文字，带白色描边以便压在线上。"""
        f = self.font(size)
        self.draw.text((x, y), s, font=f, fill=color, anchor=anchor, stroke_width=3, stroke_fill=BG)
        ta = {"ls": "start", "ms": "middle", "rs": "end"}[anchor]
        self.svg.append(
            f'<text x="{x}" y="{y}" font-family="Microsoft YaHei,Segoe UI,sans-serif" '
            f'font-size="{size}" fill="{color}" text-anchor="{ta}" '
            f'stroke="{BG}" stroke-width="4" paint-order="stroke">{esc(s)}</text>'
        )

    def save_svg(self, path: str, title: str, desc: str) -> None:
        body = "\n  ".join(self.svg)
        svg = (
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.w}" height="{self.h}" '
            f'viewBox="0 0 {self.w} {self.h}">\n'
            f"  <title>{esc(title)}</title>\n  <desc>{esc(desc)}</desc>\n"
            f'  <rect width="{self.w}" height="{self.h}" fill="{BG}"/>\n'
            f"  {body}\n</svg>\n"
        )
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(svg)

    def save_png(self, path: str) -> None:
        self.img.save(path, "PNG", optimize=True)


def title_block(cv: Canvas, title: str, subtitle: str, right: str = "", y: int = 78) -> None:
    cv.text(100, y, title, 46, NAVY, bold=True)
    cv.text(100, y + 40, subtitle, 18, MUTED)
    cv.line([(100, y + 60), (cv.w - 100, y + 60)], LIGHT, 2)
    if right:
        cv.text(cv.w - 100, y + 40, right, 16, MUTED, anchor="rs")


def node(
    cv: Canvas,
    x: int,
    y: int,
    w: int,
    h: int,
    name: str,
    detail: str = "",
    color: str = NAVY,
    tag: str = "",
) -> None:
    """标准部署节点：左侧色条 + 名称 + 说明。"""
    cv.rect(x + 4, y + 5, w, h, fill=SHADOW, outline=None, width=0, radius=10)
    cv.rect(x, y, w, h, fill=BG, outline=BORDER, width=2, radius=10)
    cv.rect(x, y, 8, h, fill=color, outline=None, width=0, radius=4)
    cv.text(x + 24, y + 38, name, 21, TEXT, bold=True)
    if detail:
        cv.text(x + 24, y + 66, detail, 15, MUTED)
    if tag:
        tw = cv.text_width(tag, 13, True) + 22
        cv.rect(x + w - tw - 14, y + 16, tw, 26, fill=color, outline=None, width=0, radius=13)
        cv.text(x + w - 14 - tw / 2, y + 34, tag, 13, "#FFFFFF", bold=True, anchor="ms")


def zone(cv: Canvas, x: int, y: int, w: int, h: int, label: str, color: str, sub: str = "") -> None:
    cv.rect(x, y, w, h, fill=PANEL, outline=BORDER, width=2, radius=16)
    cv.rect(x, y, w, 52, fill=color, outline=None, width=0, radius=16)
    cv.rect(x, y + 36, w, 16, fill=color, outline=None, width=0, radius=0)
    cv.text(x + 22, y + 35, label, 21, "#FFFFFF", bold=True)
    if sub:
        cv.text(x + w - 20, y + 35, sub, 14, "#DCE9F5", anchor="rs")


def chip(cv: Canvas, x: int, y: int, w: int, h: int, cn: str, en: str, color: str) -> None:
    cv.rect(x, y, w, h, fill=BG, outline=BORDER, width=2, radius=8)
    cv.rect(x, y, 6, h, fill=color, outline=None, width=0, radius=3)
    cv.text(x + 18, y + 26, cn, 18, TEXT, bold=True)
    cv.text(x + 18, y + 50, en, 13, MUTED)
