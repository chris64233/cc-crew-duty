package com.chris64233.crewduty;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 值勤组合发布、资格校验、幂等和成员交换的端到端测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
class CrewDutyApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    // ---------- 发布：成功路径 ----------

    @Test
    void publishSuccessStoresSnapshotVersionAndAudit() throws Exception {
        long captain = createMember("E1001", "CAPTAIN", "B737", "2026-01-01", "2026-12-31");
        long pairing = createPairing("PA-001", captain, "CAPTAIN",
                segment(1, "CA100", "B737", "CAPTAIN", "2026-10-01T08:00:00", "2026-10-01T12:00:00"));

        mockMvc.perform(post("/api/pairings/{id}/publish", pairing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idemKey\":\"pub-ok-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.assignments[0].qualificationSnapshot").isNotEmpty());

        String snapshot = mockMvc.perform(get("/api/pairings/{id}", pairing))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.read(snapshot, "$.assignments[0].qualificationSnapshot").toString())
                .contains("B737").contains("E1001");

        mockMvc.perform(get("/api/pairings/{id}/audits", pairing))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("PUBLISH"));
    }

    // ---------- 发布：四类资格失败 ----------

    @Test
    void publishFailsWhenPositionNotCovered() throws Exception {
        long officer = createMember("E1002", "FIRST_OFFICER", "B737", "2026-01-01", "2026-12-31");
        long pairing = createPairing("PA-002", officer, "FIRST_OFFICER",
                segment(1, "CA101", "B737", "CAPTAIN", "2026-10-01T08:00:00", "2026-10-01T12:00:00"));

        mockMvc.perform(post("/api/pairings/{id}/publish", pairing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idemKey\":\"pub-fail-pos\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QUALIFICATION_FAILED"))
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("POSITION_NOT_COVERED")));

        // 整组拒绝：组合保持草稿，不产生部分生效
        mockMvc.perform(get("/api/pairings/{id}", pairing))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(get("/api/pairings/{id}/qualification-failures", pairing))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rule").value("POSITION_NOT_COVERED"));
    }

    @Test
    void publishFailsWhenQualificationExpired() throws Exception {
        long captain = createMember("E1003", "CAPTAIN", "B737", "2026-01-01", "2026-09-30");
        long pairing = createPairing("PA-003", captain, "CAPTAIN",
                segment(1, "CA102", "B737", "CAPTAIN", "2026-10-01T08:00:00", "2026-10-01T12:00:00"));

        mockMvc.perform(post("/api/pairings/{id}/publish", pairing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idemKey\":\"pub-fail-exp\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QUALIFICATION_FAILED"))
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("AIRCRAFT_NOT_QUALIFIED")));
    }

    @Test
    void publishFailsWhenDutyTimeExceeded() throws Exception {
        long captain = createMember("E1004", "CAPTAIN", "B737", "2026-01-01", "2026-12-31");
        long pairing = createPairing("PA-004", captain, "CAPTAIN",
                segment(1, "CA103", "B737", "CAPTAIN", "2026-10-01T06:00:00", "2026-10-01T12:00:00"),
                segment(2, "CA104", "B737", "CAPTAIN", "2026-10-01T14:00:00", "2026-10-01T23:00:00"));

        mockMvc.perform(post("/api/pairings/{id}/publish", pairing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idemKey\":\"pub-fail-duty\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("DUTY_TIME_EXCEEDED")));
    }

    @Test
    void publishFailsWhenRestIsInsufficient() throws Exception {
        long captain = createMember("E1005", "CAPTAIN", "B737", "2026-01-01", "2026-12-31");
        long first = createPairing("PA-005", captain, "CAPTAIN",
                segment(1, "CA105", "B737", "CAPTAIN", "2026-10-01T08:00:00", "2026-10-01T12:00:00"));
        publishOk(first, "pub-rest-first");

        // 与上一组合间隔仅 6 小时，低于最低休息 10 小时
        long second = createPairing("PA-006", captain, "CAPTAIN",
                segment(1, "CA106", "B737", "CAPTAIN", "2026-10-01T18:00:00", "2026-10-01T22:00:00"));
        mockMvc.perform(post("/api/pairings/{id}/publish", second)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idemKey\":\"pub-rest-second\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("INSUFFICIENT_REST")));

        // 间隔 20 小时则满足要求
        long third = createPairing("PA-007", captain, "CAPTAIN",
                segment(1, "CA107", "B737", "CAPTAIN", "2026-10-02T08:00:00", "2026-10-02T12:00:00"));
        publishOk(third, "pub-rest-third");
    }

    // ---------- 幂等 ----------

    @Test
    void publishIsIdempotentAndConflictsOnDifferentContent() throws Exception {
        long captain = createMember("E1006", "CAPTAIN", "B737", "2026-01-01", "2026-12-31");
        long pairing = createPairing("PA-008", captain, "CAPTAIN",
                segment(1, "CA108", "B737", "CAPTAIN", "2026-11-01T08:00:00", "2026-11-01T12:00:00"));

        String first = publishOk(pairing, "pub-idem-1");
        String replay = publishOk(pairing, "pub-idem-1");
        assertThat(replay).isEqualTo(first);

        // 重放不产生重复审计
        String audits = mockMvc.perform(get("/api/pairings/{id}/audits", pairing))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.read(audits, "$.length()").toString()).isEqualTo("1");

        // 相同业务号、不同内容 → 409
        long other = createPairing("PA-009", captain, "CAPTAIN",
                segment(1, "CA109", "B737", "CAPTAIN", "2026-11-05T08:00:00", "2026-11-05T12:00:00"));
        mockMvc.perform(post("/api/pairings/{id}/publish", other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idemKey\":\"pub-idem-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void failedPublishIsAlsoReplayed() throws Exception {
        long officer = createMember("E1007", "FIRST_OFFICER", "B737", "2026-01-01", "2026-12-31");
        long pairing = createPairing("PA-010", officer, "FIRST_OFFICER",
                segment(1, "CA110", "B737", "CAPTAIN", "2026-11-02T08:00:00", "2026-11-02T12:00:00"));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/pairings/{id}/publish", pairing)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"idemKey\":\"pub-idem-fail\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("QUALIFICATION_FAILED"));
        }
    }

    // ---------- 交换 ----------

    @Test
    void swapConfirmExchangesMembersAtomically() throws Exception {
        long c1 = createMember("E2001", "CAPTAIN", "B737", "2026-01-01", "2026-12-31");
        long c2 = createMember("E2002", "CAPTAIN", "B737", "2026-01-01", "2026-12-31");
        long pairingA = createPairing("PA-101", c1, "CAPTAIN",
                segment(1, "CA201", "B737", "CAPTAIN", "2026-12-01T08:00:00", "2026-12-01T12:00:00"));
        long pairingB = createPairing("PA-102", c2, "CAPTAIN",
                segment(1, "CA202", "B737", "CAPTAIN", "2026-12-02T08:00:00", "2026-12-02T12:00:00"));
        publishOk(pairingA, "pub-swap-a");
        publishOk(pairingB, "pub-swap-b");

        long proposal = submitSwap(pairingA, pairingB, c1, c2, "swap-ok-1");

        mockMvc.perform(post("/api/swaps/{id}/confirm", proposal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idemKey\":\"swap-ok-1-confirm\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // 成员已对调，版本已递增
        mockMvc.perform(get("/api/pairings/{id}", pairingA))
                .andExpect(jsonPath("$.assignments[0].memberId").value(c2))
                .andExpect(jsonPath("$.version").value(2));
        mockMvc.perform(get("/api/pairings/{id}", pairingB))
                .andExpect(jsonPath("$.assignments[0].memberId").value(c1))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(get("/api/pairings/{id}/audits", pairingA))
                .andExpect(jsonPath("$[?(@.action == 'SWAP_CONFIRMED')]").isNotEmpty());
    }

    @Test
    void swapConfirmFailsWhenVersionChanged() throws Exception {
        long c1 = createMember("E2003", "CAPTAIN", "B737", "2026-01-01", "2026-12-31");
        long c2 = createMember("E2004", "CAPTAIN", "B737", "2026-01-01", "2026-12-31");
        long pairingA = createPairing("PA-103", c1, "CAPTAIN",
                segment(1, "CA203", "B737", "CAPTAIN", "2026-12-05T08:00:00", "2026-12-05T12:00:00"));
        long pairingB = createPairing("PA-104", c2, "CAPTAIN",
                segment(1, "CA204", "B737", "CAPTAIN", "2026-12-06T08:00:00", "2026-12-06T12:00:00"));
        publishOk(pairingA, "pub-stale-a");
        publishOk(pairingB, "pub-stale-b");

        long proposal1 = submitSwap(pairingA, pairingB, c1, c2, "swap-stale-1");
        long proposal2 = submitSwap(pairingA, pairingB, c1, c2, "swap-stale-2");

        // 第一个方案确认后组合版本变化，第二个（旧）方案不得生效
        mockMvc.perform(post("/api/swaps/{id}/confirm", proposal1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idemKey\":\"swap-stale-1-confirm\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/swaps/{id}/confirm", proposal2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idemKey\":\"swap-stale-2-confirm\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SWAP_STALE"));

        mockMvc.perform(get("/api/swaps/{id}", proposal2))
                .andExpect(jsonPath("$.status").value("STALE"));
    }

    @Test
    void swapConfirmRevalidatesBothPairings() throws Exception {
        long c1 = createMember("E2005", "CAPTAIN", "B737", "2026-01-01", "2026-12-31");
        long c3 = createMember("E2006", "CAPTAIN", "A320", "2026-01-01", "2026-12-31");
        long pairingA = createPairing("PA-105", c1, "CAPTAIN",
                segment(1, "CA205", "B737", "CAPTAIN", "2026-12-10T08:00:00", "2026-12-10T12:00:00"));
        long pairingB = createPairing("PA-106", c3, "CAPTAIN",
                segment(1, "CA206", "A320", "CAPTAIN", "2026-12-11T08:00:00", "2026-12-11T12:00:00"));
        publishOk(pairingA, "pub-reval-a");
        publishOk(pairingB, "pub-reval-b");

        // c3 无 B737 资质，交换后组合 A 不合格 → 整笔交换拒绝
        long proposal = submitSwap(pairingA, pairingB, c1, c3, "swap-reval-1");
        mockMvc.perform(post("/api/swaps/{id}/confirm", proposal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idemKey\":\"swap-reval-1-confirm\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SWAP_QUALIFICATION_FAILED"));

        // 双方组合保持不变，方案标记为 REJECTED
        mockMvc.perform(get("/api/pairings/{id}", pairingA))
                .andExpect(jsonPath("$.assignments[0].memberId").value(c1))
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(get("/api/pairings/{id}", pairingB))
                .andExpect(jsonPath("$.assignments[0].memberId").value(c3))
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(get("/api/swaps/{id}", proposal))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.failureReason").isNotEmpty());
    }

    // ---------- 查询与错误结构 ----------

    @Test
    void memberTimelineIsOrderedByDutyStart() throws Exception {
        long captain = createMember("E3001", "CAPTAIN", "B737", "2026-01-01", "2026-12-31");
        long later = createPairing("PA-201", captain, "CAPTAIN",
                segment(1, "CA301", "B737", "CAPTAIN", "2026-10-10T08:00:00", "2026-10-10T12:00:00"));
        long earlier = createPairing("PA-202", captain, "CAPTAIN",
                segment(1, "CA302", "B737", "CAPTAIN", "2026-10-05T08:00:00", "2026-10-05T12:00:00"));
        publishOk(later, "pub-tl-1");
        publishOk(earlier, "pub-tl-2");

        String timeline = mockMvc.perform(get("/api/members/{id}/timeline", captain))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        List<String> pairingNos = JsonPath.read(timeline, "$[*].pairingNo");
        assertThat(pairingNos).containsExactly("PA-202", "PA-201");
    }

    @Test
    void errorsUseUnifiedStructure() throws Exception {
        mockMvc.perform(get("/api/pairings/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        mockMvc.perform(post("/api/pairings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pairingNo\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ---------- 测试辅助 ----------

    private long createMember(String employeeNo, String position, String aircraftType,
                              String validFrom, String validTo) throws Exception {
        Map<String, Object> body = Map.of(
                "employeeNo", employeeNo,
                "name", "测试" + employeeNo,
                "position", position,
                "qualifications", List.of(Map.of(
                        "aircraftType", aircraftType,
                        "validFrom", validFrom,
                        "validTo", validTo)));
        MvcResult result = mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private Map<String, Object> segment(int seq, String flightNo, String aircraftType,
                                        String requiredPosition, String dep, String arr) {
        return Map.of(
                "sequenceNo", seq,
                "flightNo", flightNo,
                "aircraftType", aircraftType,
                "requiredPosition", requiredPosition,
                "depTime", dep,
                "arrTime", arr);
    }

    @SafeVarargs
    private final long createPairing(String pairingNo, long memberId, String position,
                                     Map<String, Object>... segments) throws Exception {
        Map<String, Object> body = Map.of(
                "pairingNo", pairingNo,
                "segments", List.of(segments),
                "assignments", List.of(Map.of("memberId", memberId, "position", position)));
        MvcResult result = mockMvc.perform(post("/api/pairings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String publishOk(long pairingId, String idemKey) throws Exception {
        return mockMvc.perform(post("/api/pairings/{id}/publish", pairingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idemKey\":\"" + idemKey + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andReturn().getResponse().getContentAsString();
    }

    private long submitSwap(long pairingA, long pairingB, long memberA, long memberB,
                            String idemKey) throws Exception {
        Map<String, Object> body = Map.of(
                "idemKey", idemKey,
                "pairingAId", pairingA,
                "pairingBId", pairingB,
                "memberAId", memberA,
                "memberBId", memberB);
        MvcResult result = mockMvc.perform(post("/api/swaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.proposalNo").isNotEmpty())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }
}
