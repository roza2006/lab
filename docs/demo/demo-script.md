# 答辩演示脚本（v1.0.0）

| 版本 | 日期 | 负责人 | 状态 | 关联材料 |
|---|---|---|---|---|
| v1.0 | 2026-11-24 | 班国勇 | 待评审 | `docs/process/release-notes.md`、`docs/test/test-report.md`、`docs/design/api-contract.md` |

## 1. 用途

给站在投影前的人用：每一步写清楚点什么、调哪个接口、应该看到什么、失败了怎么兜。技术细节与接口参数以 `docs/design/api-contract.md` 为准，本脚本只保留演示需要的最小集合。

## 2. 演示前准备（10 分钟清单）

### 2.1 启动

二选一，演示前先跑通并保持不动：

```bash
# 方式一：本地编排（需要 Docker）
docker compose -f deploy/docker-compose.yml up -d --build
docker compose -f deploy/docker-compose.yml logs -f backend

# 方式二：本机 Java + MySQL
mysql -uroot -p medical_platform < database/schema.sql
mysql -uroot -p medical_platform < database/seed.sql
cd backend && mvn spring-boot:run
```

前端：

```bash
cd frontend
pnpm install
pnpm dev          # 默认 5173，/api 代理到 8080
```

### 2.2 演示账号（来自 `database/seed.sql`，全部虚构）

| 账号 | 角色 | 密码 | 用途 |
|---|---|---|---|
| `patient01` | 患者 | `Demo@2026` | 发起问诊、选药房、查处方 |
| `doctor01` | 医生 | `Demo@2026` | 接诊、发消息、开方 |
| `pharmacist01` | 药师（归属示例药房） | `Demo@2026` | 审核处方、接方、核销 |
| `admin` | 管理员 | `Demo@2026` | 用户与权限页面 |

### 2.3 开演前自检

1. `curl http://127.0.0.1:8080/actuator/health` 返回 `UP`；
2. `mysql -uroot -p -e "select username, role_code from medical_platform.sys_user"` 有上面四个账号；
3. 浏览器准备两个窗口：普通窗口演患者，无痕窗口演医生与药师（避免同一浏览器串 token）；
4. 演示前先把"药师审核队列"刷一遍，确认没有上一轮遗留的待审核处方干扰。

## 3. 主线演示（8 幕，约 8 分钟）

### 第 1 幕：患者发起问诊（1 分钟）

- 页面：`/login` 登录 `patient01` → `/patient/consultations/new`
- 操作：科室填"内科"，复诊疾病填"高血压"，主诉填 100 字以内的就诊描述，提交
- 接口：`POST /api/v1/consultations`，请求体 `{"department":"内科","diseaseName":"高血压","chiefComplaint":"..."}`
- 预期：跳转到会话页，列表里出现一条"待接诊"
- 口述：问诊创建时写入会话与患者身份，患者只能看到自己的会话，医生侧看到待接诊列表
- 失败排查：401 说明 token 失效，重新登录；422 看字段长度（科室 64 字、疾病 128 字、主诉 1000 字上限）

### 第 2 幕：医生接诊（1 分钟）

- 页面：无痕窗口 `/login` 登录 `doctor01` → `/doctor/queue`
- 操作：找到刚才那条问诊，点"接诊"
- 接口：`POST /api/v1/consultations/{id}/accept`
- 预期：状态变为"进行中"；另一名医生账号（如 `doctor02`）再看列表时该条消失
- 口述：接诊用条件更新，先到先得；重复接诊返回 409 `SESSION_ALREADY_ACCEPTED`
- 失败排查：409 说明别的会话已接走，换一条演示

### 第 3 幕：图文沟通与结束（1 分钟）

