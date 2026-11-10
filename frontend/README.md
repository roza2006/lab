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
- `src/stores/auth.ts`：`token` 与用户信息持久化在 `localStorage`，`logout()` 清除；
- **R2 为本地模拟登录**：任选角色即可进入，密码不校验。R3-A 完成 `/api/v1/auth/login` 后，只需替换 `loginAs` 为真实请求；
- `src/api/http.ts`：统一 `ApiResult` 解包、`Authorization` 注入与 401 事件，供 R3 起复用。

## 5. 页面骨架约定

每个页面通过 `src/components/PageStub.vue` 渲染“标题 + 用例编号 + 目标说明 + 计划区域”，保证四端风格一致、评审时能直接对照用例。R3–R5 实现具体页面时，从对应 `views/<角色>/*.vue` 替换占位内容即可，路由与菜单无需改动。

## 6. 边界

- 本目录只放前端代码；接口契约由 B 在 `docs/design/api-contract.md` 维护，本骨架不重复定义字段；
- 不提交 `node_modules/`、`dist/`、`.env*`（已在根 `.gitignore` 中忽略）；
- 演示数据均为虚构，不得录入真实患者信息。
