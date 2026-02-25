package com.yiyundao.compensation.interfaces.vo.payroll;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 薪资批次汇总VO，包含批次信息和汇总统计数据
 */
@Data
@Schema(description = "薪资批次汇总信息")
public class PayrollBatchSummaryVO {

    @Schema(description = "批次ID")
    private Long batchId;

    @Schema(description = "批次号")
    private String batchNo;

    @Schema(description = "发薪周期ID")
    private Long payCycleId;

    @Schema(description = "周期标签，如 2024-08")
    private String periodLabel;

    @Schema(description = "用工类型: full_time/part_time/contractor")
    private String payrollType;

    @Schema(description = "发薪周期类型: monthly/weekly/daily")
    private String cycleType;

    @Schema(description = "批次状态")
    private String status;

    @Schema(description = "计算状态: pending/running/completed/failed/pay_processing")
    private String computeStatus;

    @Schema(description = "审批状态")
    private String approvalStatus;

    @Schema(description = "支付状态")
    private String paymentStatus;

    @Schema(description = "员工数")
    private Integer totalEmployees;

    @Schema(description = "薪资行数")
    private Integer totalLines;

    @Schema(description = "应发总额")
    private BigDecimal grossTotal;

    @Schema(description = "实发总额")
    private BigDecimal netTotal;

    @Schema(description = "币种")
    private String currency;

    @Schema(description = "预警信息列表(JSON数组格式)")
    private String warnings;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "计算完成时间")
    private LocalDateTime computedAt;

    @Schema(description = "审批完成时间")
    private LocalDateTime approvedAt;

    @Schema(description = "支付完成时间")
    private LocalDateTime paidAt;
}
