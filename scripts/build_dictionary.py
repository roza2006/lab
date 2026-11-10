"""从 database/schema.sql 解析并生成数据字典（Excel）。

用法：python scripts/build_dictionary.py
输出：docs/database/dictionary.xlsx

解析内容：表清单、全部字段、索引/唯一键/主键、外键、CHECK 约束。
DDL 改动后重跑即可同步，避免文档与库不一致。
"""

from __future__ import annotations

import os
import re
from typing import Dict, List

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter

NAVY = "1F4E79"
LIGHT = "DEEAF6"
ZEBRA = "F5F9FD"
GOLD = "BF9000"
TEAL = "2F8F83"

CORE_TABLES = {
    "patient_profile", "doctor_profile", "pharmacist_profile",
    "consultation_session", "consultation_message", "medical_record",
    "prescription", "prescription_item", "drug", "pharmacy",
    "circulation_order", "dispense_record",
}

COLUMN_RE = re.compile(r"^(\w+)\s+([A-Za-z]+(?:\([^)]*\))?)\s*(.*)$", re.S)


def split_top_level(body: str) -> List[str]:
    parts, buf, depth = [], [], 0
    for ch in body:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append("".join(buf))
            buf = []
        else:
            buf.append(ch)
    if "".join(buf).strip():
        parts.append("".join(buf))
    return parts


def parse(schema_path: str) -> List[Dict]:
    text = open(schema_path, encoding="utf-8").read()
    text = "\n".join(l for l in text.splitlines() if not l.strip().startswith("--"))

    tables = []
    for m in re.finditer(
        r"CREATE TABLE (\w+) \((.*?)\n\) ENGINE[^;]*?COMMENT = '([^']*)'", text, re.S
    ):
        name, body, table_comment = m.group(1), m.group(2), m.group(3)
        cn = table_comment.split("：")[0].strip()
        cn = cn[:-1] if cn.endswith("表") else cn
        t = {
            "name": name,
            "cn": cn,
            "comment": table_comment,
            "columns": [],
            "pk": [],
            "uk": [],
            "idx": [],
            "fk": [],
            "check": [],
        }
        for raw in split_top_level(body):
            item = " ".join(raw.split())
            if not item:
                continue
            if item.startswith("PRIMARY KEY"):
                t["pk"] = re.findall(r"`?(\w+)`?", item.split("(", 1)[1])
                continue
            if item.startswith("UNIQUE KEY"):
                km = re.match(r"UNIQUE KEY (\w+) \((.*?)\)", item)
                if km:
                    t["uk"].append((km.group(1), re.sub(r"[`\s]", "", km.group(2))))
                continue
            if item.startswith("KEY "):
                km = re.match(r"KEY (\w+) \((.*?)\)", item)
                if km:
                    t["idx"].append((km.group(1), re.sub(r"[`\s]", "", km.group(2))))
                continue
            if item.startswith("CONSTRAINT"):
                fm = re.match(
                    r"CONSTRAINT (\w+) FOREIGN KEY \((\w+)\) REFERENCES (\w+) \((\w+)\)", item
                )
                if fm:
                    t["fk"].append(
                        {"name": fm.group(1), "col": fm.group(2),
                         "ref_table": fm.group(3), "ref_col": fm.group(4)}
                    )
                    continue
                cm = re.match(r"CONSTRAINT (\w+) CHECK \((.*)\)", item)
                if cm:
                    t["check"].append((cm.group(1), cm.group(2)))
                    continue
                continue

            col = COLUMN_RE.match(item)
            if not col:
                continue
            cname, ctype, rest = col.group(1), col.group(2), col.group(3)
            comment = ""
            cm = re.search(r"COMMENT '([^']*)'", rest)
            if cm:
                comment = cm.group(1)
            nullable = "否" if "NOT NULL" in rest else "是"
            default = ""
            dm = re.search(r"DEFAULT (.+?)(?:\s+ON UPDATE|\s+COMMENT|\s+AUTO_INCREMENT|$)", rest)
            if dm:
                default = dm.group(1).strip()
            t["columns"].append(
                {"name": cname, "type": ctype, "nullable": nullable,
                 "default": default, "comment": comment}
            )

        fk_cols = {f["col"] for f in t["fk"]}
        uk_cols = {c for _, cols in t["uk"] for c in cols.split(",")}
        for c in t["columns"]:
            keys = []
            if c["name"] in t["pk"]:
                keys.append("PK")
            if c["name"] in fk_cols:
                keys.append("FK")
            if c["name"] in uk_cols:
                keys.append("UK")
            c["keys"] = "、".join(keys)
        t["category"] = "核心" if name in CORE_TABLES else "支撑"
        tables.append(t)
    return tables


def style_sheet(ws, widths: List[int], freeze: str = "A2") -> None:
    thin = Side(style="thin", color="D6E2EE")
    border = Border(left=thin, right=thin, top=thin, bottom=thin)
    head_fill = PatternFill("solid", fgColor=NAVY)
    for i, w in enumerate(widths, start=1):
        ws.column_dimensions[get_column_letter(i)].width = w
    for cell in ws[1]:
        cell.fill = head_fill
        cell.font = Font(color="FFFFFF", bold=True, size=11)
        cell.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
        cell.border = border
    ws.row_dimensions[1].height = 30
    for r, row in enumerate(ws.iter_rows(min_row=2), start=2):
        for cell in row:
            cell.border = border
            cell.alignment = Alignment(vertical="center", wrap_text=True)
            if r % 2 == 0:
                cell.fill = PatternFill("solid", fgColor=ZEBRA)
    ws.freeze_panes = freeze
    ws.auto_filter.ref = ws.dimensions


