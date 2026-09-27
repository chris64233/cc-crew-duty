package com.chris64233.crewduty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.LocalDate;

/**
 * 机型资质：成员可执飞的机型及资质有效期。
 */
@Entity
public class CrewQualification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String aircraftType;

    @Column(nullable = false)
    private LocalDate validFrom;

    @Column(nullable = false)
    private LocalDate validTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private CrewMember member;

    protected CrewQualification() {
    }

    public CrewQualification(String aircraftType, LocalDate validFrom, LocalDate validTo) {
        this.aircraftType = aircraftType;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    /**
     * 判断该资质在指定日期是否覆盖指定机型。
     */
    public boolean covers(String aircraftType, LocalDate date) {
        return this.aircraftType.equalsIgnoreCase(aircraftType)
                && !date.isBefore(validFrom)
                && !date.isAfter(validTo);
    }

    public Long getId() {
        return id;
    }

    public String getAircraftType() {
        return aircraftType;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    void setMember(CrewMember member) {
        this.member = member;
    }
}
