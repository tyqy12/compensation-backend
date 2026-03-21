package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.common.utils.ValidationUtils;
import com.yiyundao.compensation.enums.EmploymentType;
import com.yiyundao.compensation.enums.SettlementAccountType;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payment.service.SettlementService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.PayrollCalculationService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentBatchProcessStatus;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.enums.PaymentType;
import com.yiyundao.compensation.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
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
    private final ObjectProvider<PayrollCalculationService> payrollCalculationServiceProvider;
    private final ObjectProvider<EmployeeService> employeeServiceProvider;
    private final SettlementService settlementService;
    private final EncryptionService encryptionService;
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
                    settlementService.batchTransfer(existing.getBatchNo());
                }
                return existing;
            }
        }

        List<PayrollLine> lines = payrollLineService.list(new LambdaQueryWrapper<PayrollLine>()
                .eq(PayrollLine::getBatchId, payrollBatch.getId()));
        if (lines.isEmpty()) {
            boolean computed = computePayrollLinesIfNeeded(payrollBatch);
            if (computed) {
                lines = payrollLineService.list(new LambdaQueryWrapper<PayrollLine>()
                        .eq(PayrollLine::getBatchId, payrollBatch.getId()));
            }
        }
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
            Employee employee = getEmployeeService().getById(line.getEmployeeId());
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
            RecipientRouteResult recipientRouteResult = resolveRecipientRoute(payrollBatch, line, employee);
            record.setPaymentMethod(recipientRouteResult.paymentMethod());
            record.setProviderCode(recipientRouteResult.providerCode());
            record.setRecipientAccount(recipientRouteResult.recipientAccount());
            record.setRecipientName(StringUtils.hasText(employee.getSettlementAccountName())
                    ? employee.getSettlementAccountName()
                    : employee.getName());
            record.setPaymentDesc(buildPaymentDesc(payrollBatch));
            record.setIdCardHash(hashIdCard(employee));

            if (!recipientRouteResult.supported()) {
                record.setStatus(PaymentStatus.FAILED);
                record.setErrorCode(recipientRouteResult.errorCode());
                record.setErrorMsg(recipientRouteResult.errorMsg());
                log.warn("Payment record for employee {} skipped: {}", employee.getId(), recipientRouteResult.errorMsg());
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
        paymentBatch.setPaymentStatus(payableCount > 0 ? PaymentBatchProcessStatus.SUBMITTED : PaymentBatchProcessStatus.FAILED);
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
            wrapper.set(PayrollBatch::getStatus, PayrollBatchStatus.PAY_PROCESSING);
        }
        payrollBatchMapper.update(null, wrapper);

        payrollBatch.setPaymentBatchNo(batchNo);
        if (payableCount > 0) {
            payrollBatch.setStatus(PayrollBatchStatus.PAY_PROCESSING);
        }

        if (triggerTransfer && payableCount > 0) {
            settlementService.batchTransfer(batchNo);
        }

        return paymentBatch;
    }

    @Override
    @Transactional
    public PaymentBatch retryFailedPayment(Long payrollBatchId, boolean triggerTransfer) {
        if (payrollBatchId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "薪酬批次ID不能为空");
        }

        PayrollBatch payrollBatch = payrollBatchMapper.selectById(payrollBatchId);
        if (payrollBatch == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "薪酬批次不存在");
        }
        if (payrollBatch.getStatus() != PayrollBatchStatus.PAY_FAILED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "仅支付失败批次支持重试");
        }
        if (!StringUtils.hasText(payrollBatch.getPaymentBatchNo())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "当前批次未关联支付批次，不能重试");
        }

        String batchNo = payrollBatch.getPaymentBatchNo();
        PaymentBatch paymentBatch = paymentBatchService.getByBatchNo(batchNo);
        if (paymentBatch == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "支付批次不存在");
        }
        if (paymentBatch.getStatus() == BatchStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "支付批次正在处理中，请稍后查看结果");
        }
        if (paymentBatch.getStatus() == BatchStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "支付批次已完成，不能重试");
        }

        long retryableCount = paymentRecordService.count(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getBatchNo, batchNo)
                .in(PaymentRecord::getStatus, PaymentStatus.FAILED, PaymentStatus.CANCELLED));
        if (retryableCount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "当前批次无可重试的失败记录");
        }

        boolean resetRecords = paymentRecordService.update(new LambdaUpdateWrapper<PaymentRecord>()
                .eq(PaymentRecord::getBatchNo, batchNo)
                .in(PaymentRecord::getStatus, PaymentStatus.FAILED, PaymentStatus.CANCELLED)
                .set(PaymentRecord::getStatus, PaymentStatus.PENDING)
                .set(PaymentRecord::getErrorCode, null)
                .set(PaymentRecord::getErrorMsg, null)
                .set(PaymentRecord::getPaymentTime, null)
                .set(PaymentRecord::getNotificationTime, null)
                .set(PaymentRecord::getProviderOrderNo, null)
                .set(PaymentRecord::getProviderTradeNo, null)
                .set(PaymentRecord::getAlipayOrderNo, null)
                .set(PaymentRecord::getAlipayTradeNo, null));
        if (!resetRecords) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "重置失败记录状态失败");
        }

        long successCount = paymentRecordService.count(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getBatchNo, batchNo)
                .eq(PaymentRecord::getStatus, PaymentStatus.SUCCESS));
        long failedCount = paymentRecordService.count(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getBatchNo, batchNo)
                .in(PaymentRecord::getStatus, PaymentStatus.FAILED, PaymentStatus.CANCELLED));

        boolean updatePaymentBatch = paymentBatchService.update(new LambdaUpdateWrapper<PaymentBatch>()
                .eq(PaymentBatch::getBatchNo, batchNo)
                .set(PaymentBatch::getStatus, BatchStatus.SUBMITTED)
                .set(PaymentBatch::getPaymentStatus, PaymentBatchProcessStatus.SUBMITTED)
                .set(PaymentBatch::getSuccessCount, (int) successCount)
                .set(PaymentBatch::getFailedCount, (int) failedCount)
                .set(PaymentBatch::getProcessStartTime, null)
                .set(PaymentBatch::getProcessEndTime, null));
        if (!updatePaymentBatch) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "更新支付批次状态失败");
        }

        payrollBatchMapper.update(null, new LambdaUpdateWrapper<PayrollBatch>()
                .eq(PayrollBatch::getId, payrollBatchId)
                .set(PayrollBatch::getStatus, PayrollBatchStatus.PAY_PROCESSING));

        if (triggerTransfer) {
            settlementService.batchTransfer(batchNo);
        }

        log.info("支付失败批次重试已触发: payrollBatchId={}, paymentBatchNo={}, retryableCount={}, triggerTransfer={}",
                payrollBatchId, batchNo, retryableCount, triggerTransfer);

        return paymentBatchService.getByBatchNo(batchNo);
    }

    private String buildBatchNo(PayrollBatch payrollBatch) {
        String prefix = "PAYROLL-" + payrollBatch.getId();
        return prefix + "-" + BATCH_NO_FORMAT.format(LocalDateTime.now());
    }

    private EmployeeService getEmployeeService() {
        EmployeeService employeeService = employeeServiceProvider.getIfAvailable();
        if (employeeService == null) {
            throw new IllegalStateException("EmployeeService 不可用，无法创建支付批次");
        }
        return employeeService;
    }

    private boolean computePayrollLinesIfNeeded(PayrollBatch payrollBatch) {
        if (payrollBatch == null || payrollBatch.getId() == null) {
            return false;
        }
        PayrollCalculationService payrollCalculationService = payrollCalculationServiceProvider.getIfAvailable();
        if (payrollCalculationService == null) {
            log.warn("PayrollCalculationService 不可用，无法自动补算 payroll_line: batchId={}", payrollBatch.getId());
            return false;
        }
        try {
            boolean computed = payrollCalculationService.computeAndSave(payrollBatch.getId());
            if (!computed) {
                log.warn("自动补算 payroll_line 失败: batchId={}", payrollBatch.getId());
            }
            return computed;
        } catch (Exception ex) {
            log.warn("自动补算 payroll_line 异常: batchId={}, msg={}", payrollBatch.getId(), ex.getMessage());
            return false;
        }
    }

    private String buildBatchName(PayrollBatch payrollBatch) {
        String period = StringUtils.hasText(payrollBatch.getPeriodLabel()) ? payrollBatch.getPeriodLabel() : "周期";
        return "薪资发放-" + period;
    }

    private String buildPaymentDesc(PayrollBatch payrollBatch) {
        String period = StringUtils.hasText(payrollBatch.getPeriodLabel()) ? payrollBatch.getPeriodLabel() : "当期";
        return "薪资发放-" + period;
    }

    private RecipientRouteResult resolveRecipientRoute(PayrollBatch payrollBatch, PayrollLine line, Employee employee) {
        if (employee == null) {
            return RecipientRouteResult.failed("ACCOUNT_MISSING", "员工信息不存在");
        }

        String employmentType = resolveEmploymentType(payrollBatch, line, employee);
        if (!StringUtils.hasText(employmentType)) {
            return RecipientRouteResult.failed("EMPLOYMENT_TYPE_MISSING", "缺少用工类型，无法路由结算渠道");
        }

        String settlementType = normalizeSettlementType(employee.getSettlementAccountType());
        String settlementAccount = decryptAccount(employee.getSettlementAccount());
        String bankAccount = decryptAccount(employee.getBankAccount());

        if (!StringUtils.hasText(settlementAccount) && StringUtils.hasText(bankAccount)) {
            settlementAccount = bankAccount;
            if (!StringUtils.hasText(settlementType)) {
                settlementType = SettlementAccountType.BANK_CARD.getCode();
            }
        }
        if (!StringUtils.hasText(settlementType) && StringUtils.hasText(settlementAccount)) {
            settlementType = inferSettlementType(settlementAccount);
        }

        if (!StringUtils.hasText(settlementAccount)) {
            String fallbackAlipayAccount = resolveFallbackAlipayAccount(employee);
            if (StringUtils.hasText(fallbackAlipayAccount)) {
                settlementAccount = fallbackAlipayAccount;
                settlementType = SettlementAccountType.ALIPAY.getCode();
            }
        }

        if (!StringUtils.hasText(settlementAccount)) {
            return RecipientRouteResult.failed("ACCOUNT_MISSING", "缺少收款账号（结算账户/银行卡/手机号/邮箱）");
        }

        String accountType = StringUtils.hasText(settlementType) ? settlementType : SettlementAccountType.BANK_CARD.getCode();
        String paymentMethod = resolvePaymentMethod(accountType);

        if (EmploymentType.PART_TIME.getCode().equals(employmentType)) {
            if (!SettlementAccountType.ALIPAY.getCode().equals(accountType)) {
                return RecipientRouteResult.failed(
                        accountType,
                        settlementAccount,
                        paymentMethod,
                        "yunzhanghu",
                        "ACCOUNT_TYPE_UNSUPPORTED",
                        "灵活用工仅支持支付宝收款账户"
                );
            }
            return RecipientRouteResult.supported(accountType, settlementAccount, "ALIPAY", "yunzhanghu");
        }

        if (EmploymentType.FULL_TIME.getCode().equals(employmentType)) {
            if (SettlementAccountType.ALIPAY.getCode().equals(accountType)) {
                return RecipientRouteResult.supported(accountType, settlementAccount, "ALIPAY", "alipay");
            }
            if (SettlementAccountType.BANK_CARD.getCode().equals(accountType)) {
                return RecipientRouteResult.supported(accountType, settlementAccount, "BANK_CARD", "alipay");
            }
            return RecipientRouteResult.failed(
                    accountType,
                    settlementAccount,
                    paymentMethod,
                    "alipay",
                    "ACCOUNT_TYPE_UNSUPPORTED",
                    "全职用工仅支持支付宝或银行卡收款账户"
            );
        }

        return RecipientRouteResult.failed(
                accountType,
                settlementAccount,
                paymentMethod,
                "unknown",
                "EMPLOYMENT_TYPE_UNSUPPORTED",
                "不支持的用工类型: " + employmentType
        );
    }

    private String resolveEmploymentType(PayrollBatch payrollBatch, PayrollLine line, Employee employee) {
        String rawEmploymentType = line != null ? line.getEmploymentType() : null;
        if (!StringUtils.hasText(rawEmploymentType) && employee != null) {
            rawEmploymentType = employee.getEmploymentType();
        }
        if (!StringUtils.hasText(rawEmploymentType) && payrollBatch != null) {
            rawEmploymentType = payrollBatch.getType();
        }
        return normalizeEmploymentType(rawEmploymentType);
    }

    private String normalizeEmploymentType(String employmentType) {
        if (!StringUtils.hasText(employmentType)) {
            return null;
        }
        String normalized = employmentType.trim().toLowerCase();
        return switch (normalized) {
            case "fulltime", "full-time", "full_time" -> EmploymentType.FULL_TIME.getCode();
            case "parttime", "part-time", "part_time" -> EmploymentType.PART_TIME.getCode();
            default -> normalized;
        };
    }

    private String resolveFallbackAlipayAccount(Employee employee) {
        if (employee == null) {
            return null;
        }
        // 仅使用员工联系方式兜底，避免误将第三方平台账号（如企业微信subject_id）当作支付宝收款账号。
        if (StringUtils.hasText(employee.getPhone())) {
            return employee.getPhone().trim();
        }
        if (StringUtils.hasText(employee.getEmail())) {
            return employee.getEmail().trim();
        }
        return null;
    }

    private String inferSettlementType(String account) {
        if (!StringUtils.hasText(account)) {
            return SettlementAccountType.BANK_CARD.getCode();
        }
        if (ValidationUtils.isValidPhone(account) || ValidationUtils.isValidEmail(account)) {
            return SettlementAccountType.ALIPAY.getCode();
        }
        return SettlementAccountType.BANK_CARD.getCode();
    }

    private String normalizeSettlementType(String type) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        String normalized = type.trim().toLowerCase();
        return switch (normalized) {
            case "bank", "bankcard", "bank_card" -> SettlementAccountType.BANK_CARD.getCode();
            case "alipay" -> SettlementAccountType.ALIPAY.getCode();
            case "wechat", "weixin", "wx" -> SettlementAccountType.WECHAT.getCode();
            case "other" -> SettlementAccountType.OTHER.getCode();
            default -> normalized;
        };
    }

    private String resolvePaymentMethod(String accountType) {
        return switch (accountType) {
            case "alipay" -> "ALIPAY";
            case "bank_card" -> "BANK_CARD";
            case "wechat" -> "WECHAT";
            case "other" -> "OTHER";
            default -> "UNKNOWN";
        };
    }

    private String decryptAccount(String encryptedValue) {
        if (!StringUtils.hasText(encryptedValue)) {
            return null;
        }
        try {
            return encryptionService.decrypt(encryptedValue);
        } catch (Exception ex) {
            log.warn("解密收款账户失败，按明文兜底处理: {}", ex.getMessage());
            return encryptedValue;
        }
    }

    private record RecipientRouteResult(
            String accountType,
            String recipientAccount,
            String paymentMethod,
            String providerCode,
            String errorCode,
            String errorMsg
    ) {
        private static RecipientRouteResult supported(String accountType,
                                                      String recipientAccount,
                                                      String paymentMethod,
                                                      String providerCode) {
            return new RecipientRouteResult(accountType, recipientAccount, paymentMethod, providerCode, null, null);
        }

        private static RecipientRouteResult failed(String errorCode, String errorMsg) {
            return new RecipientRouteResult(null, null, "UNKNOWN", "unknown", errorCode, errorMsg);
        }

        private static RecipientRouteResult failed(String accountType,
                                                   String recipientAccount,
                                                   String paymentMethod,
                                                   String providerCode,
                                                   String errorCode,
                                                   String errorMsg) {
            return new RecipientRouteResult(accountType, recipientAccount, paymentMethod, providerCode, errorCode, errorMsg);
        }

        private boolean supported() {
            return StringUtils.hasText(recipientAccount) && !StringUtils.hasText(errorCode);
        }
    }

    private String hashIdCard(Employee employee) {
        if (employee == null || !StringUtils.hasText(employee.getEncryptedIdCard())) {
            return null;
        }
        String idCardSource = employee.getEncryptedIdCard();
        try {
            String plainIdCard = encryptionService.decryptIdCard(employee.getEncryptedIdCard());
            if (StringUtils.hasText(plainIdCard)) {
                idCardSource = plainIdCard;
            }
        } catch (Exception e) {
            log.warn("解密身份证失败，降级使用密文计算哈希: employeeId={}, msg={}",
                    employee.getId(), e.getMessage());
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(idCardSource.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("计算身份证哈希失败: {}", e.getMessage());
            return null;
        }
    }
}
