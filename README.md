# 智慧医疗在线问诊与处方流转平台

> 软件工程课程实验小组项目：连接患者、医生、药师与合作药房，完成“图文问诊—病历—电子处方—药师审核—处方流转—药房核销”的业务闭环。

## 1. 项目简介

本项目面向常见病复诊与轻症健康咨询，提供在线图文问诊、电子处方开具与审核、处方流转至合作药房、身份核验与核销、用药指导等功能。系统不替代实体医疗机构的首次诊断，不承担急诊救治职能；首版不接入医保结算、在线支付与检验检查预约。

v1 实际交付 **Identity、Consultation、Prescription、Circulation 四个核心上下文**。药品目录、审计、通知、运营管理只保留表结构、页面骨架或设计说明，不作为已实现功能。

## 2. 核心业务闭环

```text
患者发起图文问诊
      ↓
医生接诊 / 书写病历
      ↓
医生创建处方草稿 → 提交
      ↓
药师审核 → 通过 / 驳回
      ↓
处方流转至合作药房
      ↓
药房核验身份 → 调剂发药 → 核销
      ↓
患者查看处方与用药指导
```

处方状态按 `DRAFT → PENDING_REVIEW → APPROVED → CIRCULATED → REDEEMED` 管理，并处理驳回、取消、过期等异常分支。

## 3. 系统角色

| 角色 | 主要职责 |
|---|---|
| 患者 | 实名认证、发起问诊、查看处方与用药指导 |
| 医生 | 接诊、书写病历、创建/提交处方 |
| 药师 | 处方审核、通过/驳回、药品目录维护 |
| 管理员 | 用户、角色、权限、药品目录、审计与统计 |
| 合作药房 | 接方、身份核验、发药、核销回传 |

## 4. 技术栈

- 后端：Java 17、Spring Boot 3、Spring Security 6、MyBatis-Plus、MySQL 8、Redis 7（预留，业务代码未使用）
- 前端：Vue 3、TypeScript、Vite、Element Plus、Pinia
- 文档：Markdown、OpenAPI 3.0、Mermaid / draw.io
- 部署：Docker Compose（单机演示）；Nginx/TLS/MySQL 主从为目标方案，未落地

## 5. 目录结构

```text
docs/        需求、设计、测试、过程、安全、报告、演示
backend/     模块化单体后端（按限界上下文分包）
frontend/    患者端、医生端、药师端、管理端
database/    schema、migration、seed
deploy/      docker-compose、Nginx、部署说明
.github/     Issue/PR 模板与 CI
```

## 6. 项目文档

- [提交计划](COMMIT_PLAN.md)：6 轮 × 6 人，每轮的任务、目录与 commit message
- [文档索引](docs/README.md)：需求、设计、测试、过程、安全、报告
- [交付物索引](docs/report/README.md)：按报告章节找图表、数据库、演示材料
- [部署说明](docs/report/deployment.md)：环境要求、一键启动、演示账号、排错
- [核销与幂等设计](docs/design/circulation-redeem.md)：处方核销、幂等键与发药记录
- [局域网多机测试](docs/demo/local-network-test.md)：没有云服务器时怎么让 2–3 台电脑连同一套服务
- [发布说明](docs/process/release-notes.md)：v1.0.0 范围、限制与运行方式
- [AI 使用记录](docs/ai-usage.md)：工具、范围、人工修改与验证
- [远程仓库重置说明](RESET_GUIDE.md)：已完成记录与回滚参考

## 7. 当前进度