- 页面：`/doctor/consultations/{id}`
- 操作：发送两条文字消息（各 100 字以内），患者窗口刷新看到消息
- 接口：`POST /api/v1/consultations/{id}/messages`，`{"contentType":"TEXT","content":"..."}`
- 预期：双方都能看到消息，患者页面轮询刷新；消息上限 4000 字，超长返回 422
- 口述：消息按会话归属校验，患者 A 拿患者 B 的会话 ID 访问会得到 403 `SCOPE_DENIED`

### 第 4 幕：医生开方并提交审核（1.5 分钟）

- 页面：`/doctor/consultations/{id}/prescription`
- 操作：诊断填"原发性高血压"，添加 1 条药品（名称、规格、单次剂量、频次、疗程、数量），先存草稿，再点"提交审核"
- 接口：`POST /api/v1/consultations/{id}/prescriptions` → `POST /api/v1/prescriptions/{id}/submit`
- 请求体示例：

```json
{
  "diagnosis": "原发性高血压",
  "items": [
    { "drugCode": "D001", "drugName": "氨氯地平片", "specification": "5mg*7片",
      "dosage": "5mg", "frequency": "qd", "duration": "7天", "quantity": 1, "unitPrice": 12.50 }
  ]
}
```

- 预期：处方状态从"草稿"变为"待审核"
- 口述：状态机只允许 `DRAFT → PENDING_REVIEW`，非法迁移返回 409 `RX_STATE_INVALID`；同一药品不能重复，最多 5 条明细
- 失败排查：400 `RX_ITEM_EMPTY` 说明明细为空；409 说明处方已提交过

### 第 5 幕：药师审核（1.5 分钟）

- 页面：无痕窗口登录 `pharmacist01` → `/pharmacist/reviews`
- 操作：打开待审核处方，先演示一次"驳回"（理由写 10 字以上），再对另一张处方演示"通过"
- 接口：
  - 通过：`POST /api/v1/prescriptions/{id}/reviews/approve`，`{"comment":"用法用量无误，同意调配"}`
  - 驳回：`POST /api/v1/prescriptions/{id}/reviews/reject`，`{"comment":"剂量偏高，请调整为 5mg 每日一次后重新提交"}`
- 预期：通过后状态变"已通过"，驳回后变"已驳回"且医生可在原会话新建草稿
- 口述：签名摘要由服务端按处方、药师与时间生成并落库（`ReviewSignature.of`），不依赖前端传入，事后可核对"谁在什么时候审的"；驳回理由少于 10 字返回 422
- 失败排查：403 `RX_SCOPE_DENIED` 说明不是该处方所属医生的方子

### 第 6 幕：患者选择药房并流转（1 分钟）

- 页面：患者窗口 `/patient/prescriptions/{id}`
- 操作：选择合作药房，点"流转到药房"
- 接口：`POST /api/v1/circulations`，`{"rxId":1,"pharmacyId":1}`
- 预期：处方状态变为"流转中"，页面出现流转单与药房状态
- 口述：只有本人名下"已通过"的处方能流转；未审核返回 409，重复流转返回同一张（200），药房不存在/停用返回 400
- 失败排查：403 `RX_SCOPE_DENIED` 说明不是本人处方；400 `CIRC_PHARMACY_INVALID` 说明药房不可用

### 第 7 幕：药房接方（1 分钟）

- 页面：药师窗口 → `/pharmacist/dispense`
- 操作：在待接方列表里点"接方"；另准备一张流转单演示"拒接"（填拒绝原因）
- 接口：
  - 接方：`POST /api/v1/circulations/{id}/accept`
  - 拒接：`POST /api/v1/circulations/{id}/reject`，`{"reason":"药品暂时缺货"}`
- 预期：流转单状态从"已创建"变为"已接方"（拒接则变"已拒绝"），患者端追踪页同步
- 口述：流转单只有 `CREATED → ACCEPTED → REDEEMED` 与 `CREATED → REJECTED` 两条合法路径，接方与拒接都只能操作"已创建"的流转单；接方只能由目标药房操作，其它药房调用返回 403
- 失败排查：409 说明已被接走或状态不允许

