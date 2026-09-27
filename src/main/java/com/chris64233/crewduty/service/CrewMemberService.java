package com.chris64233.crewduty.service;

import com.chris64233.crewduty.api.BusinessException;
import com.chris64233.crewduty.api.Dtos.CreateMemberRequest;
import com.chris64233.crewduty.api.Dtos.MemberResponse;
import com.chris64233.crewduty.api.Dtos.QualificationDto;
import com.chris64233.crewduty.domain.CrewMember;
import com.chris64233.crewduty.domain.CrewQualification;
import com.chris64233.crewduty.repo.CrewMemberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 机组成员服务。
 */
@Service
public class CrewMemberService {

    private final CrewMemberRepository repository;

    public CrewMemberService(CrewMemberRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public MemberResponse create(CreateMemberRequest request) {
        CrewMember member = new CrewMember(request.employeeNo(), request.name(), request.position());
        request.qualifications().forEach(q -> {
            if (q.validTo().isBefore(q.validFrom())) {
                throw BusinessException.unprocessable("INVALID_QUALIFICATION_PERIOD",
                        "资质有效期截止日不能早于起始日", java.util.List.of());
            }
            member.addQualification(new CrewQualification(q.aircraftType(), q.validFrom(), q.validTo()));
        });
        try {
            repository.save(member);
            repository.flush();
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.conflict("EMPLOYEE_NO_DUPLICATED",
                    "员工号 " + request.employeeNo() + " 已存在");
        }
        return toResponse(member);
    }

    @Transactional(readOnly = true)
    public MemberResponse get(Long id) {
        return repository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> BusinessException.notFound("成员 " + id));
    }

    private MemberResponse toResponse(CrewMember member) {
        return new MemberResponse(
                member.getId(),
                member.getEmployeeNo(),
                member.getName(),
                member.getPosition(),
                member.getQualifications().stream()
                        .map(q -> new QualificationDto(q.getAircraftType(), q.getValidFrom(), q.getValidTo()))
                        .toList());
    }
}
