package com.chris64233.crewduty.repo;

import com.chris64233.crewduty.domain.CrewMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CrewMemberRepository extends JpaRepository<CrewMember, Long> {
    Optional<CrewMember> findByEmployeeNo(String employeeNo);
}
