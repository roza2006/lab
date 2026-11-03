# 文档索引

本目录按软件工程过程组织：需求 → 设计 → 测试 → 过程 → 安全 → 报告。文件名使用小写英文与短横线，正文使用中文。

## 1. 目录与当前状态

| 目录/文件 | 内容 | 计划负责人 | 状态 |
|---|---|---|---|
| `requirements/requirements.md` | 项目范围、角色、约束、非功能需求 | B | 待 R1 |
| `requirements/use-cases.md` | 29 个用例、优先级、权限边界 | C | 已提交（R1-C，v1.0 待评审） |
| `design/domain-model.md` | 聚合根、实体、值对象、关系 | D | 已提交（R1-D，v0.1 待评审） |
| `design/architecture.md` | DDD 分层、模块化单体、架构决策 | A | 待 R2 |
| `design/api-contract.md` | 认证/问诊/处方 API 契约 | B | 待 R2 |
| `design/er.md` | ER 图与表关系 | E | 待 R2 |
| `design/adr/` | 架构决策记录 | A | 待 R2 |
| `test/test-plan.md` | 测试策略、环境、进入/退出准则 | F | 待 R1 |
| `test/test-report.md` | 回归执行、缺陷与结论 | A | 待 R6 |
| `test/acceptance.md` | 最终验收用例 | D | 待 R6 |
| `process/sprint-01.md` | 第 1 次迭代目标与完成情况 | F | 待 R3 |
| `process/review-01.md` | 需求/设计评审记录 | F | 待 R3 |
| `process/sprint-03.md` | 第 3 次迭代记录 | F | 待 R5 |
| `process/release-notes.md` | 版本与演示说明 | F | 待 R5 |
| `security/threat-model.md` | STRIDE、信任边界、威胁清单 | F | 待 R4 |
| `security/checklist.md` | 隐私与安全检查表 | F | 待 R4 |
| `report/` | 最终实验报告与过程证据包 | E | 待 R6 |
| `demo/demo-script.md` | 答辩演示脚本 | F | 待 R6 |
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

需求变更时，至少同步更新：用例、设计类/接口、数据库字段、测试用例、`process/` 中的变更记录。

## 3. 版本与命名

- 需求/设计文档按 `v0.x` 迭代，进入编码前冻结 `v1.0`；
- 数据库迁移使用 `V001__init.sql`、`V002__core.sql` 顺序命名；
- 测试用例编号：`FT-xx` 功能、`ST-xx` 安全、`SM-xx` 状态机、`PT-xx` 性能；
- 每份文档在文首写“版本 / 更新日期 / 负责人”，便于评审追溯。
