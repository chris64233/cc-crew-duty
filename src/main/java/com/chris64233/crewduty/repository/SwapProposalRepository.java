package com.chris64233.crewduty.repository;

import com.chris64233.crewduty.domain.SwapProposal;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SwapProposalRepository extends JpaRepository<SwapProposal, Long> {

    Optional<SwapProposal> findByBizNo(String bizNo);

    /**
     * 确认交换时先对方案行加悲观写锁，串行化同一方案的并发确认，
     * 使后进入的事务能读到前一事务已提交的最终状态。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SwapProposal p where p.bizNo = :bizNo")
    Optional<SwapProposal> findByBizNoForUpdate(@Param("bizNo") String bizNo);
}
