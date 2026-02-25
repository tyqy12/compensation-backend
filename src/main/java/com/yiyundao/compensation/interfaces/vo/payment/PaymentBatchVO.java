package com.yiyundao.compensation.interfaces.vo.payment;

import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentBatchVO {
    private Long id;
    private String batchNo;
    private String batchName;
    private String paymentType; // 枚举 code
    private BigDecimal totalAmount;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private String status; // 枚举 code
    private String remark;
    private LocalDateTime submitTime;
    private LocalDateTime approveTime;
    private LocalDateTime processStartTime;
    private LocalDateTime processEndTime;

    public static PaymentBatchVO from(PaymentBatch b) {
        PaymentBatchVO vo = new PaymentBatchVO();
        vo.setId(b.getId());
        vo.setBatchNo(b.getBatchNo());
        vo.setBatchName(b.getBatchName());
        vo.setPaymentType(b.getPaymentType() != null ? b.getPaymentType().getCode() : null);
        vo.setTotalAmount(b.getTotalAmount());
        vo.setTotalCount(b.getTotalCount());
        vo.setSuccessCount(b.getSuccessCount());
        vo.setFailedCount(b.getFailedCount());
        vo.setStatus(b.getStatus() != null ? b.getStatus().getCode() : null);
        vo.setRemark(b.getRemark());
        vo.setSubmitTime(b.getSubmitTime());
        vo.setApproveTime(b.getApproveTime());
        vo.setProcessStartTime(b.getProcessStartTime());
        vo.setProcessEndTime(b.getProcessEndTime());
        return vo;
    }
}
