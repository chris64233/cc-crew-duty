package com.chris64233.crewduty.repo;

import com.chris64233.crewduty.domain.SwapProposal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SwapProposalRepository extends JpaRepository<SwapProposal, Long> {
    Optional<SwapProposal> findByProposalNo(String proposalNo);
}
