package com.chris64233.crewduty.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chris64233.crewduty.domain.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * REST 接口与统一错误结构 {@link ApiError} 的端到端测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
class CrewDutyWebApiTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUpMembers() throws Exception {
        createMember("W-CAP", Position.CAPTAIN, "[\"A320\",\"B737\"]", "2027-12-31");
        createMember("W-FO", Position.FIRST_OFFICER, "[\"A320\"]", "2027-12-31");
        createMember("W-CAP2", Position.CAPTAIN, "[\"A320\"]", "2027-12-31");
        createMember("W-FO2", Position.FIRST_OFFICER, "[\"A320\"]", "2027-12-31");
    }

    private void createMember(String no, Position position, String types, String expires)
            throws Exception {
        String body = """
                {"employeeNo":"%s","name":"n-%s","position":"%s",
                 "qualifiedAircraftTypes":%s,"qualificationExpiresOn":"%s"}"""
                .formatted(no, no, position.name(), types, expires);
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(r -> {
                    // 并发/重跑场景下允许已存在；正常应为 201
                    int status = r.getResponse().getStatus();
                    if (status != 201 && status != 409) {
                        throw new AssertionError("unexpected status " + status);
                    }
                });
    }

    private String publishBody(String bizNo, String memberNo, Position required, String aircraft) {
        return """
                {"bizNo":"%s","segments":[
                  {"flightNo":"WA1","departureAt":"2026-10-01T08:00:00",
                   "arrivalAt":"2026-10-01T10:00:00","aircraftType":"%s",
                   "requiredPosition":"%s","memberEmployeeNo":"%s"}]}"""
                .formatted(bizNo, aircraft, required.name(), memberNo);
    }

    @Test
    void validationErrorUsesUnifiedStructure() throws Exception {
        // 缺少必填字段 bizNo
        mockMvc.perform(post("/api/combos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"segments":[{"flightNo":"WA1",
                                 "departureAt":"2026-10-01T08:00:00",
                                 "arrivalAt":"2026-10-01T10:00:00",
                                 "aircraftType":"A320",
                                 "requiredPosition":"CAPTAIN","memberEmployeeNo":"W-CAP"}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.details[0].field").exists());
    }

    @Test
    void qualificationFailureReturns422WithReasons() throws Exception {
        // 用乘务岗位要求执飞机长岗位 → POSITION_MISMATCH
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeNo":"W-PUR","name":"p","position":"PURSER",
                                 "qualifiedAircraftTypes":["A320"],
                                 "qualificationExpiresOn":"2027-12-31"}"""))
                .andExpect(r -> {
                });

        mockMvc.perform(post("/api/combos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishBody("W-BAD", "W-PUR", Position.CAPTAIN, "A320")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("QUALIFICATION_FAILED"))
                .andExpect(jsonPath("$.details[0].rule").value("POSITION_MISMATCH"))
                .andExpect(jsonPath("$.details[0].memberEmployeeNo").value("W-PUR"));
    }

    @Test
    void publishThenReplaySameAndConflict() throws Exception {
        mockMvc.perform(post("/api/combos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishBody("W-IDEM", "W-CAP", Position.CAPTAIN, "A320")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bizNo").value("W-IDEM"))
                .andExpect(jsonPath("$.version").value(0));

        // 相同内容重放 → 200，返回原结果
        mockMvc.perform(post("/api/combos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishBody("W-IDEM", "W-CAP", Position.CAPTAIN, "A320")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bizNo").value("W-IDEM"));

        // 同号不同内容 → 409 冲突
        mockMvc.perform(post("/api/combos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishBody("W-IDEM", "W-CAP2", Position.CAPTAIN, "A320")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENT_CONFLICT"));
    }

    @Test
    void unknownComboReturns404UnifiedError() throws Exception {
        mockMvc.perform(get("/api/combos/NO-SUCH-COMBO"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void fullSwapFlowOverHttp() throws Exception {
        // 发布 A、B（不同日期，互不冲突）
        mockMvc.perform(post("/api/combos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bizNo":"W-COMBO-A","segments":[
                                  {"flightNo":"WA1","departureAt":"2026-11-01T08:00:00",
                                   "arrivalAt":"2026-11-01T10:00:00","aircraftType":"A320",
                                   "requiredPosition":"CAPTAIN","memberEmployeeNo":"W-CAP"}]}"""))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/combos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bizNo":"W-COMBO-B","segments":[
                                  {"flightNo":"WB1","departureAt":"2026-11-02T08:00:00",
                                   "arrivalAt":"2026-11-02T10:00:00","aircraftType":"A320",
                                   "requiredPosition":"CAPTAIN","memberEmployeeNo":"W-CAP2"}]}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/swaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bizNo":"W-SWAP-1","comboABizNo":"W-COMBO-A",
                                 "comboBBizNo":"W-COMBO-B","items":[
                                  {"side":"A","segmentSeqNo":0,"targetEmployeeNo":"W-CAP2"},
                                  {"side":"B","segmentSeqNo":0,"targetEmployeeNo":"W-CAP"}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROPOSED"));

        mockMvc.perform(post("/api/swaps/W-SWAP-1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.resultingComboAVersion").value(1));

        mockMvc.perform(get("/api/combos/W-COMBO-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.segments[0].memberEmployeeNo").value("W-CAP2"));

        // 时间线
        mockMvc.perform(get("/api/members/W-CAP2/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeNo").value("W-CAP2"))
                .andExpect(jsonPath("$.entries[0].flightNo").value("WA1"));
    }

    @Test
    void malformedJsonReturnsUnifiedValidationError() throws Exception {
        mockMvc.perform(post("/api/combos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
