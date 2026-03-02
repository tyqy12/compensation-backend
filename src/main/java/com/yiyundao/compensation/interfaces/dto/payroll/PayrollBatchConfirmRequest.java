package com.yiyundao.compensation.interfaces.dto.payroll;

import lombok.Data;

import java.util.List;

@Data
public class PayrollBatchConfirmRequest {

    /**
     * 需要批量确认的工资行ID。为空表示当前用户负责的全部待确认工资行。
     */
    private List<Long> lineIds;

    private String comment;

    private String signature;
}
