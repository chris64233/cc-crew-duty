package com.chris64233.crewduty.api;

import com.chris64233.crewduty.domain.Position;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * API 请求/响应 DTO 汇总。
 */
public final class Dtos {

    private Dtos() {
    }

    // ---------- 成员 ----------

    public record QualificationDto(
            @NotBlank String aircraftType,
            @NotNull LocalDate validFrom,
            @NotNull LocalDate validTo
    ) {
    }

    public record CreateMemberRequest(
            @NotBlank String employeeNo,
            @NotBlank String name,
            @NotNull Position position,
            @NotEmpty List<@Valid QualificationDto> qualifications
    ) {
    }

    public record MemberResponse(
            Long id,
            String employeeNo,
            String name,
            Position position,
            List<QualificationDto> qualifications
    ) {
    }

    // ---------- 值勤组合 ----------

    public record SegmentDto(
            @NotNull Integer sequenceNo,
            @NotBlank String flightNo,
            @NotBlank String aircraftType,
            @NotNull Position requiredPosition,
            @NotNull LocalDateTime depTime,
            @NotNull LocalDateTime arrTime
    ) {
    }

    public record AssignmentDto(
            @NotNull Long memberId,
            @NotNull Position position
    ) {
    }

    public record CreatePairingRequest(
            @NotBlank String pairingNo,
            @NotEmpty List<@Valid SegmentDto> segments,
            @NotEmpty List<@Valid AssignmentDto> assignments
    ) {
    }

    public record AssignmentView(
            Long memberId,
            String employeeNo,
            String memberName,
            Position position,
            /** 发布时保存的资格快照（JSON），草稿状态为 null。 */
            String qualificationSnapshot
    ) {
    }

    public record PairingResponse(
            Long id,
            String pairingNo,
            String status,
            long version,
            List<SegmentDto> segments,
            List<AssignmentView> assignments,
            String publishedAt
    ) {
    }

    /** 发布请求：业务号保证幂等。 */
    public record PublishRequest(@NotBlank String idemKey) {
    }

    /** 资格校验失败原因。 */
    public record QualificationFailure(
            String rule,
            String message
    ) {
    }

    // ---------- 交换 ----------

    public record CreateSwapRequest(
            @NotBlank String idemKey,
            @NotNull Long pairingAId,
            @NotNull Long pairingBId,
            @NotNull Long memberAId,
            @NotNull Long memberBId
    ) {
    }

    public record SwapResponse(
            Long id,
            String proposalNo,
            String status,
            Long pairingAId,
            Long pairingBId,
            Long memberAId,
            Long memberBId,
            long expectedVersionA,
            long expectedVersionB,
            String failureReason,
            String createdAt,
            String decidedAt
    ) {
    }

    // ---------- 时间线 / 审计 ----------

    public record TimelineEntry(
            Long pairingId,
            String pairingNo,
            String status,
            Position position,
            LocalDateTime dutyStart,
            LocalDateTime dutyEnd,
            List<SegmentDto> segments
    ) {
    }

    public record AuditResponse(
            Long id,
            String action,
            Long pairingId,
            String detail,
            String createdAt
    ) {
    }
}
