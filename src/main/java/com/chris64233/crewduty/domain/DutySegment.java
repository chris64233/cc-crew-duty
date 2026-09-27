package com.chris64233.crewduty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.LocalDateTime;

/**
 * 值勤段：记录航班时间、机型和所需岗位。
 */
@Entity
public class DutySegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int sequenceNo;

    @Column(nullable = false)
    private String flightNo;

    @Column(nullable = false)
    private String aircraftType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Position requiredPosition;

    @Column(nullable = false)
    private LocalDateTime depTime;

    @Column(nullable = false)
    private LocalDateTime arrTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pairing_id")
    private DutyPairing pairing;

    protected DutySegment() {
    }

    public DutySegment(int sequenceNo, String flightNo, String aircraftType,
                       Position requiredPosition, LocalDateTime depTime, LocalDateTime arrTime) {
        this.sequenceNo = sequenceNo;
        this.flightNo = flightNo;
        this.aircraftType = aircraftType;
        this.requiredPosition = requiredPosition;
        this.depTime = depTime;
        this.arrTime = arrTime;
    }

    public Long getId() {
        return id;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public String getFlightNo() {
        return flightNo;
    }

    public String getAircraftType() {
        return aircraftType;
    }

    public Position getRequiredPosition() {
        return requiredPosition;
    }

    public LocalDateTime getDepTime() {
        return depTime;
    }

    public LocalDateTime getArrTime() {
        return arrTime;
    }

    void setPairing(DutyPairing pairing) {
        this.pairing = pairing;
    }
}
