# AI 使用记录

> 课程允许 AI 辅助。本文件记录“用了什么、改了什么、怎么验证”，不是隐藏痕迹，而是证明团队对 AI 输出负责。

| 日期 | 成员 | 工具/模型 | 使用范围 | AI 输出 | 人工修改 | 验证方式 |
|---|---|---|---|---|---|---|
| 2026-11-03 | 魏宇佳 | DeepSeek Harness（deepseek 模型） | 起草 `database/schema.sql`：依据报告表 3-6 ~ 3-13、表 2-10、图 3-5 生成 24 张表 DDL，并整理 `docs/database/` 下的表清单、ER、规范 | 24 表 DDL 初稿、表关系说明、命名与安全规范、静态自检脚本 | 待 E 复核：补充字段是否采纳、审计/风控表是否新增、敏感列加密方案落地方式 | MySQL 临时实例导入通过（24 表）；CHECK/唯一键/外键拦截验证；`scripts/check_schema.py` 全绿；与报告表 3-7 ~ 3-13 逐条对照 |
| 2026-11-03 | 郝佳鑫 | DeepSeek Harness（deepseek 模型） | 起草 `docs/design/domain-model.md`：按报告表 2-9/2-10、表 3-3/3-6 提炼聚合、实体、值对象与不变量 | 文档结构、聚合清单、Mermaid 类图与状态图、错误码映射表初稿 | 待郝佳鑫复核：核准聚合边界（病历是否独立）、错误码命名、开放问题结论 | 与实验报告 §3.4、§2.3、表 2-10 逐条对照；PR 内逐节 review |
| 2026-11-03 | 代香晰 | DeepSeek Harness（deepseek 模型） | 起草 `docs/requirements/use-cases.md`：按报告表 2-4～2-7、表 2-8 权限矩阵与《代码实现方案》§6.2 梳理 29 个用例 | 用例总览、分角色场景表、5 个关键用例规格、权限矩阵与越权场景初稿 | 待 C 复核：优先级、合作药房用例边界、8 个待确认问题的结论 | 与实验报告 §3.2/§3.3 逐表对照；与郝佳鑫的 `domain-model.md` §12.1 追踪列核对 |
| 2026-11-03 | 班国勇 | DeepSeek | 起草测试策略文档、Issue 模板、PR 模板 | 初稿 | 人工审核、补充修改与格式调整 | PR review 并合并至 main 分支 |
| 2026-11-09 | 郝佳鑫 | DeepSeek Harness（deepseek 模型） | 搭建后端工程骨架：`backend/pom.xml`、`PlatformApplication`、`application.yml`，并核对依赖坐标与配置项 | Spring Boot 3.2.5 + MyBatis-Plus 依赖清单、启动类、数据源/Redis/日志基础配置初稿 | 待郝佳鑫复核：依赖取舍（Flyway/Redis/Actuator 是否在第 2 轮引入）、包结构、配置命名 | 父 POM 与 16 个依赖坐标在 Maven Central 校验存在；JDK 17.0.11 + Maven 3.9.6 实机 `mvn -B -DskipTests package` BUILD SUCCESS（产物 `backend/target/medical-platform-backend.jar`）；`application.yml` 通过 YAML 解析校验 |
|  |  |  |  |  |  |  |

## 记录要求

- 不粘贴完整密钥、患者数据、老师未公开的题目材料。
- 写清楚“AI 生成后，人改了什么”，例如：重命名、补异常、改事务边界、补测试。
- 每轮至少 1 条记录；答辩时能指出对应 commit。
