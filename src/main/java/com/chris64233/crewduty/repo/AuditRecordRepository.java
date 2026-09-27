package com.chris64233.crewduty.repo;

import com.chris64233.crewduty.domain.AuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {
    List<AuditRecord> findByPairingIdOrderByCreatedAtAsc(Long pairingId);
}
