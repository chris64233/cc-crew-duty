package com.chris64233.crewduty.web.dto;

import com.chris64233.crewduty.domain.Position;
import java.time.LocalDate;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * 成员信息响应。
 */
public record MemberResponse(
        String employeeNo,
        String name,
        Position position,
        SortedSet<String> qualifiedAircraftTypes,
        LocalDate qualificationExpiresOn) {

    public static MemberResponse from(com.chris64233.crewduty.domain.CrewMember m) {
        return new MemberResponse(m.getEmployeeNo(), m.getName(), m.getPosition(),
                new TreeSet<>(m.getQualifiedAircraftTypes()), m.getQualificationExpiresOn());
    }
}
