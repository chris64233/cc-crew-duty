package com.chris64233.crewduty.repository;

import com.chris64233.crewduty.domain.CrewMember;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrewMemberRepository extends JpaRepository<CrewMember, Long> {

    Optional<CrewMember> findByEmployeeNo(String employeeNo);

    boolean existsByEmployeeNo(String employeeNo);
}
