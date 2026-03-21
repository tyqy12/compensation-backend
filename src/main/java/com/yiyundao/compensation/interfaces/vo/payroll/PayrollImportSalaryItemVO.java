package com.yiyundao.compensation.interfaces.vo.payroll;

import lombok.Data;

@Data
public class PayrollImportSalaryItemVO {
    private String code;
    private String name;
    private String type;
    private Boolean taxable;
    private Boolean showOnPayslip;
    private Integer orderNum;
}
