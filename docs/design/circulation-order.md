# 流转单与药房接方（R5-A）

> 分工：R5-A 负责流转单创建、列表、详情、接方与拒接；R5-B 负责核销、幂等键与发药记录，见
> `circulation-redeem.md`。两份文档共用同一张 `circulation_order` 表。

## 1 接口

| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| `POST` | `/circulations` | 患者（本人 `APPROVED`）、药师、管理员 | 创建流转单；患者必须传启用中的 `pharmacyId` 且只能操作本人处方；药师用自己绑定的药房；管理员必须传 `pharmacyId` |
| `GET` | `/circulations` | 全角色 | 患者看本人处方、医生看本人开具、药师看本药房、管理员看全量 |
| `GET` | `/circulations/{id}` | 相关方 | 流转单详情，含处方摘要 |
| `POST` | `/circulations/{id}/accept` | 药师 | `CREATED → ACCEPTED` |
| `POST` | `/circulations/{id}/reject` | 药师 | `CREATED → REJECTED`，原因 2～255 字 |
| `POST` | `/circulations/{id}/redeem` | 药师 | R5-B，`ACCEPTED → REDEEMED`，强制 `Idempotency-Key` |

## 2 状态与并发

```text
CREATED ──accept──▶ ACCEPTED ──redeem──▶ REDEEMED
   │
   └──reject──▶ REJECTED
```

- 创建流转单和处方 `APPROVED → CIRCULATED` 在同一个事务里完成；处方更新失败则整笔回滚。
- 同一处方只允许一张流转单，`circulation_order.uk_rx` 兜底；重复创建返回已存在的那张。
- 接方/拒接用 `WHERE id=? AND version=?` 条件更新，版本不一致返回 `CIRC_VERSION_CONFLICT`。
- 药师药房来自 `pharmacist_profile.pharmacy_id`；没有档案或跨药房操作返回 403。
- 患者发起时校验处方归属与药房 `status=1`；重复创建命中已有单时仍按角色复查可见性。

## 3 错误码

| 错误码 | HTTP | 场景 |
|---|---|---|
| `CIRC_NOT_FOUND` | 404 | 流转单不存在 |
| `CIRC_STATE_INVALID` | 409 | 处方未审核通过、重复接方/拒接 |
| `CIRC_VERSION_CONFLICT` | 409 | 并发修改，版本已变 |
| `CIRC_PHARMACY_INVALID` | 400 | 药师未绑定药房、药房不存在/停用，或患者/管理员未指定药房 |
| `CIRC_REJECT_REASON_INVALID` | 422 | 拒接原因少于 2 字或超过 255 字 |
| `RX_SCOPE_DENIED` | 403 | 操作其他药房的流转单 |

## 4 验证

`CirculationOrderApiTest` 覆盖：药师/患者创建、重复创建返回同一张、处方状态回写、跨药房接方 403、
重复接方 409、拒接后不可接方、患者列表可见、未审核处方不可流转、患者只能流转本人处方、
药房不存在返回 400。
`CirculationIdempotencyTest` 继续覆盖 R5-B 的核销重放与并发。
