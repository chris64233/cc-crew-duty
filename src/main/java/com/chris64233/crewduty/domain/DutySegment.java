package com.chris64233.crewduty.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 值勤段：一段航班值勤安排，记录航班时间、机型、所需岗位和执飞机组成员。
 *
 * <p>段上冗余保存发布时的成员资格快照（岗位、机型集合、资质截止日），
 * 即使后续成员档案发生变化，组合当时满足资质要求的事实仍可追溯。
 */
@Entity
@Table(name = "duty_segment")
public class DutySegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "combo_id", nullable = false)
    private DutyCombo combo;

    /** 组合内的时间顺序序号，从 0 开始 */
    @Column(nullable = false)
    private int seqNo;

    @Column(nullable = false)
    private String flightNo;

    @Column(nullable = false)
    private LocalDateTime departureAt;

    @Column(nullable = false)
    private LocalDateTime arrivalAt;

    @Column(nullable = false)
    private String aircraftType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Position requiredPosition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private CrewMember member;

    // ---- 发布/交换时固化的成员资格快照 ----

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Position snapshotPosition;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "segment_qual_snapshot_aircraft",
            joinColumns = @JoinColumn(name = "segment_id"))
    @Column(name = "aircraft_type", nullable = false)
    private Set<String> snapshotAircraftTypes = new LinkedHashSet<>();

    /** 快照中成员资质有效截止日 */
    @Column(nullable = false)
    private java.time.LocalDate snapshotQualificationExpiresOn;

    protected DutySegment() {
    }

    public DutySegment(int seqNo, String flightNo, LocalDateTime departureAt,
                       LocalDateTime arrivalAt, String aircraftType,
                       Position requiredPosition, CrewMember member) {
        this.seqNo = seqNo;
        this.flightNo = flightNo;
        this.departureAt = departureAt;
        this.arrivalAt = arrivalAt;
        this.aircraftType = aircraftType;
        this.requiredPosition = requiredPosition;
        assignMember(member);
    }

    /**
     * 更换执飞成员并同步刷新资格快照（交换确认时使用）。
     */
    public void assignMember(CrewMember newMember) {
        this.member = newMember;
        this.snapshotPosition = newMember.getPosition();
        this.snapshotAircraftTypes = new LinkedHashSet<>(newMember.getQualifiedAircraftTypes());
        this.snapshotQualificationExpiresOn = newMember.getQualificationExpiresOn();
    }

    void attachTo(DutyCombo combo) {
        this.combo = combo;
    }

    public Long getId() {
        return id;
    }

    public DutyCombo getCombo() {
        return combo;
    }

    public int getSeqNo() {
        return seqNo;
    }

    public String getFlightNo() {
        return flightNo;
    }

    public LocalDateTime getDepartureAt() {
        return departureAt;
    }

    public LocalDateTime getArrivalAt() {
        return arrivalAt;
    }

    public String getAircraftType() {
        return aircraftType;
    }

    public Position getRequiredPosition() {
        return requiredPosition;
    }

    public CrewMember getMember() {
        return member;
    }

    public Position getSnapshotPosition() {
        return snapshotPosition;
    }

    public Set<String> getSnapshotAircraftTypes() {
        return snapshotAircraftTypes;
    }

    public java.time.LocalDate getSnapshotQualificationExpiresOn() {
        return snapshotQualificationExpiresOn;
    }
}