def main() -> int:
    schema = os.path.join("database", "schema.sql")
    tables = parse(schema)
    out = os.path.join("docs", "database", "dictionary.xlsx")
    os.makedirs(os.path.dirname(out), exist_ok=True)

    n_cols = sum(len(t["columns"]) for t in tables)
    n_fk = sum(len(t["fk"]) for t in tables)
    n_uk = sum(len(t["uk"]) for t in tables)
    n_idx = sum(len(t["idx"]) for t in tables)
    n_ck = sum(len(t["check"]) for t in tables)

    wb = Workbook()

    # ---- 说明 ----
    ws = wb.active
    ws.title = "说明"
    rows = [
        ("数据字典", ""),
        ("来源", "database/schema.sql（R1 草案 v0.1）"),
        ("生成方式", "python scripts/build_dictionary.py（解析 DDL 自动生成）"),
        ("表数量", f"{len(tables)} 张：核心 {sum(1 for t in tables if t['category'] == '核心')} 张、"
                    f"支撑 {sum(1 for t in tables if t['category'] == '支撑')} 张"),
        ("字段总数", f"{n_cols} 个"),
        ("主键 / 唯一键", f"{len(tables)} 个主键、{n_uk} 个唯一键"),
        ("外键", f"{n_fk} 条"),
        ("普通索引", f"{n_idx} 个"),
        ("CHECK 约束", f"{n_ck} 条"),
        ("负责人", "魏宇佳"),
        ("更新日期", "2026-11-10"),
        ("说明", "本表由 DDL 解析生成，字段顺序与 schema.sql 一致；改动 DDL 后重跑脚本即可同步。"),
    ]
    for r, (k, v) in enumerate(rows, start=1):
        ws.cell(row=r, column=1, value=k).font = Font(bold=True, size=12 if r == 1 else 11)
        ws.cell(row=r, column=2, value=v).alignment = Alignment(wrap_text=True, vertical="center")
    ws.column_dimensions["A"].width = 18
    ws.column_dimensions["B"].width = 96
    ws["A1"].font = Font(bold=True, size=16, color=NAVY)

    # ---- 表清单 ----
    ws = wb.create_sheet("表清单")
    ws.append(["序号", "表名", "中文名", "分类", "字段数", "主键", "唯一键", "外键数", "索引数", "说明"])
    for i, t in enumerate(tables, start=1):
        ws.append([
            i, t["name"], t["cn"], t["category"], len(t["columns"]),
            "、".join(t["pk"]),
            "；".join(f"{name}({cols})" for name, cols in t["uk"]) or "-",
            len(t["fk"]), len(t["idx"]), t["comment"],
        ])
    style_sheet(ws, [6, 26, 18, 8, 8, 8, 40, 8, 8, 60])
    for row in ws.iter_rows(min_row=2, min_col=4, max_col=4):
        for cell in row:
            cell.fill = PatternFill("solid", fgColor=TEAL if cell.value == "核心" else GOLD)
            cell.font = Font(color="FFFFFF", bold=True)
            cell.alignment = Alignment(horizontal="center", vertical="center")

    # ---- 字段字典 ----
    ws = wb.create_sheet("字段字典")
    ws.append(["表名", "表中文名", "序号", "字段", "类型", "允许空", "默认值", "键", "说明"])
    for t in tables:
        for i, c in enumerate(t["columns"], start=1):
            ws.append([
                t["name"], t["cn"], i, c["name"], c["type"],
                c["nullable"], c["default"], c["keys"], c["comment"],
            ])
    style_sheet(ws, [26, 16, 6, 24, 18, 8, 22, 8, 52])

    # ---- 索引与约束 ----
    ws = wb.create_sheet("索引与约束")
    ws.append(["表名", "类型", "名称", "列 / 表达式", "备注"])
    for t in tables:
        if t["pk"]:
            ws.append([t["name"], "主键", f"pk_{t['name']}", "、".join(t["pk"]), "聚簇索引"])
        for name, cols in t["uk"]:
            ws.append([t["name"], "唯一键", name, cols, "业务唯一性由数据库保证"])
        for name, cols in t["idx"]:
            ws.append([t["name"], "普通索引", name, cols, "按查询条件组合"])
        for name, expr in t["check"]:
            ws.append([t["name"], "CHECK", name, expr, "枚举/范围校验"])
    style_sheet(ws, [26, 12, 28, 52, 32])

    # ---- 外键关系 ----
    ws = wb.create_sheet("外键关系")
    ws.append(["子表", "外键名", "列", "父表", "父列", "删除策略"])
    for t in tables:
        for f in t["fk"]:
            ws.append([t["name"], f["name"], f["col"], f["ref_table"], f["ref_col"], "RESTRICT（不级联删除）"])
    style_sheet(ws, [26, 26, 22, 26, 10, 26])

    wb.save(out)
    print(f"tables   : {len(tables)}")
    print(f"columns  : {n_cols}")
    print(f"fk/uk/idx/check : {n_fk}/{n_uk}/{n_idx}/{n_ck}")
    print(f"file     : {out} ({os.path.getsize(out)} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
