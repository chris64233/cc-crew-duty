package com.chris64233.crewduty.service;

import com.chris64233.crewduty.domain.CrewMember;
import com.chris64233.crewduty.repository.CrewMemberRepository;
import com.chris64233.crewduty.web.ErrorCode;
import com.chris64233.crewduty.web.ApiException;
import com.chris64233.crewduty.web.dto.MemberRequest;
import com.chris64233.crewduty.web.dto.MemberResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 机组成员档案服务。
 */
@Service
public class CrewMemberService {

    private final CrewMemberRepository memberRepository;

    public CrewMemberService(CrewMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional
    public MemberResponse create(MemberRequest request) {
        if (memberRepository.existsByEmployeeNo(request.employeeNo())) {
            throw new ApiException(ErrorCode.MEMBER_ALREADY_EXISTS,
                    "成员工号 %s 已存在".formatted(request.employeeNo()));
        }
        Set<String> types = new LinkedHashSet<>();
        request.qualifiedAircraftTypes().forEach(t -> types.add(t.trim().toUpperCase(Locale.ROOT)));
        CrewMember member = new CrewMember(
                request.employeeNo().trim(), request.name().trim(), request.position(),
                types, request.qualificationExpiresOn());
        return MemberResponse.from(memberRepository.save(member));
    }

    @Transactional(readOnly = true)
    public MemberResponse get(String employeeNo) {
        return MemberResponse.from(resolve(employeeNo));
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> list() {
        return memberRepository.findAll().stream().map(MemberResponse::from).toList();
    }

    /** 供内部服务使用的按工号加载，缺失抛 NOT_FOUND。 */
    public CrewMember resolve(String employeeNo) {
        return memberRepository.findByEmployeeNo(employeeNo)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "成员不存在: %s".formatted(employeeNo)));
    }
}
