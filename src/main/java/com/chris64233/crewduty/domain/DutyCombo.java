package com.chris64233.crewduty.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 值勤组合：按时间排列的多个值勤段及对应成员。
 *
 * <p>整组原子发布：发布时全部成员通过校验才落库，拒绝时不产生任何记录。
 * 组合带 JPA 乐观锁版本号，任何段成员交换都会使版本递增；交换方案确认时
 * 必须仍基于当前版本，旧方案不得生效。
 */
@Entity
@Table(name = "duty_combo")
public class DutyCombo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 发布业务号，保证幂等 */
    @Column(nullable = false, unique = true)
    private String bizNo;

    /** 发布请求内容指纹，用于识别“同号不同内容”的冲突重放 */
    @Column(nullable = false)
    private String contentFingerprint;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ComboStatus status = ComboStatus.PUBLISHED;

    @Version
    private long version;

    @Column(nullable = false, updatable = false)
    private Instant publishedAt;

    /**
     * 最近一次调整（发布/交换确认）时间。交换替换段成员时显式更新该字段，
     * 确保组合行发生 UPDATE，从而可靠触发 {@link Version} 乐观锁版本递增。
     */
    @Column(nullable = false)
    private Instant lastAdjustedAt;

    @OneToMany(mappedBy = "combo", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("seqNo ASC")
    private List<DutySegment> segments = new ArrayList<>();

    protected DutyCombo() {
    }

    public DutyCombo(String bizNo, String contentFingerprint, Instant publishedAt) {
        this.bizNo = bizNo;
        this.contentFingerprint = contentFingerprint;
        this.publishedAt = publishedAt;
        this.lastAdjustedAt = publishedAt;
    }

    /** 标记组合发生一次调整（交换确认），驱动版本号递增。 */
    public void touch(Instant at) {
        this.lastAdjustedAt = at;
    }

    public void addSegment(DutySegment segment) {
        segment.attachTo(this);
        segments.add(segment);
    }

    public Long getId() {
        return id;
    }

    public String getBizNo() {
        return bizNo;
    }

    public String getContentFingerprint() {
        return contentFingerprint;
    }

    public ComboStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getLastAdjustedAt() {
        return lastAdjustedAt;
    }

    public List<DutySegment> getSegments() {
        return segments;
    }
}
