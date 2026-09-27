package com.chris64233.crewduty.service;

import static com.chris64233.crewduty.TestFixtures.member;
import static com.chris64233.crewduty.TestFixtures.publish;
import static com.chris64233.crewduty.TestFixtures.segment;
import static com.chris64233.crewduty.TestFixtures.swap;
import static com.chris64233.crewduty.TestFixtures.swapItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chris64233.crewduty.domain.AuditAction;
import com.chris64233.crewduty.domain.Position;
import com.chris64233.crewduty.repository.AuditRecordRepository;
import com.chris64233.crewduty.repository.CrewMemberRepository;
import com.chris64233.crewduty.repository.DutyComboRepository;
import com.chris64233.crewduty.repository.SwapProposalRepository;
import com.chris64233.crewduty.service.CrewMemberService;
import com.chris64233.crewduty.service.CrewDutyService;
import com.chris64233.crewduty.web.ApiException;
import com.chris64233.crewduty.web.ErrorCode;
import com.chris64233.crewduty.web.dto.ComboDetailResponse;
import com.chris64233.crewduty.web.dto.PublishComboRequest;
import com.chris64233.crewduty.web.dto.SwapProposalRequest;
import com.chris64233.crewduty.web.dto.SwapProposalResponse;
import com.chris64233.crewduty.web.dto.TimelineEntry;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 发布、幂等、交换原子性、版本失效、时间线与审计的服务级集成测试。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:svcdb;DB_CLOSE_DELAY=-1")
class CrewDutyServiceIntegrationTest {

    @Autowired
    private CrewMemberService memberService;
    @Autowired
    private CrewDutyService dutyService;
    @Autowired
    private DutyComboRepository comboRepository;
    @Autowired
    private SwapProposalRepository proposalRepository;
    @Autowired
    private CrewMemberRepository memberRepository;
    @Autowired
    private AuditRecordRepository auditRecordRepository;

    private static final LocalDateTime D1 = LocalDateTime.of(2026, 10, 1, 8, 0);

    @BeforeEach
    void setUp() {
        // 成员为全测试共享的基础数据，幂等创建（不同测试方法间库不重置）
        createIfAbsent("CAP1", Position.CAPTAIN, "A320", "B737");
        createIfAbsent("CAP2", Position.CAPTAIN, "A320");
        createIfAbsent("FO1", Position.FIRST_OFFICER, "A320", "B737");
        createIfAbsent("FO2", Position.FIRST_OFFICER, "A320");
        createIfAbsent("PUR1", Position.PURSER, "A320");
        createIfAbsent("CAP-EXP", Position.CAPTAIN, "A320");

        // 成员档案跨方法复用，但组合/方案/审计每个测试方法从零开始，
        // 避免 H2 内存库中历史组合触发跨组合休息校验、污染断言
        proposalRepository.deleteAll();
        comboRepository.deleteAll();
        auditRecordRepository.deleteAll();
        proposalRepository.flush();
        comboRepository.flush();
        auditRecordRepository.flush();
    }

    private void createIfAbsent(String no, Position position, String... types) {
        if (memberRepository.existsByEmployeeNo(no)) {
            return;
        }
        LocalDate expiresOn = "CAP-EXP".equals(no)
                ? LocalDate.of(2026, 9, 30)
                : LocalDate.of(2027, 12, 31);
        memberService.create(member(no, "姓名-" + no, position, List.of(types), expiresOn));
    }

