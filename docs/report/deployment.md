# 部署说明

答辩现场用一台电脑起整套服务，或者用两三台电脑连同一套服务（见
[局域网多机测试](../demo/local-network-test.md)）。下面按"最省事的顺序"写。

## 1. 环境要求

| 项目 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | 后端编译与运行 |
| Maven | 3.9+ | 打包 |
| Node.js / pnpm | 18+ / 8+ | 前端构建 |
| MySQL | 8.0.16+ | CHECK 约束需要 8.0.16 以上 |
| Redis | 7 | 登录会话与幂等 |
| Docker（可选） | 24+ | 用 compose 一键起环境时用 |

## 2. 方式一：Docker Compose（推荐）

```bash
docker compose -f deploy/docker-compose.yml up -d --build
docker compose -f deploy/docker-compose.yml ps
```

第一次启动、数据卷为空时，MySQL 会按顺序执行
`V002__core → V003__auth_consult → V004__prescription → V005__circulation → seed`，
建完表直接带演示数据。后端起来后访问 `http://localhost:8080`，前端另起（见第 4 节）。

演示账号（密码都是 `Demo@2026`）：

| 账号 | 角色 | 能做什么 |
|---|---|---|
| `patient01` | 患者 | 发起问诊、查看处方、追踪流转 |
| `doctor01` | 医生 | 接诊、写病历、开方 |
| `pharmacist01` | 药师 | 审核、流转、核销 |
| `admin` | 管理员 | 查看全部数据 |

## 3. 方式二：手动起服务

```bash
# 1) 建库并按顺序导入
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS medical_platform \
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
mysql -uroot -p medical_platform < database/migrations/V002__core.sql
mysql -uroot -p medical_platform < database/migrations/V003__auth_consult.sql
mysql -uroot -p medical_platform < database/migrations/V004__prescription.sql
mysql -uroot -p medical_platform < database/migrations/V005__circulation.sql
mysql -uroot -p medical_platform < database/seed.sql

# 2) Redis
redis-server

# 3) 后端
cd backend && mvn spring-boot:run

# 4) 前端
cd frontend && pnpm install && pnpm dev
```

环境变量用默认值就能跑，需要改的话参考 `backend/src/main/resources/application.yml`，
或复制 [`deploy/env.example`](../../deploy/env.example) 为 `deploy/.env` 后由 compose 读取。

## 4. 访问地址

| 端 | 地址 | 说明 |
|---|---|---|
| 前端 | `http://localhost:5173` | Vite 开发服务器 |
| 后端 | `http://localhost:8080` | 接口前缀 `/api/v1` |
| 健康检查 | `http://localhost:8080/actuator/health` | 返回 `UP` 即可 |

## 5. 数据维护

- **重置演示数据**：`docker compose -f deploy/docker-compose.yml down -v` 后再 `up`；
  手动部署时重新建库、按顺序导入迁移与 `seed.sql`。
- **备份**：`mysqldump -uroot -p medical_platform > backup.sql`；
  恢复用 `mysql -uroot -p medical_platform < backup.sql`。
- **种子数据只能用一次**：`seed.sql` 里是固定 ID（1001 起），空库执行，
  重复执行会主键冲突。

## 6. 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| 后端启动报数据源错误 | MySQL/Redis 没起，或库名、账号不对 | 先确认 `mysql -uroot -p` 能连上，再核对环境变量 |
| 表是空的 | 数据卷已存在，初始化脚本不会重跑 | `down -v` 清卷后重启 |
| 登录提示账号不存在 | 没导 `seed.sql` | 补导种子数据 |
| 前端接口 404 或跨域 | Vite 代理没指向后端 | 看 `frontend/vite.config.ts` 的 proxy 配置 |
| 端口被占用 | 3306 / 6379 / 8080 / 5173 已有服务 | 改环境变量或停掉占用的进程 |

## 7. 答辩前的检查清单

- [ ] `docker compose ... ps` 三个容器都是 healthy / running
- [ ] 健康检查返回 `UP`
- [ ] 四个演示账号都能登录
- [ ] 走一遍"问诊 → 开方 → 审核 → 流转 → 核销"，时间线能看到状态变化
- [ ] 数据库里 `flyway_schema_history`（若开启 Flyway）或表数量与 `database/schema.sql` 一致
