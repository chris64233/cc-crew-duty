package com.chris64233.crewduty.api;

import com.chris64233.crewduty.service.CrewMemberService;
import com.chris64233.crewduty.api.Dtos.CreateMemberRequest;
import com.chris64233.crewduty.api.Dtos.MemberResponse;
import com.chris64233.crewduty.api.Dtos.TimelineEntry;
import com.chris64233.crewduty.service.PairingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/members")
public class CrewMemberController {

    private final CrewMemberService memberService;
    private final PairingService pairingService;

    public CrewMemberController(CrewMemberService memberService, PairingService pairingService) {
        this.memberService = memberService;
        this.pairingService = pairingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse create(@Valid @RequestBody CreateMemberRequest request) {
        return memberService.create(request);
    }

    @GetMapping("/{id}")
    public MemberResponse get(@PathVariable Long id) {
        return memberService.get(id);
    }

    /** 成员值勤时间线。 */
    @GetMapping("/{id}/timeline")
    public List<TimelineEntry> timeline(@PathVariable Long id) {
        return pairingService.getMemberTimeline(id);
    }
}
