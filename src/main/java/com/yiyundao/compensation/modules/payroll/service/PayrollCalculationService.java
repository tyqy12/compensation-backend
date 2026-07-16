package com.yiyundao.compensation.modules.payroll.service;

import com.yiyundao.compensation.interfaces.dto.payroll.PayrollLedgerDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollManagerReviewDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollPreviewDto;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;

public interface PayrollCalculationService {
    /** 试算：根据批次生成/刷新工资行（不落库，返回true表示试算成功，后续可扩展返回明细DTO） */
    boolean dryRun(Long batchId);

    /** 计算落地：根据批次模板与数据生成工资行并保存 */
    boolean computeAndSave(Long batchId);

    /** 以新的批次 revision 重算完整工资结果集；employeeId 仅用于兼容旧的单行重算入口。 */
    boolean recomputeLine(Long batchId, Long employeeId);

    /** 校验批次可计算性（周期/模板/数据就绪） */
    boolean validateReady(PayrollBatch batch);

    /** 试算预览：返回行级明细与汇总（不落库） */
    PayrollPreviewDto dryRunPreview(Long batchId);

    /** 财务台账视图：使用已落地数据+预警信息 */
    PayrollLedgerDto ledger(Long batchId);

    /** 经理核对视图：按部门/经理筛选，包含预警与差异 */
    PayrollManagerReviewDto managerReview(Long batchId, String department, Long managerId, String keyword);
}
