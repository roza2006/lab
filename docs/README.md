# 文档索引

本目录按软件工程过程组织：需求 → 设计 → 测试 → 过程 → 安全 → 报告。文件名用小写英文和短横线，正文用中文。

## 1. 目录与当前状态

| 目录/文件 | 内容 | 负责人 | 状态 |
|---|---|---|---|
| `requirements/requirements.md` | 项目范围、角色、约束、非功能需求 | 李梓语 | 待 R1 |
| `requirements/use-cases.md` | 29 个用例、优先级、权限边界 | 代香晰 | 已提交（R1，v1.0 待评审） |
| `design/domain-model.md` | 聚合根、实体、值对象、关系 | 郝佳鑫 | 已提交（R1，v0.1 待评审） |
| `design/architecture.md` | DDD 分层、模块化单体、架构决策 | 刘飞燕 | 待 R2 |
| `design/api-contract.md` | 认证/问诊/处方 API 契约 | 李梓语 | 待 R2 |
| `design/er.md` | ER 图与表关系 | 魏宇佳 | 待 R2（草案见 `database/er.md`） |
| `design/adr/` | 架构决策记录 | 刘飞燕 | 待 R2 |
| `test/test-plan.md` | 测试策略、环境、进入/退出准则 | 班国勇 | 待 R1 |
| `test/test-report.md` | 回归执行、缺陷与结论 | 刘飞燕 | 待 R6 |
| `test/acceptance.md` | 最终验收用例 | 郝佳鑫 | 待 R6 |
| `process/sprint-01.md` | 第 1 次迭代目标与完成情况 | 班国勇 | 待 R3 |
| `process/review-01.md` | 需求/设计评审记录 | 班国勇 | 待 R3 |
| `process/sprint-03.md` | 第 3 次迭代记录 | 班国勇 | 待 R5 |
| `process/release-notes.md` | 版本与演示说明 | 班国勇 | 待 R5 |
| `security/threat-model.md` | STRIDE、信任边界、威胁清单 | 班国勇 | 待 R4 |
| `security/checklist.md` | 隐私与安全检查表 | 班国勇 | 待 R4 |
| `report/` | 最终实验报告与过程证据包 | 魏宇佳 | 待 R6 |
| `demo/demo-script.md` | 答辩演示脚本 | 班国勇 | 待 R6 |
| `ai-usage.md` | AI 工具、范围、人工修改、验证 | 全员 | 持续更新 |

## 2. 文档之间的追踪

```text
requirements/use-cases.md
        ↓ 用例 ID
design/domain-model.md + design/api-contract.md
        ↓ 设计类 / 接口
database/schema.sql + database/migrations/
        ↓ 表 / 字段
test/test-plan.md + test/acceptance.md
        ↓ 测试用例
process/sprint-*.md + test/test-report.md
```

需求变更时，至少同步更新用例、设计类/接口、数据库字段、测试用例和 `process/` 下的变更记录。

## 3. 版本与命名

- 需求、设计文档按 `v0.x` 迭代，进入编码前冻结 `v1.0`；
- 数据库迁移用 `V001__init.sql`、`V002__core.sql` 顺序命名；
- 测试用例编号：`FT-xx` 功能、`ST-xx` 安全、`SM-xx` 状态机、`PT-xx` 性能；
- 每份文档在文首写版本、更新日期、负责人，便于评审追溯。

## 4. 数据库与部署文档（魏宇佳）

| 文件（仓库根目录相对） | 内容 | 状态 |
|---|---|---|
| `database/schema.sql` | 24 张表 DDL 草案：主外键、索引、CHECK 枚举、安全列 | R1 草案 v0.1，待评审 |
| `database/README.md` | 数据库目录说明、本地验证、与报告的差异 | R1 |
| `docs/database/table-list.md` | 24 张表清单与核心表字段要点 | R1 |
| `docs/database/dictionary.xlsx` | 数据字典：表清单、235 个字段、索引、外键 | R1 |
| `docs/database/er.md` | 表关系说明与核心 ER 图 | R1（R2 定稿并入 `design/er.md`） |
| `docs/database/diagrams/er-core.png` | ER 图，2600×1480，可直接插入报告 | R1 |
| `docs/database/diagrams/table-overview.png` | 24 张表按业务域分组的总览图 | R1 |
| `docs/database/diagrams/deployment.png` | 系统部署图（四区分区、主从、备份） | R1 |
| `docs/database/conventions.md` | 命名、类型、索引、外键、安全与迁移规范 | R1 |
| `deploy/README.md` | 部署结构、端口、环境变量、备份策略 | R1 |
| `scripts/check_schema.py` | 无数据库时的静态结构自检 | R1 |
| `scripts/render_er_diagram.py` | 生成 ER 图 | R1 |
| `scripts/render_table_overview.py` | 生成表总览图 | R1 |
| `scripts/render_deployment.py` | 生成部署图 | R1 |
| `scripts/build_dictionary.py` | 解析 DDL 生成数据字典 | R1 |