    @Test
    void validComboPublishesWithSnapshotsAndAudit() {
        ComboDetailResponse combo = dutyService.publish(publish("BIZ-P-1", List.of(
                segment("CA101", D1, D1.plusHours(2), "A320", Position.CAPTAIN, "CAP1"),
                segment("CA102", D1.plusHours(12), D1.plusHours(14), "A320",
                        Position.FIRST_OFFICER, "FO1"))));

        assertThat(combo.version()).isZero();
        assertThat(combo.segments()).hasSize(2);
        ComboDetailResponse.SegmentView first = combo.segments().getFirst();
        assertThat(first.snapshotPosition()).isEqualTo("CAPTAIN");
        assertThat(first.snapshotAircraftTypes()).containsExactlyInAnyOrder("A320", "B737");
        assertThat(first.snapshotQualificationExpiresOn()).isEqualTo(LocalDate.of(2027, 12, 31));

        var audits = dutyService.audits();
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst().action()).isEqualTo(AuditAction.COMBO_PUBLISHED);
        assertThat(audits.getFirst().bizNo()).isEqualTo("BIZ-P-1");
        assertThat(audits.getFirst().detail()).contains("CAP1");
    }

    @Test
    void unqualifiedMemberRejectsWholeComboAtomically() {
        PublishComboRequest request = publish("BIZ-P-BAD", List.of(
                segment("CA201", D1, D1.plusHours(2), "A320", Position.CAPTAIN, "CAP1"),
                // 乘务长不能执飞副驾驶岗位
                segment("CA202", D1.plusHours(3), D1.plusHours(5), "A320",
                        Position.FIRST_OFFICER, "PUR1")));

        assertThatThrownBy(() -> dutyService.publish(request))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.QUALIFICATION_FAILED);
                    assertThat(ex.getDetails()).isNotEmpty();
                });

        // 整组不落库
        assertThat(comboRepository.findByBizNo("BIZ-P-BAD")).isEmpty();
    }

    @Test
    void precheckReturnsFailureReasonsWithoutPersisting() {
        QualificationReport report = dutyService.precheckPublish(publish("BIZ-PRE", List.of(
                segment("CA301", D1, D1.plusHours(2), "A350", Position.CAPTAIN, "CAP1"))));

        assertThat(report.valid()).isFalse();
        assertThat(report.failures()).extracting(FailureReason::rule)
                .contains(FailureRule.AIRCRAFT_NOT_QUALIFIED);
        assertThat(comboRepository.findByBizNo("BIZ-PRE")).isEmpty();
    }

    @Test
    void idempotentReplayReturnsOriginalResult() {
        var first = dutyService.publish(publish("BIZ-IDEM", List.of(
                segment("CA401", D1, D1.plusHours(2), "A320", Position.CAPTAIN, "CAP1"))));
        var second = dutyService.publish(publish("BIZ-IDEM", List.of(
                segment("CA401", D1, D1.plusHours(2), "A320", Position.CAPTAIN, "CAP1"))));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.version()).isZero();
        assertThat(comboRepository.count()).isEqualTo(1);
    }

    @Test
    void sameBizNoDifferentContentConflicts() {
        dutyService.publish(publish("BIZ-CONF", List.of(
                segment("CA501", D1, D1.plusHours(2), "A320", Position.CAPTAIN, "CAP1"))));

        assertThatThrownBy(() -> dutyService.publish(publish("BIZ-CONF", List.of(
                segment("CA501", D1, D1.plusHours(2), "A320", Position.CAPTAIN, "CAP2")))))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.IDEMPOTENT_CONFLICT));
    }

    @Test
    void successfulSwapReplacesMembersAndBumpsVersionsAtomically() {
        ComboDetailResponse a = publishTwoSegmentCombo("BIZ-A", "CAP1", "FO1");
        ComboDetailResponse b = publishTwoSegmentCombo("BIZ-B", "CAP2", "FO2");

        SwapProposalResponse proposal = dutyService.proposeSwap(swap("BIZ-SWAP-1",
                "BIZ-A", "BIZ-B",
                List.of(swapItem("A", 0, "CAP2"), swapItem("B", 0, "CAP1"))));
        assertThat(proposal.status()).isEqualTo("PROPOSED");
        assertThat(proposal.comboAVersion()).isZero();

        SwapProposalResponse confirmed = dutyService.confirmSwap("BIZ-SWAP-1");

        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        ComboDetailResponse updatedA = dutyService.getCombo("BIZ-A");
        ComboDetailResponse updatedB = dutyService.getCombo("BIZ-B");
        assertThat(updatedA.version()).isEqualTo(1);
        assertThat(updatedB.version()).isEqualTo(1);
        assertThat(updatedA.segments().getFirst().memberEmployeeNo()).isEqualTo("CAP2");
        assertThat(updatedB.segments().getFirst().memberEmployeeNo()).isEqualTo("CAP1");
        // 资格快照随新成员刷新
        assertThat(updatedA.segments().getFirst().snapshotAircraftTypes())
                .containsExactly("A320");
        assertThat(proposalRepository.findByBizNo("BIZ-SWAP-1").orElseThrow().getResultingComboAVersion())
                .isEqualTo(1);

        var audits = dutyService.audits();
        assertThat(audits).extracting(ar -> ar.action())
                .contains(AuditAction.SWAP_PROPOSED, AuditAction.SWAP_CONFIRMED);
        assertThat(audits.getLast().detail()).contains("CAP2").contains("CAP1");
    }

    @Test
    void swapConfirmIsIdempotent() {
        // A 在 10 日、B 在 11 日，机长双向互换后休息仍充足
        dutyService.publish(publish("BIZ-A2", List.of(
                segment("CA-A2-1", LocalDateTime.of(2026, 10, 10, 8, 0),
                        LocalDateTime.of(2026, 10, 10, 10, 0),
                        "A320", Position.CAPTAIN, "CAP1"),
                segment("CA-A2-2", LocalDateTime.of(2026, 10, 10, 20, 0),
                        LocalDateTime.of(2026, 10, 10, 21, 0),
                        "A320", Position.FIRST_OFFICER, "FO1"))));
        dutyService.publish(publish("BIZ-B2", List.of(
                segment("CA-B2-1", LocalDateTime.of(2026, 10, 11, 8, 0),
                        LocalDateTime.of(2026, 10, 11, 10, 0),
                        "A320", Position.CAPTAIN, "CAP2"),
                segment("CA-B2-2", LocalDateTime.of(2026, 10, 11, 20, 0),
                        LocalDateTime.of(2026, 10, 11, 21, 0),
                        "A320", Position.FIRST_OFFICER, "FO2"))));
        dutyService.proposeSwap(swap("BIZ-SWAP-IDEM", "BIZ-A2", "BIZ-B2",
                List.of(swapItem("A", 0, "CAP2"), swapItem("B", 0, "CAP1"))));

        var first = dutyService.confirmSwap("BIZ-SWAP-IDEM");
        var replay = dutyService.confirmSwap("BIZ-SWAP-IDEM");

        assertThat(replay.status()).isEqualTo("CONFIRMED");
        assertThat(replay.id()).isEqualTo(first.id());
        // 版本不因重放重复递增
        assertThat(dutyService.getCombo("BIZ-A2").version()).isEqualTo(1);
    }

    @Test
    void staleProposalDoesNotTakeEffectAfterComboChanged() {
        publishTwoSegmentCombo("BIZ-A3", "CAP1", "FO1");
        publishTwoSegmentCombo("BIZ-B3", "CAP2", "FO2");

        // 旧方案基于版本 0
        dutyService.proposeSwap(swap("BIZ-SWAP-OLD", "BIZ-A3", "BIZ-B3",
                List.of(swapItem("A", 0, "CAP2"))));

        // 期间 BIZ-A3 被另一笔交换改动，版本递增到 1
        dutyService.proposeSwap(swap("BIZ-SWAP-NEWER", "BIZ-A3", "BIZ-B3",
                List.of(swapItem("A", 0, "CAP2"), swapItem("B", 0, "CAP1"))));
        dutyService.confirmSwap("BIZ-SWAP-NEWER");

        // 旧方案确认 -> 版本不匹配，作废且不生效
        assertThatThrownBy(() -> dutyService.confirmSwap("BIZ-SWAP-OLD"))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));

        assertThat(proposalRepository.findByBizNo("BIZ-SWAP-OLD").orElseThrow().getStatus().name())
                .isEqualTo("REJECTED");
        // 再次确认同一已作废方案被拒绝
        assertThatThrownBy(() -> dutyService.confirmSwap("BIZ-SWAP-OLD"))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.SWAP_REJECTED));
        var audits = dutyService.audits();
        assertThat(audits).extracting(a -> a.action()).contains(AuditAction.SWAP_REJECTED);
    }

    @Test
    void swapFailingRevalidationIsRejectedAtomically() {
        // A、B 原本各自合法：CAP1 执飞 A 早段，CAP2 执飞 B 下午段
        dutyService.publish(publish("BIZ-A4", List.of(
                segment("CA-A41", LocalDateTime.of(2026, 11, 1, 8, 0),
                        LocalDateTime.of(2026, 11, 1, 9, 0),
                        "A320", Position.CAPTAIN, "CAP1"))));
        dutyService.publish(publish("BIZ-B4", List.of(
                segment("CA-B41", LocalDateTime.of(2026, 11, 1, 15, 0),
                        LocalDateTime.of(2026, 11, 1, 16, 0),
                        "A320", Position.CAPTAIN, "CAP2"))));

        // 把 A 早段换给 CAP2：CAP2 09:00 到达后 15:00 又出发，只休息 6h
        dutyService.proposeSwap(swap("BIZ-SWAP-BAD", "BIZ-A4", "BIZ-B4",
                List.of(swapItem("A", 0, "CAP2"))));

        assertThatThrownBy(() -> dutyService.confirmSwap("BIZ-SWAP-BAD"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.QUALIFICATION_FAILED);
                    assertThat(ex.getDetails()).isNotEmpty();
                });

        // 双方组合均未改变
        assertThat(dutyService.getCombo("BIZ-A4").version()).isZero();
        assertThat(dutyService.getCombo("BIZ-B4").version()).isZero();
        assertThat(dutyService.getCombo("BIZ-A4").segments().getFirst().memberEmployeeNo())
                .isEqualTo("CAP1");
        assertThat(proposalRepository.findByBizNo("BIZ-SWAP-BAD").orElseThrow().getStatus())
                .isEqualTo(com.chris64233.crewduty.domain.SwapStatus.REJECTED);
    }

    @Test
    void timelineAggregatesAcrossCombosInChronologicalOrder() {
        dutyService.publish(publish("BIZ-T1", List.of(
                segment("CA-T1", LocalDateTime.of(2026, 12, 2, 9, 0),
                        LocalDateTime.of(2026, 12, 2, 11, 0),
                        "A320", Position.CAPTAIN, "CAP1"))));
        dutyService.publish(publish("BIZ-T2", List.of(
                segment("CA-T2", LocalDateTime.of(2026, 12, 1, 9, 0),
                        LocalDateTime.of(2026, 12, 1, 11, 0),
                        "A320", Position.CAPTAIN, "CAP1"))));

        TimelineEntry.TimelineResponse timeline = dutyService.timeline("CAP1");

        assertThat(timeline.entries()).hasSize(2);
        assertThat(timeline.entries()).extracting(TimelineEntry::flightNo)
                .containsExactly("CA-T2", "CA-T1");
        assertThat(timeline.entries().getFirst().comboBizNo()).isEqualTo("BIZ-T2");
    }

    @Test
    void crossComboRestViolationRejectsPublish() {
        // CAP1 已在 10 月 1 日 20:00 结束一段值勤
        dutyService.publish(publish("BIZ-X1", List.of(
                segment("CA-X1", LocalDateTime.of(2026, 10, 1, 16, 0),
                        LocalDateTime.of(2026, 10, 1, 20, 0),
                        "A320", Position.CAPTAIN, "CAP1"))));

        // 次日 05:00 再出发只休息 9h
        assertThatThrownBy(() -> dutyService.publish(publish("BIZ-X2", List.of(
                segment("CA-X2", LocalDateTime.of(2026, 10, 2, 5, 0),
                        LocalDateTime.of(2026, 10, 2, 7, 0),
                        "A320", Position.CAPTAIN, "CAP1")))))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.QUALIFICATION_FAILED));
        assertThat(comboRepository.findByBizNo("BIZ-X2")).isEmpty();
    }

    @Test
    void proposingSwapWithUnknownComboFails() {
        assertThatThrownBy(() -> dutyService.proposeSwap(swap("BIZ-SWAP-NOPE",
                "BIZ-A", "BIZ-NOPE", List.of(swapItem("A", 0, "CAP2")))))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void sameSwapBizNoDifferentContentConflicts() {
        publishTwoSegmentCombo("BIZ-A5", "CAP1", "FO1");
        publishTwoSegmentCombo("BIZ-B5", "CAP2", "FO2");
        dutyService.proposeSwap(swap("BIZ-SWAP-CONF", "BIZ-A5", "BIZ-B5",
                List.of(swapItem("A", 0, "CAP2"))));

        assertThatThrownBy(() -> dutyService.proposeSwap(swap("BIZ-SWAP-CONF",
                "BIZ-A5", "BIZ-B5", List.of(swapItem("A", 0, "CAP1")))))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.IDEMPOTENT_CONFLICT));
    }

    @Test
    void concurrentSwapsOnSameComboOnlyOneTakesEffect() throws Exception {
        // 三个组合分布在不同日期（间隔超过最低休息），两个并发交换方案都基于
        // BIZ-CC-A 的版本 0，把其机长分别换成 CAP2 / CAP3，必须至多一个生效，
        // 另一个因组合版本已变化而失败。
        publishTwoSegmentComboOnDay("BIZ-CC-A", "CAP1", "FO1", 20);
        publishTwoSegmentComboOnDay("BIZ-CC-B", "CAP2", "FO2", 21);
        createIfAbsent("CAP3", Position.CAPTAIN, "A320");
        publishTwoSegmentComboOnDay("BIZ-CC-C", "CAP3", "FO2", 22);

        dutyService.proposeSwap(swap("BIZ-CC-S1", "BIZ-CC-A", "BIZ-CC-B",
                List.of(swapItem("A", 0, "CAP2"))));
        dutyService.proposeSwap(swap("BIZ-CC-S2", "BIZ-CC-A", "BIZ-CC-C",
                List.of(swapItem("A", 0, "CAP3"))));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = List.of(
                    pool.submit(() -> awaitAndConfirm(start, "BIZ-CC-S1")),
                    pool.submit(() -> awaitAndConfirm(start, "BIZ-CC-S2")));
            start.countDown();

            List<Object> outcomes = new java.util.ArrayList<>();
            List<Throwable> errors = new java.util.ArrayList<>();
            for (Future<?> f : futures) {
                try {
                    outcomes.add(f.get());
                } catch (Exception e) {
                    errors.add(e.getCause() != null ? e.getCause() : e);
                }
            }

            // 恰有一个成功
            long successCount = futures.size() - errors.size();
            assertThat(successCount).isEqualTo(1);
            // 失败者必须是版本冲突
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst()).isInstanceOf(ApiException.class);
            ErrorCode loserCode = ((ApiException) errors.getFirst()).getErrorCode();
            assertThat(loserCode).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);

            // 落库状态自洽：A 版本恰为 1，机长为二者之一
            ComboDetailResponse a = dutyService.getCombo("BIZ-CC-A");
            assertThat(a.version()).isEqualTo(1);
            assertThat(a.segments().getFirst().memberEmployeeNo())
                    .isIn("CAP2", "CAP3");
        } finally {
            pool.shutdownNow();
        }
    }

    private void awaitAndConfirm(CountDownLatch start, String swapBizNo) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        dutyService.confirmSwap(swapBizNo);
    }

    private ComboDetailResponse publishTwoSegmentCombo(String bizNo, String captain, String fo) {
        return publishTwoSegmentComboOnDay(bizNo, captain, fo, 10);
    }

    private ComboDetailResponse publishTwoSegmentComboOnDay(String bizNo, String captain,
                                                            String fo, int day) {
        return dutyService.publish(publish(bizNo, List.of(
                segment("CA-" + bizNo + "-1",
                        LocalDateTime.of(2026, 10, day, 8, 0),
                        LocalDateTime.of(2026, 10, day, 10, 0),
                        "A320", Position.CAPTAIN, captain),
                segment("CA-" + bizNo + "-2",
                        LocalDateTime.of(2026, 10, day, 20, 0),
                        LocalDateTime.of(2026, 10, day, 21, 0),
                        "A320", Position.FIRST_OFFICER, fo))));
    }
}
