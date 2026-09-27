package com.chris64233.crewduty.service;

import com.chris64233.crewduty.domain.Position;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 校验用的不可变值勤段视图，屏蔽“已落库的段”和“交换后的假设段”之间的差异，
 * 使同一套规则同时服务于发布校验与交换后的重新校验。
 *
 * @param seqNo                  组合内顺序
 * @param flightNo               航班号
 * @param departureAt            出发时间
 * @param arrivalAt              到达时间
 * @param aircraftType           机型
 * @param requiredPosition       所需岗位
 * @param memberEmployeeNo       执飞成员工号
 * @param memberPosition         成员当前岗位
 * @param memberAircraftTypes    成员当前可执飞机型
 * @param qualificationExpiresOn 成员资质有效截止日（含当日）
 * @param sourceComboBizNo       该段所属组合的业务号，用于跨组合休息检查中识别来源
 */
public record AssignmentView(
        int seqNo,
        String flightNo,
        LocalDateTime departureAt,
        LocalDateTime arrivalAt,
        String aircraftType,
        Position requiredPosition,
        String memberEmployeeNo,
        Position memberPosition,
        Set<String> memberAircraftTypes,
        LocalDate qualificationExpiresOn,
        String sourceComboBizNo) {
}
