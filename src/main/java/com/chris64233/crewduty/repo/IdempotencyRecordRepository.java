package com.chris64233.crewduty.repo;

import com.chris64233.crewduty.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByIdemKey(String idemKey);
}
