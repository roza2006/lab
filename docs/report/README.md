# 实验报告与交付物索引

实验报告正文按学校模板单独提交（docx/pdf 不放进仓库），这里放它的支撑材料和提交物索引，
答辩时按这张表找文件。

## 1. 按报告章节找材料

| 报告章节 | 对应文件 |
|---|---|
| 一、项目开发计划 | [`COMMIT_PLAN.md`](../../COMMIT_PLAN.md)、[`docs/process/sprint-01.md`](../process/sprint-01.md) |
| 二、需求规格说明书 | [`docs/requirements/use-cases.md`](../requirements/use-cases.md)、[`requirements.md`](../requirements/requirements.md) |
| 三、设计规格说明书 | [`docs/design/architecture.md`](../design/architecture.md)、[`domain-model.md`](../design/domain-model.md)、[`api-contract.md`](../design/api-contract.md)、[`docs/database/er.md`](../database/er.md) |
| 四、核心代码设计方案 | [`docs/design/circulation-order.md`](../design/circulation-order.md)、[`circulation-redeem.md`](../design/circulation-redeem.md)、[`prescription-review.md`](../design/prescription-review.md)、[`consultation.md`](../design/consultation.md) |
| 五、测试报告 | [`docs/test/test-report.md`](../test/test-report.md)、[`acceptance.md`](../test/acceptance.md) |
| 六、实验总结 | [`docs/process/release-notes.md`](../process/release-notes.md)、本文档 |

## 2. 图表

| 图 | 文件 | 说明 |
|---|---|---|
| 核心 ER 图 | [`docs/database/diagrams/er-core.png`](../database/diagrams/er-core.png) | 25 张表，14 张主干表连线 |
| 表总览 | [`docs/database/diagrams/table-overview.png`](../database/diagrams/table-overview.png) | 按业务域分组 |
| 系统部署图 | [`docs/database/diagrams/deployment.png`](../database/diagrams/deployment.png) | 四区分区 |
| 局域网部署 | [`docs/database/diagrams/deployment-local.png`](../database/diagrams/deployment-local.png) | 多机演示接线 |
| 界面截图 | [`docs/demo/screenshots/`](../demo/screenshots/) | 12 张，覆盖四端主流程 |

图的源文件是同名 `.svg`，用 draw.io 或 Visio 打开可直接改。

## 3. 数据库

| 内容 | 文件 |
|---|---|
| 完整结构 | [`database/schema.sql`](../../database/schema.sql) |
| 迁移脚本 | [`database/migrations/`](../../database/migrations/)（V002 ~ V005） |
| 演示数据 | [`database/seed.sql`](../../database/seed.sql) |
| 数据字典 | [`docs/database/dictionary.xlsx`](../database/dictionary.xlsx) |
| 表清单 | [`docs/database/table-list.md`](../database/table-list.md) |
| 设计规范 | [`docs/database/conventions.md`](../database/conventions.md) |

## 4. 部署与演示

| 内容 | 文件 |
|---|---|
| 部署步骤、账号、排错 | [`docs/report/deployment.md`](./deployment.md) |
| 编排文件 | [`deploy/docker-compose.yml`](../../deploy/docker-compose.yml) |
| 环境变量示例 | [`deploy/env.example`](../../deploy/env.example) |
| 局域网多机演示 | [`docs/demo/local-network-test.md`](../demo/local-network-test.md) |
| 答辩演示脚本 | [`docs/demo/demo-script.md`](../demo/demo-script.md) |
| 界面走查 | [`docs/demo/ui-walkthrough.md`](../demo/ui-walkthrough.md) |

## 5. 过程与协作

| 内容 | 文件 |
|---|---|
| 提交计划（6 轮 × 6 人） | [`COMMIT_PLAN.md`](../../COMMIT_PLAN.md) |
| 迭代记录 | [`docs/process/sprint-01.md`](../process/sprint-01.md)、[`sprint-03.md`](../process/sprint-03.md) |
| 评审记录 | [`docs/process/review-01.md`](../process/review-01.md) |
| AI 使用记录 | [`docs/ai-usage.md`](../ai-usage.md) |
| 发布说明 | [`docs/process/release-notes.md`](../process/release-notes.md) |

## 6. 提交前自查

- [ ] 仓库里没有 `.env`、密钥、真实患者数据
- [ ] 图表与 `database/schema.sql` 一致（表数 25、字段名没改）
- [ ] 测试报告里的用例数与 `docs/test/` 下的文件对得上
- [ ] 演示账号只出现在种子数据和演示文档里
- [ ] 每个成员都有可追溯的提交（`git log --format='%an %s'` 可核对）
