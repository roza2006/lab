# 局域网多机测试与演示连接说明

> 没有云服务器也能做多角色、多用户测试。服务只在一台电脑上跑，其他电脑用浏览器通过局域网访问同一台主机即可。本文的命令按 Windows PowerShell 写，Linux/macOS 只差防火墙命令。

## 1 先回答：需要几台电脑

| 方案 | 电脑数 | 怎么分工 | 适用 |
|---|---:|---|---|
| 单机多身份 | 1 台 | 一台电脑跑 MySQL、后端、前端；用 Chrome 普通窗口 / Chrome 无痕 / Edge 分别登录患者、医生、药师 | 开发自测、单人走查、录屏 |
| 双机 | 2 台 | A 跑全部服务并登录患者；B 用浏览器登录医生、药师（两个浏览器或一普通一无痕） | 最推荐的答辩/联调配置 |
| 三机 | 3 台 | A 跑服务 + 患者；B 医生；C 药师 | 三人同时操作、演示并发接诊/审核 |
| 压力测试 | 1 台足够 | 本机 JMeter/脚本打 8080；不需要第二台机器 | 接口并发与幂等验证 |

**不需要单独的数据库服务器，也不需要云服务器。** MySQL、Redis、后端、前端都在 A 上；B/C 只开浏览器。

## 2 拓扑

```text
       同一 Wi-Fi / 手机热点 / 交换机
┌─────────────────────┐        ┌─────────────────────┐
│ 电脑 A（主机）        │        │ 电脑 B / C（客户端） │
│                     │        │                     │
│ MySQL 8  127.0.0.1  │        │ 浏览器               │
│ Redis 7  127.0.0.1  │        │ http://A的IP:5173    │
│ 后端     127.0.0.1  │        │                     │
│ Vite     0.0.0.0:5173│◀──────│ 只访问 5173          │
└─────────────────────┘  LAN   └─────────────────────┘
          │
          └── /api 由 Vite 代理到 127.0.0.1:8080
```

浏览器只访问 A 的 `5173`。Vite 在 A 上把 `/api` 转发给本机后端，所以：

- 不需要给浏览器配后端地址；
- 不会产生跨域；
- 不需要把 MySQL `3306`、Redis `6379`、后端 `8080` 暴露到局域网。

`deploy/docker-compose.yml` 已把这三个端口默认绑在 `127.0.0.1`，只有 Vite 的 `5173` 对外。

## 3 主机 A：启动服务

### 3.1 方式一：Docker Compose（推荐）

```powershell
cd C:\path\to\lab_repo
docker compose -f deploy/docker-compose.yml up -d --build
docker compose -f deploy/docker-compose.yml ps
```

看到 `mysql`、`redis` 为 `healthy`、`backend` 为 `running` 即可。后端日志出现 `Started PlatformApplication` 表示启动完成。

```powershell
docker compose -f deploy/docker-compose.yml logs -f backend
```

### 3.2 方式二：本机 MySQL + Redis + Java

```powershell
# 1. 建库并导入（只第一次）
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS medical_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p medical_platform < database/migrations/V002__core.sql
mysql -uroot -p medical_platform < database/migrations/V003__auth_consult.sql
mysql -uroot -p medical_platform < database/migrations/V004__prescription.sql
mysql -uroot -p medical_platform < database/migrations/V005__circulation.sql
mysql -uroot -p medical_platform < database/seed.sql

# 2. 启动后端
cd backend
mvn spring-boot:run
```

Redis 没起也能启动，但涉及 Redis 的功能没实现；演示主链路只依赖 MySQL。若要和 compose 一致，还是建议起 Redis。

### 3.3 启动前端

```powershell
cd frontend
pnpm install
pnpm dev
```

Vite 会同时打印 Local 和 Network 两个地址：

```text
Local:   http://localhost:5173/
Network: http://192.168.1.23:5173/
```

`Network` 那一行就是给 B/C 用的地址。`vite.config.ts` 已设置 `host: '0.0.0.0'`，不要改成 `localhost`，否则其他电脑连不上。

## 4 查主机 IP

```powershell
ipconfig
```

找当前联网网卡的 `IPv4 地址`，例如 `192.168.1.23`。不要用 `127.0.0.1` 给别的电脑。

如果同时有 VMware、VirtualBox、Tailscale 等多张网卡，按默认网关确认哪张是当前局域网网卡：

```powershell
Get-NetIPConfiguration | Where-Object { $_.IPv4DefaultGateway -ne $null } |
  Select-Object InterfaceAlias, IPv4Address, IPv4DefaultGateway
```

## 5 放行 Windows 防火墙

只在 A 上做。用**管理员 PowerShell**：

```powershell
New-NetFirewallRule -DisplayName "Medical Demo 5173" `
  -Direction Inbound -Protocol TCP -LocalPort 5173 -Action Allow -Profile Private
```

如果 B/C 需要直接访问后端 `8080`（不推荐，正常走 Vite 代理不需要）：

```powershell
New-NetFirewallRule -DisplayName "Medical Demo 8080" `
  -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private
```

检查当前网络是不是“专用网络”：

```powershell
Get-NetConnectionProfile
```

