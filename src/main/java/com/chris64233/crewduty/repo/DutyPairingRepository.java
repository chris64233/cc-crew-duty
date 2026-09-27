package com.chris64233.crewduty.repo;

import com.chris64233.crewduty.domain.DutyPairing;
import com.chris64233.crewduty.domain.PairingStatus;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.Optional;

public interface DutyPairingRepository extends JpaRepository<DutyPairing, Long> {
    Optional<DutyPairing> findByPairingNo(String pairingNo);

    boolean existsByPairingNo(String pairingNo);

    Optional<DutyPairing> findWithSegmentsAndAssignmentsById(Long id);

    /**
     * 休息规则校验使用。flushMode=COMMIT 避免交换预演中的内存变更被提前刷库，
     * 保证校验未通过时组合版本不被污染。
     */
    @QueryHints(@QueryHint(name = "org.hibernate.flushMode", value = "COMMIT"))
    List<DutyPairing> findByStatus(PairingStatus status);
}
