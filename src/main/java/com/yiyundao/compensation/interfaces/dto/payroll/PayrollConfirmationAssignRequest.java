package com.yiyundao.compensation.interfaces.dto.payroll;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class PayrollConfirmationAssignRequest {

    @NotNull(message = "负责人员工ID不能为空")
    private Long assigneeEmployeeId;

    /**
     * 指定工资行ID集合，优先级高于 employeeIds。
     * 为空时按 employeeIds 或 applyAll 处理。
     */
    private List<Long> lineIds;

    /**
     * 指定员工ID集合。
     */
    private List<Long> employeeIds;

    /**
     * 是否作用于批次内所有未完结确认记录。
     */
    private Boolean applyAll;
}
