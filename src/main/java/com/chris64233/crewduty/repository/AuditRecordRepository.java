package com.chris64233.crewduty.repository;

import com.chris64233.crewduty.domain.AuditRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {

    List<AuditRecord> findAllByOrderByOccurredAtAscIdAsc();

    List<AuditRecord> findByBizNoOrderByOccurredAtAscIdAsc(String bizNo);
}
