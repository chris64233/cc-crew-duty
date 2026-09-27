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
import jakarta.persistence.Lob;

/**
 * 组合成员指派：成员在组合中承担的岗位，发布时保存资格快照。
 */
@Entity
public class PairingAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pairing_id")
    private DutyPairing pairing;

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Position position;

    /** 发布/交换生效时的成员资格快照（JSON），用于事后追溯。 */
    @Lob
    @Column
    private String qualificationSnapshot;

    protected PairingAssignment() {
    }

    public PairingAssignment(Long memberId, Position position) {
        this.memberId = memberId;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public DutyPairing getPairing() {
        return pairing;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Position getPosition() {
        return position;
    }

    public String getQualificationSnapshot() {
        return qualificationSnapshot;
    }

    public void setQualificationSnapshot(String qualificationSnapshot) {
        this.qualificationSnapshot = qualificationSnapshot;
    }

    void setPairing(DutyPairing pairing) {
        this.pairing = pairing;
    }
}
