# cc-crew-duty

航空机组资质和值勤安排管理服务：机组成员档案、值勤组合的资格校验与原子发布、
已发布组合之间的成员原子交换，配套业务号幂等、版本并发控制、资格快照、
审计记录以及成员值勤时间线查询。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1（JPA / WebMVC / Validation / H2，Jackson 3）

迁移项目沿用现有 Spring Boot 版本，其他项目使用上述版本。

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 领域模型

- **机组成员 `CrewMember`**：工号、姓名、岗位（机长/副驾驶/乘务长/乘务员）、
  可执飞机型集合、资质有效截止日（含当日）。
- **值勤组合 `DutyCombo`**：由按时间排列的值勤段 `DutySegment` 组成，
  带乐观锁版本号（`@Version`）；每段冗余保存发布/交换时刻的**成员资格快照**
  （岗位、机型集合、资质截止日），成员档案后续变更不影响当时的合规事实。
- **交换方案 `SwapProposal`**：作用于两个已发布组合，提交时冻结双方版本，
  含若干交换项（哪一方的哪个段确认后改由谁执飞）。
- **审计记录 `AuditRecord`**：发布、方案提交、确认、作废均追加一条只增记录。

## 主要业务规则

1. **岗位覆盖**：每个值勤段必须有一名成员执飞，且成员岗位必须等于该段所需岗位。
2. **机型资质**：成员可执飞机型集合必须包含值勤段机型。
3. **资质有效期**：值勤段出发当日不得晚于成员资质有效截止日（截止日当天仍有效）。
4. **值勤时长上限**：单个组合“首段出发 → 末段到达”的跨度不得超过上限，
   默认 14 小时（`crewduty.rule.max-duty-hours`）。
5. **最低休息**：同一成员任意相邻两段值勤（**含跨组合**的段）之间的间隔不得低于
   最低休息时间，默认 10 小时（`crewduty.rule.min-rest-hours`）；恰好等于下限视为满足。
6. **段时间合法性**：到达必须晚于出发；组合内相邻段不得重叠、必须按出发时间排列。

### 发布

- 上述规则**全部通过**才落库；任一成员不合格则**整组拒绝**（HTTP 422，
  返回全部失败原因），不会产生缺员或部分生效的组合。
- 发布用 `bizNo` 保证幂等：相同业务号 + 相同内容重放返回原组合（201，不重复创建）；
  相同业务号 + 不同内容返回冲突（HTTP 409 `IDEMPOTENT_CONFLICT`）。
  内容差异通过规范化 JSON 的 SHA-256 指纹识别。
- 发布成功后保留成员资格快照，并写入 `COMBO_PUBLISHED` 审计。

### 成员交换

- 先提交方案：校验结构（双方为不同组合、段与目标成员存在、交换项不重复），
  冻结双方组合当前版本；提交时不做资格判断。
- 确认时在**一个事务**内：
  1. 锁定方案行，再按组合 id 升序加悲观写锁（避免并发交换死锁）；
  2. 比对冻结版本：任一组合版本已变化（发生过并发交换或排班变更）→
     旧方案作废，**不得生效**（HTTP 409 `CONCURRENT_MODIFICATION`）；
  3. 构造交换后的双方**完整假设组合**（含未参与交换的段和组合外的段）重新执行
     全部资格规则（岗位/机型/有效期/时长/跨组合休息）；任一不通过 →
     整笔交换拒绝，双方组合均不改变（HTTP 422）；
  4. 全部通过才应用成员替换、刷新资格快照、组合版本递增并提交，写入
     `SWAP_CONFIRMED` 审计；作废时写入 `SWAP_REJECTED` 审计。
- 交换确认同样使用 `bizNo` 幂等：重复确认已完成方案返回原结果，版本不重复递增；
  同号不同内容返回冲突；已作废方案再次确认返回 409 `SWAP_REJECTED`。

### 查询与错误结构

- 组合详情（含版本、资格快照）、组合当前资格失败原因、成员跨组合值勤时间线
  （按出发时间升序）、审计记录。
- 所有错误使用统一结构：

  ```json
  { "code": "QUALIFICATION_FAILED", "message": "...", "details": [ ... ], "traceId": null }
  ```

  主要错误码：`VALIDATION_ERROR(400)`、`NOT_FOUND(404)`、
  `IDEMPOTENT_CONFLICT(409)`、`CONCURRENT_MODIFICATION(409)`、
  `SWAP_REJECTED(409)`、`QUALIFICATION_FAILED(422)`、`INTERNAL_ERROR(500)`。

## HTTP 接口摘要

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/members` | 创建机组成员 |
| GET | `/api/members` / `/api/members/{employeeNo}` | 成员列表/详情 |
| POST | `/api/combos` | 发布值勤组合（`bizNo` 幂等） |
| POST | `/api/combos/precheck` | 发布预检，不落库返回失败原因 |
| GET | `/api/combos/{bizNo}` | 组合详情（版本 + 资格快照） |
| GET | `/api/combos/{bizNo}/qualification` | 按成员当前档案重新校验组合 |
| POST | `/api/swaps` | 提交交换方案（冻结双方版本） |
| POST | `/api/swaps/{bizNo}/confirm` | 确认交换（重新校验，单事务原子完成） |
| GET | `/api/swaps/{bizNo}` | 交换方案详情 |
| GET | `/api/members/{employeeNo}/timeline` | 成员值勤时间线 |
| GET | `/api/audits` | 审计记录列表 |

发布请求示例：

```json
{
  "bizNo": "PUB-20261001-001",
  "segments": [
    {
      "flightNo": "CA101",
      "departureAt": "2026-10-01T08:00:00",
      "arrivalAt": "2026-10-01T10:00:00",
      "aircraftType": "A320",
      "requiredPosition": "CAPTAIN",
      "memberEmployeeNo": "E1001"
    }
  ]
}
```

## 测试

- `QualificationValidatorTest`：五类规则（岗位/机型/有效期/时长/休息，含跨组合
  休息、边界值与多失败原因汇总）。
- `CrewDutyServiceIntegrationTest`：整组原子拒绝、发布/交换幂等重放与内容冲突、
  交换成功后版本与快照、重新校验失败原子回滚、过期方案作废、并发交换至多一个生效、
  时间线与审计。
- `CrewDutyWebApiTest`：REST 端到端，覆盖统一错误结构、422/409/404、完整交换流程。
