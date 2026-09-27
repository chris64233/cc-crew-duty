package com.chris64233.crewduty;

import com.chris64233.crewduty.domain.Position;
import com.chris64233.crewduty.web.dto.MemberRequest;
import com.chris64233.crewduty.web.dto.PublishComboRequest;
import com.chris64233.crewduty.web.dto.SwapProposalRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 测试夹具构造工具。
 */
public final class TestFixtures {

    public static final LocalDate QUALIFIED_UNTIL = LocalDate.of(2027, 12, 31);

    private TestFixtures() {
    }

    public static MemberRequest member(String no, String name, Position position,
                                       List<String> types, LocalDate expiresOn) {
        return new MemberRequest(no, name, position, types, expiresOn);
    }

    public static MemberRequest member(String no, Position position, String... types) {
        return member(no, "姓名-" + no, position, List.of(types), QUALIFIED_UNTIL);
    }

    public static PublishComboRequest.SegmentRequest segment(
            String flight, LocalDateTime dep, LocalDateTime arr, String aircraft,
            Position required, String memberNo) {
        return new PublishComboRequest.SegmentRequest(flight, dep, arr, aircraft,
                required, memberNo);
    }

    public static PublishComboRequest publish(String bizNo,
                                              List<PublishComboRequest.SegmentRequest> segments) {
        return new PublishComboRequest(bizNo, segments);
    }

    public static SwapProposalRequest swap(String bizNo, String comboA, String comboB,
                                           List<SwapProposalRequest.SwapItemRequest> items) {
        return new SwapProposalRequest(bizNo, comboA, comboB, items);
    }

    public static SwapProposalRequest.SwapItemRequest swapItem(
            String side, int seqNo, String targetMemberNo) {
        return new SwapProposalRequest.SwapItemRequest(side, seqNo, targetMemberNo);
    }
}
