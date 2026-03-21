package com.yiyundao.compensation.modules.payroll.service;

import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.user.entity.SysUser;

public interface PayrollPaymentService {

    /**
     * 根据已审批的薪资批次生成支付批次和支付记录。
     *
     * @param payrollBatch 薪资批次
     * @param approver     审批人（可为空）
     * @param triggerTransfer 是否自动触发支付通道（统一结算路由）
     * @return 新建或已存在的支付批次，若无可支付记录则返回 null
     */
    PaymentBatch createPaymentBatch(PayrollBatch payrollBatch, SysUser approver, boolean triggerTransfer);

    /**
     * 对支付失败批次执行一键重试。
     *
     * @param payrollBatchId  薪资批次ID（要求状态为 pay_failed）
     * @param triggerTransfer 是否立即触发发放
     * @return 最新支付批次
     */
    PaymentBatch retryFailedPayment(Long payrollBatchId, boolean triggerTransfer);
}
