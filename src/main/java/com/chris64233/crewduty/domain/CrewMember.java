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

import java.util.ArrayList;
import java.util.List;

/**
 * 机组成员：记录岗位、可执飞机型及资质有效期。
 */
@Entity
public class CrewMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String employeeNo;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Position position;

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CrewQualification> qualifications = new ArrayList<>();

    protected CrewMember() {
    }

    public CrewMember(String employeeNo, String name, Position position) {
        this.employeeNo = employeeNo;
        this.name = name;
        this.position = position;
    }

    public void addQualification(CrewQualification qualification) {
        qualification.setMember(this);
        this.qualifications.add(qualification);
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

    public List<CrewQualification> getQualifications() {
        return qualifications;
    }
}
