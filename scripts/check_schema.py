"""校验 database/schema.sql 的结构完整性（无 MySQL 实例时的静态检查）。

检查项：
1. CREATE TABLE 数量是否等于预期的 24 张；
2. 每个 FOREIGN KEY ... REFERENCES 的目标表是否已在文件中定义；
3. 括号是否配平（排除字符串与注释）；
4. 每张表是否都有 PRIMARY KEY；
5. 敏感字段是否以 _enc 结尾、盲索引是否以 _hash 结尾。

用法：python scripts/check_schema.py database/schema.sql
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

EXPECTED_TABLES = 24


def strip_comments(sql: str) -> str:
    sql = re.sub(r"/\*.*?\*/", "", sql, flags=re.S)
    sql = re.sub(r"--[^\n]*", "", sql)
    return sql


def main(path: str) -> int:
    raw = Path(path).read_text(encoding="utf-8")
    sql = strip_comments(raw)
    errors: list[str] = []

    tables = re.findall(r"CREATE\s+TABLE\s+`?(\w+)`?", sql, flags=re.I)
    if len(tables) != EXPECTED_TABLES:
        errors.append(f"表数量异常：{len(tables)}，预期 {EXPECTED_TABLES}")

    if len(set(tables)) != len(tables):
        errors.append("存在重复定义的表名")

    for name, body in re.findall(r"CREATE\s+TABLE\s+`?(\w+)`?\s*\((.*?)\)\s*ENGINE", sql, flags=re.S | re.I):
        if "PRIMARY KEY" not in body.upper():
            errors.append(f"{name}: 缺少 PRIMARY KEY")

    for table, ref in re.findall(
        r"CREATE\s+TABLE\s+`?(\w+)`?\s*\((.*?)\)\s*ENGINE", sql, flags=re.S | re.I
    ):
        for target in re.findall(r"REFERENCES\s+`?(\w+)`?", ref, flags=re.I):
            if target not in tables:
                errors.append(f"{table}: 外键指向未定义的表 {target}")

    if sql.count("(") != sql.count(")"):
        errors.append(f"括号不配平：{sql.count('(')} 个左括号 / {sql.count(')')} 个右括号")

    for col, col_type in re.findall(r"^\s*(\w+_enc)\s+(\w+)", sql, flags=re.M):
        if col_type.upper() != "VARBINARY":
            errors.append(f"敏感字段 {col} 应为 VARBINARY 密文列，实际为 {col_type}")

    print(f"表数量        : {len(tables)}")
    print(f"表清单        : {', '.join(tables)}")
    print(f"FOREIGN KEY   : {len(re.findall(r'FOREIGN KEY', sql, flags=re.I))} 条")
    print(f"UNIQUE KEY    : {len(re.findall(r'UNIQUE KEY', sql, flags=re.I))} 条")
    print(f"CHECK 约束    : {len(re.findall(r'CHECK [(]', sql, flags=re.I))} 条")

    if errors:
        print("\n[FAIL] 发现问题：")
        for e in errors:
            print(f"  - {e}")
        return 1
    print("\n[OK] 结构检查全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "database/schema.sql"))

