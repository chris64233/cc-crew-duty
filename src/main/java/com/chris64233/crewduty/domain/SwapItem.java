package com.chris64233.crewduty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 交换方案中的一条交换项：某组合（A 或 B）中某个值勤段的成员将被替换。
 *
 * <p>目标成员在方案级别去重保存：同属 A 方或同属 B 方的多个段可以分别
 * 指向对方组合的不同成员，因此每个段独立携带目标成员工号。
 */
@Embeddable
public class SwapItem {

    /** "A" 或 "B"，标识该段属于交换的哪一方组合 */
    @Column(name = "item_side", nullable = false)
    private String side;

    @Column(name = "segment_id", nullable = false)
    private Long segmentId;

    /** 交换后执飞该段的目标成员工号 */
    @Column(name = "target_employee_no", nullable = false)
    private String targetEmployeeNo;

    protected SwapItem() {
    }

    public SwapItem(String side, Long segmentId, String targetEmployeeNo) {
        this.side = side;
        this.segmentId = segmentId;
        this.targetEmployeeNo = targetEmployeeNo;
    }

    public String getSide() {
        return side;
    }

    public Long getSegmentId() {
        return segmentId;
    }

    public String getTargetEmployeeNo() {
        return targetEmployeeNo;
    }
}