如果是 `Public`，Windows 默认更严格；把网络改成“专用”，或把上面的 `-Profile Private` 改成 `-Profile Any`（只建议在可信局域网）。

## 6 客户端 B/C 怎么连

1. 确认 B/C 和 A 连的是同一个 Wi-Fi/路由器/手机热点；
2. 浏览器打开 `http://<A的IPv4>:5173`，例如 `http://192.168.1.23:5173`；
3. 看到登录页即成功；
4. 用种子账号登录：

| 账号 | 密码 | 角色 |
|---|---|---|
| `patient01` | `Demo@2026` | 患者 |
| `doctor01` | `Demo@2026` | 医生 |
| `pharmacist01` | `Demo@2026` | 药师 |
| `admin` | `Demo@2026` | 管理员 |

如果登录页能开但接口 502，说明 Vite 代理没连上后端，回 A 看 `docker compose ps` 和后端日志。

## 7 一台电脑怎么同时演三个角色

同一浏览器同一站点共用 `localStorage`，直接开三个标签会互相顶掉登录态。用下面任一方式：

- Chrome 普通窗口：患者；
- Chrome 无痕窗口：医生；
- Edge 普通窗口：药师。

无痕窗口和不同浏览器各自有独立存储，互不影响。演示前把三个窗口分别登录好，不要刷新错窗口。

## 8 远程协作（不在同一局域网）

没有云服务器也可以用覆盖网络，不需要公网 IP：

### 8.1 Tailscale / ZeroTier（推荐）

A、B 都安装 Tailscale 并登录同一账号：

```powershell
tailscale ip -4
```

假设 A 的 Tailscale IP 是 `100.64.1.10`，B 直接开：

```text
http://100.64.1.10:5173
```

Tailscale 是点对点连接；打洞失败时才走中继，但仍然不需要你维护云服务器。防火墙放行 `5173` 即可。

### 8.2 临时公网隧道（只用于虚构演示数据）

```powershell
cloudflared tunnel --url http://127.0.0.1:5173
```

它会返回一个临时 `https://xxx.trycloudflare.com`。这种方式会把页面暴露到公网，**只能放虚构演示数据**，演示结束立即关掉。不要把真实患者数据放进去。

## 9 测试矩阵

| 编号 | 场景 | 机器 | 预期 |
|---|---|---|---|
| L-01 | 患者登录并发起问诊 | A | 列表出现“待接诊” |
| L-02 | 两名医生同时接同一会话 | A/B | 只有一人成功，另一人 409 |
| L-03 | 医生接诊、发消息、开方、提交 | B | 状态 `PENDING_REVIEW` |
| L-04 | 药师审核通过 | C | 状态 `APPROVED` |
| L-05 | 患者选药房发起流转 | A | 状态 `CIRCULATED`，药房工作台出现待接方 |
| L-06 | 药师接方并核销 | C | 状态 `REDEEMED`，只生成 1 条发药记录 |
| L-07 | 同一 `Idempotency-Key` 重复核销 | C | 返回首次结果，不重复发药 |
| L-08 | 患者查看处方追踪 | A | 时间线完整 |
| L-09 | 跨药房接方/核销 | B/C | 403 |
| L-10 | 插件/断网重试 | B | 前端有错误提示，数据不半写 |

## 10 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| A 上 `localhost:5173` 能开，B 打不开 | Vite 只监听了 localhost，或防火墙拦了 | 确认 `host: '0.0.0.0'`；放行 5173；确认同一网段 |
| B 打开是空白/一直转圈 | A 的 Vite 没起，或 IP 不对 | A 上看 `pnpm dev` 输出；`ping <A-IP>` |
| `8080` 被其他程序占用 | 本机已有服务监听 8080 | 后端改 `SERVER_PORT=8081`，前端用 `VITE_PROXY_TARGET=http://127.0.0.1:8081 pnpm dev` |
| `docker` 报 named pipe 找不到 | Docker Desktop 没启动 | 先启动 Docker Desktop，等鲸鱼图标变绿再 `docker compose ...` |
| 页面能开，接口 502 | Vite 代理指不到后端 | A 上 `docker compose ps`，确认 backend running；`curl http://127.0.0.1:8080/actuator/health` |
| 登录后马上 401 | 令牌过期/被另一窗口覆盖 | 用不同浏览器/无痕；重新登录 |
| 429 `RATE_LIMITED` | 触发登录/消息限流 | 等一分钟；测试环境可设 `RATE_LIMIT_ENABLED=false` |
| 数据库连不上 | MySQL 没起/端口被占 | `docker compose logs mysql`；改 `.env` 的 `DB_PORT` |
| 想重置演示数据 | 种子只在空数据卷执行 | `docker compose ... down -v` 后重新 `up`（会删数据） |
| 公司/学校 Wi-Fi 互相隔离 | AP isolation | 换手机热点，或改用 Tailscale |

## 11 安全边界

- 只暴露 `5173`；MySQL、Redis、后端默认绑 `127.0.0.1`；
- 演示数据全部虚构，不要导入真实患者信息；
- `JWT_SECRET`、数据库密码不要用仓库默认值上公网；
- 临时公网隧道用完即关；
- 这不是生产部署，没有 Nginx/TLS/审计，答辩时按“本地演示环境”表述。
