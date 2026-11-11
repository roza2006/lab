# AI 使用记录

> 课程允许 AI 辅助。本文件记录“用了什么、改了什么、怎么验证”，不是隐藏痕迹，而是证明团队对 AI 输出负责。

| 日期 | 成员 | 工具/模型 | 使用范围 | AI 输出 | 人工修改 | 验证方式 |
|---|---|---|---|---|---|---|
| 2026-11-03 | 魏宇佳 | DeepSeek Harness（deepseek 模型） | 起草 `database/schema.sql`：依据报告表 3-6 ~ 3-13、表 2-10、图 3-5 生成 24 张表 DDL，并整理 `docs/database/` 下的表清单、ER、规范 | 24 表 DDL 初稿、表关系说明、命名与安全规范、静态自检脚本 | 核对补充字段是否采纳（审核人、过期时间、盲索引）、审计类表是否建外键、敏感列加密方式 | MySQL 临时实例导入 24 张表；CHECK、唯一键、外键按预期拦截；`check_schema.py` 通过；对照报告表 3-7 ~ 3-13 |
| 2026-11-10 | 魏宇佳 | DeepSeek Harness（deepseek 模型） | 生成 ER 图、表总览图、系统部署图与数据字典：Pillow 画图，openpyxl 从 DDL 解析出 Excel | 绘图公共模块、三张图的布局、Excel 解析脚本 | 调整分栏与连线走线、补图例说明、核对 24 张表与 235 个字段、重写部署分区描述 | 图上表名与 `schema.sql` 一致；SVG 通过 XML 解析；Excel 字段数与 DDL 一致 |
| 2026-11-11 | 魏宇佳 | DeepSeek Harness（deepseek 模型） | 写 `V002__core.sql`、`V003__auth_consult.sql`：把 24 张表落成 Flyway 脚本，再按问诊代码的 Mapper SQL 调整会话/消息表并补索引 | 两份迁移脚本、索引清单、`schema.sql` 同步改动 | 对照 `Consultation*Mapper` 核对查询条件与排序，去掉与旧索引重复的项；把科室外键换成字符串字段；补 Flyway 路径配置 | 临时 MySQL 实例按 V002→V003 顺序执行成功；与 `schema.sql` 单独导入的结果逐列逐索引比对一致（237 列 / 133 索引行 / 28 外键）；插入会话与消息验证默认值与外键约束 |
| 2026-11-03 | 郝佳鑫 | DeepSeek Harness（deepseek 模型） | 起草 `docs/design/domain-model.md`：按报告表 2-9/2-10、表 3-3/3-6 提炼聚合、实体、值对象与不变量 | 文档结构、聚合清单、Mermaid 类图与状态图、错误码映射表初稿 | 待郝佳鑫复核：核准聚合边界（病历是否独立）、错误码命名、开放问题结论 | 与实验报告 §3.4、§2.3、表 2-10 逐条对照；PR 内逐节 review |
| 2026-11-03 | 代香晰 | DeepSeek Harness（deepseek 模型） | 起草 `docs/requirements/use-cases.md`：按报告表 2-4～2-7、表 2-8 权限矩阵与《代码实现方案》§6.2 梳理 29 个用例 | 用例总览、分角色场景表、5 个关键用例规格、权限矩阵与越权场景初稿 | 待 C 复核：优先级、合作药房用例边界、8 个待确认问题的结论 | 与实验报告 §3.2/§3.3 逐表对照；与郝佳鑫的 `domain-model.md` §12.1 追踪列核对 |
| 2026-11-03 | 班国勇 | DeepSeek | 起草测试策略文档、Issue 模板、PR 模板 | 初稿 | 人工审核、补充修改与格式调整 | PR review 并合并至 main 分支 |
| 2026-11-09 | 郝佳鑫 | DeepSeek Harness（deepseek 模型） | 搭建后端工程骨架：`backend/pom.xml`、`PlatformApplication`、`application.yml`，并核对依赖坐标与配置项 | Spring Boot 3.2.5 + MyBatis-Plus 依赖清单、启动类、数据源/Redis/日志基础配置初稿 | 待郝佳鑫复核：依赖取舍（Flyway/Redis/Actuator 是否在第 2 轮引入）、包结构、配置命名 | 父 POM 与 16 个依赖坐标在 Maven Central 校验存在；JDK 17.0.11 + Maven 3.9.6 实机 `mvn -B -DskipTests package` BUILD SUCCESS（产物 `backend/target/medical-platform-backend.jar`）；`application.yml` 通过 YAML 解析校验 |
| 2026-11-10 | 代香晰 | DeepSeek Harness（deepseek 模型） | 搭建前端四端骨架：Vite 6 + Vue 3 + TypeScript + Element Plus + Pinia，含 18 个页面、角色路由守卫与统一请求封装 | 工程配置、路由与菜单、布局与页面骨架、frontend/README 初稿 | 待代香晰复核：页面划分与菜单、模拟登录策略、构建白名单 | `pnpm install` 与 `pnpm build` 通过（vue-tsc 类型检查 + Vite 构建）；无头浏览器走查患者/医生/管理员页面 |
| 2026-11-10 | 郝佳鑫 | DeepSeek Harness（deepseek 模型） | 编写认证与问诊接口测试：`AuthTest`（12 例）、`ConsultationTest`（10 例），并补充 H2 测试库配置与 `schema-test.sql` | 登录/账号状态/令牌篡改/过期/伪造 ADMIN 越权等断言，问诊发起、接诊、越权与结束后写保护的用例骨架 | 待郝佳鑫复核：越权覆盖是否足够、`ConsultationTest` 待问诊实现合入后逐个启用 | JDK 17.0.11 + Maven 3.9.6 实机 `mvn -B test`：22 例运行、0 失败、6 跳过（待问诊实现）；本地沙箱禁止 JVM 动态挂载 agent，故本地临时改用 subclass mock maker 验证，仓库不保留该覆盖 |
| 2026-11-10 | 代香晰 | DeepSeek Harness（deepseek 模型） | 实现患者端流程：登录对接、发起问诊、问诊列表与会话（消息轮询、发送去重、结束问诊），并补充零依赖 mock 服务 | 页面与 API 适配层、mock 服务、README 初稿 | 待代香晰复核：问诊接口字段、轮询间隔、mock 与真实后端的差异 | `pnpm build` 通过；mock + dev 实际走通登录→问诊→消息→结束，并经 Vite 代理核对接口返回 |
| 2026-11-10 | 代香晰 | DeepSeek Harness（deepseek 模型） | 按 `docs/design/api-contract.md` v1.0 对齐患者端问诊接口：`department`/`diseaseName`/`contentType`、分页 `PageResponse`、`Idempotency-Key` | 接口类型、页面字段、mock 数据与 README 的修正稿 | 待代香晰复核：与李梓语后端实现的字段差异、医生名称的展示方式 | `pnpm build` 通过；mock 经 Vite 代理实测登录、列表、创建、消息、结束 |
| 2026-11-10 | 李梓语 | DeepSeek Harness（deepseek 模型） | 实现 R3 问诊上下文：聚合与会话状态机、消息收发、接诊/结束的乐观锁条件更新、越权与数据范围校验、7 个 REST 端点，并补 `docs/design/consultation.md` | 领域/应用/接口/基础设施四层代码初稿、错误码映射、H2 测试表结构、启用 `ConsultationTest` 的改动 | 人工核对：状态迁移是否只经聚合方法、`patientId` 是否只取 JWT、并发接诊是否条件更新、消息表 `read_flag` 避开保留字、分页参数边界 | `mvn -B -ntp test`：22 例全绿（问诊 10 例含越权/结束后写保护）；逐条比对 `openapi.yaml` 字段与 `domain-model.md` §11 错误码 |
|  |  |  |  |  |  |  |

## 记录要求

- 不粘贴完整密钥、患者数据、老师未公开的题目材料。
- 写清楚“AI 生成后，人改了什么”，例如：重命名、补异常、改事务边界、补测试。
- 每轮至少 1 条记录；答辩时能指出对应 commit。
