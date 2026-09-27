package com.chris64233.crewduty.web;

import com.chris64233.crewduty.web.dto.MemberRequest;
import com.chris64233.crewduty.web.dto.MemberResponse;
import com.chris64233.crewduty.service.CrewMemberService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 机组成员档案接口。
 */
@RestController
@RequestMapping("/api/members")
public class CrewMemberController {

    private final CrewMemberService memberService;

    public CrewMemberController(CrewMemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse create(@Valid @RequestBody MemberRequest request) {
        return memberService.create(request);
    }

    @GetMapping("/{employeeNo}")
    public MemberResponse get(@PathVariable String employeeNo) {
        return memberService.get(employeeNo);
    }

    @GetMapping
    public List<MemberResponse> list() {
        return memberService.list();
    }
}
