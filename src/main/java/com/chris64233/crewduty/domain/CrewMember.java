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
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 机组成员：记录岗位、可执飞机型和资质有效期。
 *
 * <p>{@code qualificationExpiresOn} 为资质有效截止日（含当日），值勤段出发当日
 * 必须不晚于该日期，否则视为资质过期。
 */
@Entity
@Table(name = "crew_member")
public class CrewMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务工号，全局唯一，也是外部接口引用成员的标识 */
    @Column(nullable = false, unique = true)
    private String employeeNo;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Position position;

    /** 可执飞机型代码集合，如 A320、B737 */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "crew_member_aircraft", joinColumns = @JoinColumn(name = "member_id"))
    @Column(name = "aircraft_type", nullable = false)
    private Set<String> qualifiedAircraftTypes = new LinkedHashSet<>();

    /** 资质有效截止日（含当日） */
    @Column(nullable = false)
    private LocalDate qualificationExpiresOn;

    protected CrewMember() {
    }

    public CrewMember(String employeeNo, String name, Position position,
                      Set<String> qualifiedAircraftTypes, LocalDate qualificationExpiresOn) {
        this.employeeNo = employeeNo;
        this.name = name;
        this.position = position;
        this.qualifiedAircraftTypes =
                new LinkedHashSet<>(qualifiedAircraftTypes);
        this.qualificationExpiresOn = qualificationExpiresOn;
    }

    public Long getId() {
        return id;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public String getName() {
        return name;
    }

    public Position getPosition() {
        return position;
    }

    public Set<String> getQualifiedAircraftTypes() {
        return qualifiedAircraftTypes;
    }

    public LocalDate getQualificationExpiresOn() {
        return qualificationExpiresOn;
    }

    /**
     * 资质在给定日期是否仍有效（截止日含当日）。
     */
    public boolean isQualifiedOn(LocalDate date) {
        return !date.isAfter(qualificationExpiresOn);
    }
}
