# cc-crew-duty

航空机组资质和值勤安排管理服务。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1（Spring Framework 7 / Hibernate 7 / Jackson 3）

迁移项目沿用现有 Spring Boot 版本，其他项目使用上述版本。

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 领域模型

- **机组成员（CrewMember）**：记录岗位（CAPTAIN / FIRST_OFFICER / PURSER / FLIGHT_ATTENDANT）、
  可执飞机型资质（CrewQualification：机型 + 有效期起止）。
- **值勤段（DutySegment）**：记录航班号、起飞/到达时间、机型和所需岗位。
- **值勤组合（DutyPairing）**：按时间排列的多个值勤段 + 对应成员指派（PairingAssignment），
  组合内所有成员共同执行全部值勤段。组合携带乐观锁版本号。
- **交换方案（SwapProposal）**：两个已发布组合之间对调两名成员，提交时冻结双方组合版本。
- **审计记录（AuditRecord）**：发布、发布拒绝、交换提交/确认/拒绝/失效等每次调整均留痕。
- **幂等记录（IdempotencyRecord）**：业务号 + 操作类型 + 请求指纹 + 原始响应。

## 主要业务规则

### 发布组合（POST /api/pairings/{id}/publish）

发布时对**完整组合**执行全部资格校验，任何成员不合格都**整组拒绝**，
组合保持 DRAFT，不会形成缺员或部分生效的组合：

1. **岗位覆盖**：每个值勤段的所需岗位至少有一名该岗位的成员；指派岗位与成员实际岗位必须一致。
2. **机型资质**：每位成员对组合内每个值勤段的机型，在值勤当日都持有有效期内的资质。
3. **值勤时长上限**：首段起飞至末段到达的总时长 ≤ `crewduty.max-duty-hours`（默认 14 小时）。
4. **最低休息**：成员与本组合相邻的其他**已发布**组合之间的休息间隔
   ≥ `crewduty.min-rest-hours`（默认 10 小时）；值勤窗口重叠视为休息不足。

发布成功时：组合状态置为 PUBLISHED、版本号递增，并为每条指派保存**成员资格快照**
（岗位 + 机型资质清单的 JSON），供事后追溯。

### 成员交换（POST /api/swaps，POST /api/swaps/{id}/confirm）

- 提交方案时双方组合必须均已发布，且两名成员当前分别属于两个组合；
  方案记录双方组合当时的版本号。
- 确认交换时：
  1. **版本校验**：任一组合版本与方案冻结版本不一致（并发交换或排班变更），
     方案置为 STALE，旧方案不得生效；
  2. **重新校验**：按交换后的成员构成对**双方完整组合**重新执行全部资格规则，
     任一方不合格则整笔交换拒绝（方案置为 REJECTED），双方组合均不变更；
  3. **原子生效**：校验通过后在**一次事务**中完成成员对调、资格快照刷新、
     双方组合版本递增（OPTIMISTIC_FORCE_INCREMENT）和审计写入。
- 组合版本的乐观锁同时兜底并发确认：版本冲突返回 409 `VERSION_CONFLICT`。

### 幂等

发布、交换提交、交换确认均要求调用方提供业务号（`idemKey`）：

- 相同业务号 + 相同内容重放 → 返回**原结果**（含失败响应，失败同样被记录并重放）；
- 相同业务号 + 不同内容 → 409 `IDEMPOTENCY_CONFLICT`。

### 查询

- `GET /api/pairings/{id}` — 组合详情（含版本、成员、资格快照）；
- `GET /api/pairings/{id}/qualification-failures` — 当前资格失败原因（预检，不改变状态）；
- `GET /api/pairings/{id}/audits` — 组合审计记录；
- `GET /api/members/{id}/timeline` — 成员值勤时间线（按值勤开始时间排序）。

### 统一错误结构

所有错误（参数校验、业务冲突、资格失败、乐观锁冲突）统一返回：

```json
{
  "code": "QUALIFICATION_FAILED",
  "message": "组合 PA-001 资格校验未通过，整组拒绝发布",
  "details": ["POSITION_NOT_COVERED: 值勤段 CA100 所需岗位 CAPTAIN 无成员覆盖"],
  "timestamp": "2026-09-27T06:00:00Z"
}
```

主要错误码：`NOT_FOUND`、`VALIDATION_FAILED`、`QUALIFICATION_FAILED`、
`SWAP_QUALIFICATION_FAILED`、`SWAP_STALE`、`IDEMPOTENCY_CONFLICT`、
`VERSION_CONFLICT`、`PAIRING_ALREADY_PUBLISHED`。

## 自动化测试

`CrewDutyApiIntegrationTests` 覆盖：发布成功（快照/版本/审计）、四类资格失败整组拒绝、
发布幂等重放与冲突、失败重放、交换原子生效、版本变化导致方案失效、
交换确认时重新校验失败双方不变、成员时间线排序、统一错误结构。
