# frontend —— 四端前端骨架（R2-C）

患者、医生、药师、管理员四端共用一套 Vue 3 工程，按角色拆分路由、菜单与页面骨架；业务接口在 R3–R5 接入。

## 1. 技术栈

- Vue 3（`<script setup>` + TypeScript）、Vue Router 4、Pinia 3
- Element Plus 2、Vite 6
- 目录约定见 `docs/README.md` 与 `COMMIT_PLAN.md`：`src/router`、`src/views/<角色>/`

## 2. 本地运行

```bash
cd frontend
pnpm install
pnpm dev          # http://127.0.0.1:5173
pnpm build        # 类型检查 + 生产构建，产物在 dist/
```

开发服务器把 `/api` 代理到 `http://127.0.0.1:8080`（Spring Boot，R2 后端骨架）；如后端端口不同，改 `vite.config.ts` 的 `server.proxy`。
接口前缀可用环境变量 `VITE_API_BASE_URL` 覆盖，默认 `/api/v1`。

### 没有后端时：本地 mock

```bash
# 终端 1：启动零依赖 mock（前端/mock/server.mjs，端口 8080）
pnpm mock

# 终端 2：正常启动前端
pnpm dev
```

mock 账号：`patient` / `doctor` / `pharmacist` / `admin`，密码均为 `123456`；已内置一条进行中会话与若干消息，方便直接演示 R3 患者流程。

## 3. 角色与路由

| 角色 | 代码 | 默认落地页 | 页面数 |
|---|---|---|---|
| 患者 | `PATIENT` | `/patient/home` | 7 |
| 医生 | `DOCTOR` | `/doctor/queue` | 4 |
| 药师 | `PHARMACIST` | `/pharmacist/reviews` | 3 |
| 管理员 | `ADMIN` | `/admin/users` | 4 |

页面与用例（`docs/requirements/use-cases.md`）的对应关系写在路由 `meta.useCase` 中，页面顶部同步展示，便于评审时对照。

```text
/patient/home                        患者首页            UC-P03
/patient/consultations/new           发起图文问诊        UC-P03
/patient/consultations               我的问诊            UC-P05
/patient/consultations/:id           问诊会话            UC-P04
/patient/prescriptions               我的处方            UC-P06
/patient/prescriptions/:id           处方详情与追踪      UC-P06
/patient/profile                     个人中心与授权      UC-P07
/doctor/queue                        待接诊队列          UC-D01
/doctor/consultations/:id            接诊与病历          UC-D02
/doctor/consultations/:id/prescription  开方工作台       UC-D04
/doctor/history                      历史问诊            UC-D07
/pharmacist/reviews                  待审处方            UC-H01
/pharmacist/reviews/:id              处方审核            UC-H02
/pharmacist/dispense                 药房工作台          UC-H05
/admin/users                         账号与角色          UC-A01
/admin/permissions                   权限策略            UC-A02
/admin/drugs                         药品目录            UC-A03
/admin/audits                        审计与统计          UC-A04
```

## 4. 路由守卫与登录态

- `src/router/index.ts`：`beforeEach` 校验登录态与 `meta.roles`，未登录跳 `/login?redirect=...`，越权跳 `/403`；
- `src/stores/auth.ts`：调用真实接口 `POST /api/v1/auth/login`，保存 `accessToken` 与 `{id, username, roleCode}`，按 `roleCode` 映射前端角色（`PHARMACY` 暂复用药师端）；
- `src/api/http.ts`：兼容统一信封 `ApiResult` 与后端当前的裸对象响应，注入 `Authorization`，401 时触发 `mp:unauthorized` 事件，由 `main.ts` 清理登录态并回登录页。

## 5. R3 患者端流程

| 页面 | 路由 | 用例 | 说明 |
|---|---|---|---|
| 患者首页 | `/patient/home` | UC-P03 | 进行中/待接诊统计 + 最近问诊，快捷发起 |
| 发起问诊 | `/patient/consultations/new` | UC-P03 | 科室 + 主诉（5–1000 字）+ 病情补充，提交后进入会话 |
| 我的问诊 | `/patient/consultations` | UC-P05 | 状态筛选、会话列表、进入会话 |
| 问诊会话 | `/patient/consultations/:id` | UC-P04 | 消息轮询（3 秒）、发送（客户端消息 ID 去重）、结束问诊 |

接口收敛在 `src/api/consultation.ts`，路径沿用 `docs/requirements/use-cases.md` §3/§7：

```text
POST /api/v1/consultations               创建会话
GET  /api/v1/consultations               我的会话列表
GET  /api/v1/consultations/{id}          会话详情
GET  /api/v1/consultations/{id}/messages 消息列表
POST /api/v1/consultations/{id}/messages 发送消息（clientMsgId 去重）
POST /api/v1/consultations/{id}/end      结束问诊
```

> B 的 `docs/design/api-contract.md` 合并后若字段不同，只需改 `src/api/consultation.ts` 的适配层；页面与路由不受影响。

## 6. 页面骨架约定

尚未进入实现轮次的页面（医生端、药师端、管理端等）仍通过 `src/components/PageStub.vue` 渲染“标题 + 用例编号 + 目标说明 + 计划区域”，评审时可直接对照用例。实现时替换对应 `views/<角色>/*.vue` 即可，路由与菜单无需改动。

## 7. 边界

- 本目录只放前端代码；接口契约由 B 在 `docs/design/api-contract.md` 维护，R3 的问诊接口为前端先行约定；
- 不提交 `node_modules/`、`dist/`、`.env*`（已在根 `.gitignore` 中忽略）；
- 演示数据均为虚构，不得录入真实患者信息。
