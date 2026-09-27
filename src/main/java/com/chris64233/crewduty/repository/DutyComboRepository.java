package com.chris64233.crewduty.repository;

import com.chris64233.crewduty.domain.DutyCombo;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DutyComboRepository extends JpaRepository<DutyCombo, Long> {

    Optional<DutyCombo> findByBizNo(String bizNo);

    boolean existsByBizNo(String bizNo);

    /**
     * 交换确认时对组合加悲观写锁，串行化针对同一组合的并发交换。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DutyCombo c where c.id = :id")
    Optional<DutyCombo> findByIdForUpdate(@Param("id") Long id);
}
