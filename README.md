# 智慧医疗在线问诊与处方流转平台

> 软件工程课程实验小组项目：连接患者、医生、药师与合作药房，完成“图文问诊—病历—电子处方—药师审核—处方流转—药房核销”的业务闭环。

## 1. 项目简介

本项目面向常见病复诊与轻症健康咨询，提供在线图文问诊、电子处方开具与审核、处方流转至合作药房、身份核验与核销、用药指导等功能。系统不替代实体医疗机构的首次诊断，不承担急诊救治职能；首版不接入医保结算、在线支付与检验检查预约。

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

- 后端：Java 17、Spring Boot 3、Spring Security 6、MyBatis-Plus、MySQL 8、Redis 7
- 前端：Vue 3、TypeScript、Vite、Element Plus、Pinia
- 文档：Markdown、OpenAPI 3.0、Mermaid / draw.io
- 部署：Docker Compose、Nginx

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
- [AI 使用记录](docs/ai-usage.md)：工具、范围、人工修改与验证
- [远程仓库重置说明](RESET_GUIDE.md)：已完成记录与回滚参考

## 7. 当前进度

- [x] 仓库重置与目录骨架
- [x] R1-刘飞燕：README、目录规范、文档索引
- [ ] R1-李梓语：需求范围与角色
- [x] R1-代香晰：用例清单（`docs/requirements/use-cases.md`，v1.0 待评审）
- [x] R1-郝佳鑫：领域模型（`docs/design/domain-model.md`，v0.1 待评审）
- [x] R1-魏宇佳：数据库表清单与数据字典（`database/schema.sql`、`docs/database/dictionary.xlsx`）
- [x] R1-魏宇佳：核心 ER 图、表总览图、系统部署图（`docs/database/diagrams/`、`deploy/`）
- [ ] R1-班国勇：测试计划与协作模板
- [x] R2-郝佳鑫：后端工程骨架（`backend/pom.xml`、`PlatformApplication`、`application.yml`）
- [x] R2-刘飞燕：架构设计与 ADR-001（`docs/design/architecture.md`、`docs/design/adr/ADR-001.md`）
- [x] R2-代香晰：前端四端骨架（`frontend/`，Vue 3 + Vite，18 个页面）
- [x] R3-刘飞燕：登录、JWT、角色权限（`backend/src/main/java/com/medical/platform/identity`）
- [x] R3-郝佳鑫：认证与问诊接口测试（`backend/src/test`，22 例：16 通过 / 6 待问诊实现启用）
- [ ] R2：接口契约、核心表与种子、CI
- [ ] R4–R6：处方与审核、流转与部署、测试与发布

## 8. 本地开发

> 第 2 轮起后端为可编译的 Spring Boot 3.2 工程（Java 17 + Maven）；运行需要 MySQL 8 与 Redis 7，前端骨架随第 2 轮前端任务加入。

```bash
# 后端（Java 17 + Maven 3.9）
cd backend
mvn spring-boot:run            # 默认 8080；未启动 MySQL/Redis 时会在数据源阶段失败，属预期

# 打包与测试
mvn -DskipTests package
mvn test

# 存活探针（启用安全配置后需放行）
curl http://127.0.0.1:8080/actuator/health
```

配置全部支持环境变量覆盖，仓库内不含真实密钥：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SERVER_PORT` | `8080` | 服务端口 |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `127.0.0.1` / `3306` / `medical_platform` | MySQL 连接 |
| `DB_USER` / `DB_PASSWORD` | `root` / `root` | 本地开发账号 |
| `REDIS_HOST` / `REDIS_PORT` | `127.0.0.1` / `6379` | Redis 连接 |
| `FLYWAY_ENABLED` | `false` | 迁移脚本落地后再开启 |

```bash
# 前端（第 2 轮前端骨架加入后）
cd frontend
pnpm install
pnpm dev

# 数据库（schema 见第 1 轮 database/schema.sql）
mysql -u root -p < database/schema.sql
```