- [x] 仓库重置与目录骨架
- [x] R1-刘飞燕：README、目录规范、文档索引
- [x] R1-李梓语：需求范围与角色
- [x] R1-代香晰：用例清单（`docs/requirements/use-cases.md`，v1.1 已对齐 v1 实现）
- [x] R1-郝佳鑫：领域模型（`docs/design/domain-model.md`，v1.1 已回写实现边界）
- [x] R1-魏宇佳：数据库表清单与数据字典（`database/schema.sql`、`docs/database/dictionary.xlsx`）
- [x] R1-魏宇佳：核心 ER 图、表总览图、系统部署图（`docs/database/diagrams/`、`deploy/`）
- [x] R1-班国勇：测试计划与协作模板
- [x] R2-郝佳鑫：后端工程骨架（`backend/pom.xml`、`PlatformApplication`、`application.yml`）
- [x] R2-刘飞燕：架构设计与 ADR-001（`docs/design/architecture.md`、`docs/design/adr/ADR-001.md`）
- [x] R2-代香晰：前端四端骨架（`frontend/`，Vue 3 + Vite，18 个页面）
- [x] R3-代香晰：患者端登录、发起问诊与问诊会话（`frontend/src/views/patient`）
- [x] R4-代香晰：医生开方与药师审核页面（`frontend/src/views/doctor`、`frontend/src/views/pharmacist`）
- [x] R5-代香晰：药房核销与患者处方追踪（`frontend/src/views/pharmacist`、`frontend/src/views/patient`）
- [x] R2-李梓语：API 契约与 OpenAPI（`docs/design/api-contract.md`、`backend/src/main/resources/openapi.yaml`）
- [x] R2-班国勇：本地编排与 CI（`deploy/`、`.github/workflows/ci.yml`）
- [x] R3-刘飞燕：登录、JWT、角色权限（`backend/src/main/java/com/medical/platform/identity`）
- [x] R3-李梓语：问诊会话与消息（`backend/src/main/java/com/medical/platform/consultation`）
- [x] R3-魏宇佳：Flyway 迁移 V002/V003，会话表对齐代码并补查询索引（`database/migrations/`）
- [x] R3-郝佳鑫：认证与问诊接口测试（`backend/src/test`，22 例全部通过）
- [x] R4-郝佳鑫：处方状态机用例（8 状态 / 10 条正向迁移 + 非法迁移，`docs/test/prescription-state-cases.md`、`backend/src/test/java/com/medical/platform/prescription/PrescriptionStateTest.java`）
- [x] R4-刘飞燕：处方聚合、明细与 8 状态机（`backend/src/main/java/com/medical/platform/prescription`）
- [x] R4-李梓语：药师审核，通过/驳回与审核记录（`prescription_review`）
- [x] R4-魏宇佳：V004 处方/明细/审核记录迁移，索引与字段对齐（`database/migrations/`）
- [x] R4-郝佳鑫：处方状态机用例启用（17 例全绿；补齐 `RX_STATE_INVALID`、`PUT` 改草稿等断言）
- [x] R5-郝佳鑫：流转核销幂等用例（重放/并发/越权，`docs/test/circulation-idempotency-cases.md`、`backend/src/test/java/com/medical/platform/circulation/CirculationIdempotencyTest.java`）
- [x] R5-李梓语：核销、幂等键与发药记录（`backend/src/main/java/com/medical/platform/circulation`、`docs/design/circulation-redeem.md`）
- [x] R5-刘飞燕：流转单创建、列表、药房接方与拒接（`backend/src/main/java/com/medical/platform/circulation`、`docs/design/circulation-order.md`）
- [x] R5-魏宇佳：V005 流转索引、seed.sql 演示数据与 compose 初始化（`database/migrations/`、`database/seed.sql`、`deploy/docker-compose.yml`）
- [x] R4-班国勇：安全文档（STRIDE 与隐私检查表）
- [x] R6-刘飞燕：后端回归与测试报告（`docs/test/test-report.md`，最终 82 例全绿）
- [x] R6-李梓语：限流、越权复核与驳回后修改重提（`docs/security/r6-hardening.md`）
- [x] R6-郝佳鑫：最终验收用例与覆盖统计（`docs/test/acceptance.md`、`AcceptanceTest`，最终 82 例全绿）
- [x] R6-代香晰：演示流程打磨与截图（`docs/demo/ui-walkthrough.md`、`docs/demo/screenshots/`）
- [x] R6-魏宇佳：seed.sql 演示数据与 compose 初始化（`database/seed.sql`、`deploy/docker-compose.yml`）
- [x] R6-魏宇佳：部署说明与交付物索引（`docs/report/`、`deploy/env.example`）
- [x] R6-班国勇：答辩演示脚本（`docs/demo/demo-script.md`）
- [x] R6-刘飞燕：患者流转口径、ER/部署图、文档状态统一（本提交）
- [x] 局域网多机测试说明与 Vite 监听配置（`docs/demo/local-network-test.md`）
- [ ] 前端端到端验收、真实 MySQL 联调、干净机器部署演练（答辩前待办）
- [x] 发布标签 `v1.0.0`

## 8. 本地开发与演示

后端是 Spring Boot 3.2（Java 17 + Maven），前端是 Vue 3 + Vite；运行需要 MySQL 8.0.16+ 与 Redis 7。
完整的部署步骤、演示账号和排错见 [部署说明](docs/report/deployment.md)。

### 8.1 一键起演示环境

```bash
docker compose -f deploy/docker-compose.yml up -d --build
docker compose -f deploy/docker-compose.yml ps
```

MySQL 首次初始化（数据卷为空）会按顺序执行 `database/migrations/V002~V005` 和 `seed.sql`，
起来就有 4 个演示账号（`admin` / `doctor01` / `patient01` / `pharmacist01`，密码 `Demo@2026`）和一条待审核处方。
想重置数据：`down -v` 后再 `up`。

### 8.2 手动起后端

```bash
cd backend
mvn spring-boot:run            # 默认 8080；MySQL/Redis 没起会在数据源阶段失败，属预期
mvn -DskipTests package
mvn test
curl http://127.0.0.1:8080/actuator/health
```

### 8.3 手动起前端

```bash
cd frontend
pnpm install
pnpm dev                       # 默认 5173，代理到 8080
```

### 8.4 准备数据库

```bash
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS medical_platform \
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

# 按迁移顺序建表，再导演示数据
for f in database/migrations/V00*.sql database/seed.sql; do
  mysql -uroot -p medical_platform < "$f"
done
```

不想要演示数据就只跑 `database/migrations/`；`seed.sql` 用固定 ID，只能空库执行一次。

配置支持环境变量覆盖，仓库内不含真实密钥：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SERVER_PORT` | `8080` | 服务端口 |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `127.0.0.1` / `3306` / `medical_platform` | MySQL 连接 |
| `DB_USER` / `DB_PASSWORD` | `root` / `root` | 本地开发账号 |
| `REDIS_HOST` / `REDIS_PORT` | `127.0.0.1` / `6379` | Redis 连接 |
| `FLYWAY_ENABLED` | `false` | 打开后由 Flyway 执行 `database/migrations`（compose 默认关，走初始化目录） |
| `FLYWAY_LOCATIONS` | `filesystem:../database/migrations` | 换启动目录时覆盖 |

compose 侧的可调项见 [`deploy/env.example`](deploy/env.example)，复制为 `deploy/.env` 即可生效。