### 第 8 幕：核销与轨迹追踪（1 分钟）

- 页面：药师窗口核销页，点"核销发药"；患者窗口打开处方详情看轨迹
- 接口：
  - 核销：`POST /api/v1/circulations/{id}/redeem`，请求头 `Idempotency-Key: <UUID>`，请求体 `{"batchNo":"B20261124","evidence":"身份证后四位 1234"}`
  - 轨迹：`GET /api/v1/prescriptions/{id}/trace`
- 预期：状态变为"已核销"；**再点一次核销不会重复扣减**，返回第一次的结果
- 口述：幂等键落在 `idempotency_record` 唯一键上，重放与并发都只生效一次；前端每次点击都生成新 UUID，后端对同一键返回首次结果。不带该头时请求仍可处理，但重复点击会因状态机已是"已核销"而返回 409。这是第 5 轮测试的重点，`CirculationIdempotencyTest` 覆盖重放、并发、越权
- 失败排查：如果第二次点击报 409 而不是返回同样结果，说明前端没带幂等键或键每次不同，检查请求头

## 4. 兜底方案

| 情况 | 处理 |
|---|---|
| 前端页面打不开 | 直接演示接口：用 `curl` 或 Postman 按第 3 节的接口依次调用，页面只作为"界面长什么样"补充 |
| 数据库是空的 | 现场导入 `database/seed.sql`，导入前先说明"这是虚构演示数据" |
| 某一步 500 | 切到 `docs/test/test-report.md`：72 个用例通过、0 失败，说明后端逻辑已验证，问题在环境 |
| 时间只剩 3 分钟 | 只演第 1、4、5、8 幕：发起问诊 → 开方 → 审核 → 核销，正好覆盖最核心的状态机与幂等 |

## 5. 常见提问与回答

| 问题 | 回答要点 |
|---|---|
| 为什么用模块化单体而不是微服务 | 6 人 4 周，处方审核后流转在单体内是本地事务；拆服务会把本地事务变成分布式一致性问题，测试与运维接不住 |
| 处方状态怎么保证不乱 | 8 个状态集中在 `PrescriptionStatus.canApply`，只有白名单迁移放行，非法迁移统一 `RX_STATE_INVALID`；Controller 不能直接改状态字段 |
| 越权怎么防 | 身份只从 JWT 的 `sub` 取，不信任请求体；服务层校验资源归属，越权返回 `SCOPE_DENIED`/`RX_SCOPE_DENIED`；`AuthTest`、`ConsultationTest` 有对应用例 |
| 幂等键解决什么问题 | 药房手指抖两下、网络重试都会重复提交核销；`Idempotency-Key` + `idempotency_record` 唯一键保证只生效一次 |
| 审计为什么没做 | 表结构（含哈希链）已设计，写入未实现，属于已知缺口，写在威胁模型 T-07 与检查表 E-01 里 |
| 继承了什么风险 | 默认 JWT 密钥有兜底值、令牌存 `localStorage`、无限流、无 HTTPS，这四条在 `threat-model.md` 里标为 P0/P1 并给了整改建议 |
| 测试覆盖到什么程度 | 后端 82 例自动化测试通过（认证、问诊、处方状态机、审核、流转、核销幂等、限流与越权）；前端为手工回归，压测与真实 MySQL 未做 |
| 如果真实上线还差什么 | 资质核验、电子签名、TLS 与 Nginx、审计与留存、备份演练、依赖漏洞扫描，见 `release-notes.md` 第 7 节 |

## 6. 演示后

1. 演示过程中如发现与脚本不符的地方，当天更新本文件，别留到答辩前；
2. 版本标签 `v1.0.0` 打在包含本脚本的 main 提交上，标签说明写清提交哈希与日期；
3. 演示用的数据库保留截图（账号列表、处方状态、核销记录），作为实验报告的过程证据。
