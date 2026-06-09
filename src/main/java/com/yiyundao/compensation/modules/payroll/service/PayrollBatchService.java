package com.yiyundao.compensation.modules.payroll.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;

public interface PayrollBatchService extends IService<PayrollBatch> {
    boolean lockBatch(Long batchId);
    boolean submitForApproval(Long batchId);
    boolean updateStatus(Long batchId, String status);

    /**
     * 重试创建支付批次
     * <p>
     * 用于处理审批通过后支付批次创建失败的场景。
     * 利用幂等性保护，可以安全地重复调用。
     * </p>
     *
     * @param batchId 薪资批次ID
     * @return 是否成功创建支付批次
     */
    boolean retryCreatePaymentBatch(Long batchId);

    /**
     * 重试创建支付批次，并显式控制是否立即触发统一结算通道。
     *
     * @param batchId 薪资批次ID
     * @param triggerTransfer 是否立即触发支付通道
     * @return 是否成功创建支付批次
     */
    boolean retryCreatePaymentBatch(Long batchId, boolean triggerTransfer);
}
