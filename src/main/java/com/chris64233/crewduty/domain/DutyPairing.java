package com.chris64233.crewduty.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 值勤组合：按时间排列的多个值勤段和对应成员。
 * 使用乐观锁版本号，交换确认时据此检测并发排班变更。
 */
@Entity
public class DutyPairing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String pairingNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PairingStatus status = PairingStatus.DRAFT;

    @Version
    private long version;

    private Instant publishedAt;

    @OneToMany(mappedBy = "pairing", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNo ASC")
    private List<DutySegment> segments = new ArrayList<>();

    @OneToMany(mappedBy = "pairing", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PairingAssignment> assignments = new ArrayList<>();

    protected DutyPairing() {
    }

    public DutyPairing(String pairingNo) {
        this.pairingNo = pairingNo;
    }

    public void addSegment(DutySegment segment) {
        segment.setPairing(this);
        this.segments.add(segment);
    }

    public void addAssignment(PairingAssignment assignment) {
        assignment.setPairing(this);
        this.assignments.add(assignment);
    }

    /** 值勤开始时间：最早值勤段的起飞时间。 */
    public LocalDateTime dutyStart() {
        return segments.stream()
                .map(DutySegment::getDepTime)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    /** 值勤结束时间：最晚值勤段的到达时间。 */
    public LocalDateTime dutyEnd() {
        return segments.stream()
                .map(DutySegment::getArrTime)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    public Long getId() {
        return id;
    }

    public String getPairingNo() {
        return pairingNo;
    }

    public PairingStatus getStatus() {
        return status;
    }

    public void setStatus(PairingStatus status) {
        this.status = status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public List<DutySegment> getSegments() {
        return segments;
    }

    public List<PairingAssignment> getAssignments() {
        return assignments;
    }
}
