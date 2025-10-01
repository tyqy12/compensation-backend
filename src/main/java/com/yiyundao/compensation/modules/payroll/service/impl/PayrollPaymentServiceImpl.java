package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentService;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.enums.PaymentType;
import com.yiyundao.compensation.service.AlipayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollPaymentServiceImpl implements PayrollPaymentService {

    private static final DateTimeFormatter BATCH_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PaymentBatchService paymentBatchService;
    private final PaymentRecordService paymentRecordService;
    private final PayrollLineService payrollLineService;
    private final EmployeeService employeeService;
    private final AlipayService alipayService;
    private final PayrollBatchMapper payrollBatchMapper;

    @Override
    @Transactional
    public PaymentBatch createPaymentBatch(PayrollBatch payrollBatch, SysUser approver, boolean triggerTransfer) {
        if (payrollBatch == null || payrollBatch.getId() == null) {
            throw new IllegalArgumentException("payrollBatch不能为空");
        }

        // 若已存在支付批次，直接返回并按需触发支付
        if (StringUtils.hasText(payrollBatch.getPaymentBatchNo())) {
            PaymentBatch existing = paymentBatchService.getByBatchNo(payrollBatch.getPaymentBatchNo());
            if (existing != null) {
                log.info("Payroll batch {} already linked to payment batch {}", payrollBatch.getId(), existing.getBatchNo());
                if (triggerTransfer && existing.getStatus() == BatchStatus.SUBMITTED) {
                    alipayService.batchTransfer(existing.getBatchNo());
                }
                return existing;
            }
        }

        List<PayrollLine> lines = payrollLineService.list(new LambdaQueryWrapper<PayrollLine>()
                .eq(PayrollLine::getBatchId, payrollBatch.getId()));
        if (lines.isEmpty()) {
            log.warn("Payroll batch {} has no computed lines, skip payment creation", payrollBatch.getId());
            return null;
        }

        String batchNo = buildBatchNo(payrollBatch);
        List<PaymentRecord> records = new ArrayList<>();
        BigDecimal payableTotal = BigDecimal.ZERO;
        int payableCount = 0;

        for (PayrollLine line : lines) {
            BigDecimal net = line.getNetAmount();
            if (net == null || net.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Employee employee = employeeService.getById(line.getEmployeeId());
            if (employee == null) {
                log.warn("Employee {} missing for payroll line {}", line.getEmployeeId(), line.getId());
                continue;
            }

            PaymentRecord record = new PaymentRecord();
            record.setBatchNo(batchNo);
            record.setEmployeeId(line.getEmployeeId());
            record.setPaymentType(PaymentType.SALARY);
            record.setAmount(net);
            record.setCurrency(payrollBatch.getCurrency());
            record.setPaymentMethod("ALIPAY");
            record.setRecipientAccount(resolveRecipientAccount(employee));
            record.setRecipientName(employee.getName());
            record.setPaymentDesc(buildPaymentDesc(payrollBatch));

            if (!StringUtils.hasText(record.getRecipientAccount())) {
                record.setStatus(PaymentStatus.FAILED);
                record.setErrorCode("ACCOUNT_MISSING");
                record.setErrorMsg("缺少收款账号");
                log.warn("Payment record for employee {} skipped due to missing account", employee.getId());
            } else {
                record.setStatus(PaymentStatus.PENDING);
                payableTotal = payableTotal.add(net);
                payableCount++;
            }
            records.add(record);
        }

        if (records.isEmpty()) {
            log.warn("Payroll batch {} has no payment records to persist", payrollBatch.getId());
            return null;
        }

        PaymentBatch paymentBatch = new PaymentBatch();
        paymentBatch.setBatchNo(batchNo);
        paymentBatch.setBatchName(buildBatchName(payrollBatch));
        paymentBatch.setPaymentType(PaymentType.SALARY);
        paymentBatch.setTotalAmount(payableTotal);
        paymentBatch.setTotalCount(records.size());
        long failedCount = records.stream().filter(r -> r.getStatus() == PaymentStatus.FAILED).count();
        paymentBatch.setFailedCount((int) failedCount);
        paymentBatch.setSuccessCount(0);
        paymentBatch.setStatus(payableCount > 0 ? BatchStatus.SUBMITTED : BatchStatus.FAILED);
        paymentBatch.setSubmitTime(LocalDateTime.now());
        paymentBatch.setApproverId(approver != null ? approver.getId() : null);
        if (paymentBatch.getStatus() == BatchStatus.SUBMITTED) {
            paymentBatch.setApproveTime(LocalDateTime.now());
        }
        paymentBatch.setRemark("Auto-generated from payroll batch " + payrollBatch.getId());

        paymentBatchService.save(paymentBatch);
        paymentRecordService.saveBatch(records);

        LambdaUpdateWrapper<PayrollBatch> wrapper = new LambdaUpdateWrapper<PayrollBatch>()
                .eq(PayrollBatch::getId, payrollBatch.getId())
                .set(PayrollBatch::getPaymentBatchNo, batchNo);
        if (payableCount > 0) {
            wrapper.set(PayrollBatch::getStatus, "pay_processing");
        }
        payrollBatchMapper.update(null, wrapper);

        payrollBatch.setPaymentBatchNo(batchNo);
        if (payableCount > 0) {
            payrollBatch.setStatus("pay_processing");
        }

        if (triggerTransfer && payableCount > 0) {
            alipayService.batchTransfer(batchNo);
        }

        return paymentBatch;
    }

    private String buildBatchNo(PayrollBatch payrollBatch) {
        String prefix = "PAYROLL-" + payrollBatch.getId();
        return prefix + "-" + BATCH_NO_FORMAT.format(LocalDateTime.now());
    }

    private String buildBatchName(PayrollBatch payrollBatch) {
        String period = StringUtils.hasText(payrollBatch.getPeriodLabel()) ? payrollBatch.getPeriodLabel() : "周期";
        return "薪资发放-" + period;
    }

    private String buildPaymentDesc(PayrollBatch payrollBatch) {
        String period = StringUtils.hasText(payrollBatch.getPeriodLabel()) ? payrollBatch.getPeriodLabel() : "当期";
        return "薪资发放-" + period;
    }

    private String resolveRecipientAccount(Employee employee) {
        if (employee == null) {
            return null;
        }
        if (StringUtils.hasText(employee.getPlatformUserId())) {
            return employee.getPlatformUserId();
        }
        if (StringUtils.hasText(employee.getPhone())) {
            return employee.getPhone();
        }
        if (StringUtils.hasText(employee.getEmail())) {
            return employee.getEmail();
        }
        if (StringUtils.hasText(employee.getBankAccount())) {
            return employee.getBankAccount();
        }
        return null;
    }
}
