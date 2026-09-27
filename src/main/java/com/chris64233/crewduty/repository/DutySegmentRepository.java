package com.chris64233.crewduty.repository;

import com.chris64233.crewduty.domain.DutySegment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DutySegmentRepository extends JpaRepository<DutySegment, Long> {

    /**
     * 查询某成员在所有已发布组合中的值勤段，按出发时间升序，
     * 用于跨组合最低休息校验与成员值勤时间线。
     */
    @Query("""
            select s from DutySegment s
            where s.member.employeeNo = :employeeNo
            order by s.departureAt asc, s.seqNo asc
            """)
    List<DutySegment> findAllByMemberEmployeeNoOrderByTime(@Param("employeeNo") String employeeNo);

    /**
     * 批量查询一批成员在全部已发布组合中的段（发布时跨组合休息检查用）。
     */
    @Query("""
            select s from DutySegment s
            where s.member.employeeNo in :employeeNos
            order by s.departureAt asc
            """)
    List<DutySegment> findByMemberEmployeeNoIn(@Param("employeeNos") Collection<String> employeeNos);

    /**
     * 查询一批成员在指定组合之外（其他已发布组合中）的段
     * （交换确认时跨组合休息/时长冲突检查用）。
     */
    @Query("""
            select s from DutySegment s
            where s.member.employeeNo in :employeeNos
            and s.combo.id not in :excludeComboIds
            order by s.departureAt asc
            """)
    List<DutySegment> findByMembersOutsideCombos(
            @Param("employeeNos") Collection<String> employeeNos,
            @Param("excludeComboIds") Collection<Long> excludeComboIds);
}
