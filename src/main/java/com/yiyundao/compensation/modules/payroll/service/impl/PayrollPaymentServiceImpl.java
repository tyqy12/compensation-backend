package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.common.util.TransactionAfterCommitExecutor;
import com.yiyundao.compensation.common.utils.SettlementAccountPlaintextGuard;
import com.yiyundao.compensation.common.utils.ValidationUtils;
import com.yiyundao.compensation.enums.EmploymentType;
import com.yiyundao.compensation.enums.SettlementAccountType;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollDistributionItemMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollDistributionMapper;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payment.service.SettlementService;
import com.yiyundao.compensation.modules.payment.support.SettlementRouteProviderResolver;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistribution;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistributionItem;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.PayrollCalculationService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentFailureService;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentService;
import com.yiyundao.compensation.modules.payroll.support.PayrollPaymentEligibilitySupport;
import com.yiyundao.compensation.modules.payroll.support.PayrollValidationIssueSupport;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentBatchProcessStatus;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.enums.PaymentType;
import com.yiyundao.compensation.enums.PayrollDistributionItemStatus;
import com.yiyundao.compensation.enums.PayrollDistributionStatus;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final PayrollDistributionItemMapper payrollDistributionItemMapper;
    private final PayrollDistributionMapper payrollDistributionMapper;
    private final TransactionAfterCommitExecutor afterCommitExecutor;
    private final ObjectProvider<PayrollPaymentFailureService> payrollPaymentFailureServiceProvider;
    private final PayrollValidationIssueSupport validationIssueSupport;

    @Override
    @Transactional
    public PaymentBatch createPaymentBatch(PayrollBatch payrollBatch, SysUser approver, boolean triggerTransfer) {
        if (payrollBatch == null || payrollBatch.getId() == null) {
            throw new IllegalArgumentException("payrollBatch不能为空");
        }
        if (payrollBatch.getStatus() != PayrollBatchStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "仅已审批薪资批次可创建支付批次");
        }

        // 若已存在支付批次，直接返回并按需触发支付
        if (StringUtils.hasText(payrollBatch.getPaymentBatchNo())) {
            PaymentBatch existing = paymentBatchService.getByBatchNo(payrollBatch.getPaymentBatchNo());
            if (existing != null) {
                log.info("Payroll batch {} already linked to payment batch {}", payrollBatch.getId(), existing.getBatchNo());
                if (triggerTransfer && existing.getStatus() == BatchStatus.SUBMITTED) {
                    afterCommitExecutor.execute(() -> settlementService.batchTransfer(existing.getBatchNo()));
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
        PayrollPaymentEligibilitySupport.requireAllLinesFinalForPayment(payrollBatch, lines);
        PayrollPaymentEligibilitySupport.requireNoBlockingIssues(lines, validationIssueSupport::deserialize);

        String batchNo = buildBatchNo(payrollBatch);
        PayrollDistribution distribution = resolveCurrentDistribution(payrollBatch);
        List<PaymentRecord> records = new ArrayList<>();
        Map<Long, PaymentRecord> recordByLineId = new HashMap<>();
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
            if (line.getId() != null) {
                recordByLineId.put(line.getId(), record);
            }
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
        paymentBatch.setDistributionId(distribution != null ? distribution.getId() : null);
        paymentBatch.setPaymentStatus(payableCount > 0 ? PaymentBatchProcessStatus.SUBMITTED : PaymentBatchProcessStatus.FAILED);
        paymentBatch.setSubmitTime(LocalDateTime.now());
        paymentBatch.setApproverId(approver != null ? approver.getId() : null);
        if (paymentBatch.getStatus() == BatchStatus.SUBMITTED) {
            paymentBatch.setApproveTime(LocalDateTime.now());
        }
        paymentBatch.setRemark("Auto-generated from payroll batch " + payrollBatch.getId());

        PayrollBatchStatus targetStatus = payableCount > 0
                ? PayrollBatchStatus.PAY_PROCESSING
                : PayrollBatchStatus.PAY_FAILED;
        if (!reservePaymentBatchNo(payrollBatch.getId(), batchNo, targetStatus)) {
            return resolveConcurrentPaymentBatch(payrollBatch.getId());
        }

        paymentBatchService.save(paymentBatch);
        paymentRecordService.saveBatch(records);
        syncDistributionPaymentLinks(distribution, recordByLineId);

        payrollBatch.setPaymentBatchNo(batchNo);
        payrollBatch.setStatus(targetStatus);

        if (triggerTransfer && payableCount > 0) {
            afterCommitExecutor.execute(() -> settlementService.batchTransfer(batchNo));
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
        if (paymentBatch.getStatus() == BatchStatus.COMPLETED
                && paymentBatch.getPaymentStatus() != PaymentBatchProcessStatus.PARTIAL_SUCCESS) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "支付批次已完成，不能重试");
        }

        List<PaymentRecord> retryableRecords = paymentRecordService.list(new QueryWrapper<PaymentRecord>()
                .eq("batch_no", batchNo)
                .in("status", PaymentStatus.FAILED.getCode(), PaymentStatus.CANCELLED.getCode()));
        if (retryableRecords == null || retryableRecords.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "当前批次无可重试的失败记录");
        }
        boolean hasSubmittedProviderOrder = retryableRecords.stream()
                .anyMatch(this::hasProviderOrder);
        if (hasSubmittedProviderOrder) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS,
                    "存在已提交渠道的失败记录，请等待对账确认后再重试，避免重复打款"
            );
        }
        Set<Long> retryableRecordIds = retryableRecords.stream()
                .map(PaymentRecord::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (retryableRecordIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "可重试支付记录缺少ID");
        }

        boolean resetRecords = paymentRecordService.update(new UpdateWrapper<PaymentRecord>()
                .eq("batch_no", batchNo)
                .in("id", retryableRecordIds)
                .in("status", PaymentStatus.FAILED.getCode(), PaymentStatus.CANCELLED.getCode())
                .set("status", PaymentStatus.PENDING.getCode())
                .set("error_code", null)
                .set("error_msg", null)
                .set("payment_time", null)
                .set("notification_time", null)
                .set("provider_order_no", null)
                .set("provider_trade_no", null)
                .set("alipay_order_no", null)
                .set("alipay_trade_no", null));
        if (!resetRecords) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "重置失败记录状态失败");
        }

        long successCount = paymentRecordService.count(new QueryWrapper<PaymentRecord>()
                .eq("batch_no", batchNo)
                .eq("status", PaymentStatus.SUCCESS.getCode()));
        long failedCount = paymentRecordService.count(new QueryWrapper<PaymentRecord>()
                .eq("batch_no", batchNo)
                .in("status", PaymentStatus.FAILED.getCode(), PaymentStatus.CANCELLED.getCode()));

        boolean updatePaymentBatch = paymentBatchService.update(new UpdateWrapper<PaymentBatch>()
                .eq("batch_no", batchNo)
                .set("status", BatchStatus.SUBMITTED.getCode())
                .set("payment_status", PaymentBatchProcessStatus.SUBMITTED.getCode())
                .set("success_count", (int) successCount)
                .set("failed_count", (int) failedCount)
                .set("process_start_time", null)
                .set("process_end_time", null));
        if (!updatePaymentBatch) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "更新支付批次状态失败");
        }

        syncDistributionRetryState(paymentBatch, retryableRecordIds);

        payrollBatchMapper.update(null, new UpdateWrapper<PayrollBatch>()
                .eq("id", payrollBatchId)
                .set("status", PayrollBatchStatus.PAY_PROCESSING.getCode()));
        PayrollPaymentFailureService payrollPaymentFailureService = payrollPaymentFailureServiceProvider.getIfAvailable();
        if (payrollPaymentFailureService != null) {
            payrollPaymentFailureService.markResolvedByPayrollBatchId(payrollBatchId, batchNo);
        }

        if (triggerTransfer) {
            afterCommitExecutor.execute(() -> settlementService.batchTransfer(batchNo));
        }

        log.info("支付失败批次重试已触发: payrollBatchId={}, paymentBatchNo={}, retryableCount={}, triggerTransfer={}",
                payrollBatchId, batchNo, retryableRecords.size(), triggerTransfer);

        return paymentBatchService.getByBatchNo(batchNo);
    }

    private PayrollDistribution resolveCurrentDistribution(PayrollBatch payrollBatch) {
        if (payrollBatch == null || payrollBatch.getId() == null) {
            return null;
        }
        return payrollDistributionMapper.selectOne(new LambdaQueryWrapper<PayrollDistribution>()
                .eq(PayrollDistribution::getBatchId, payrollBatch.getId())
                .eq(PayrollDistribution::getBatchRevision, normalizeRevision(payrollBatch.getBatchRevision()))
                .in(PayrollDistribution::getDistributionStatus,
                        PayrollDistributionStatus.PLANNED,
                        PayrollDistributionStatus.SUBMITTING,
                        PayrollDistributionStatus.PROCESSING,
                        PayrollDistributionStatus.FAILED,
                        PayrollDistributionStatus.PARTIALLY_COMPLETED)
                .orderByDesc(PayrollDistribution::getUpdateTime)
                .orderByDesc(PayrollDistribution::getId)
                .last("limit 1"));
    }

    private void syncDistributionPaymentLinks(PayrollDistribution distribution,
                                              Map<Long, PaymentRecord> recordByLineId) {
        if (distribution == null || distribution.getId() == null || recordByLineId == null || recordByLineId.isEmpty()) {
            return;
        }

        int linkedCount = 0;
        for (Map.Entry<Long, PaymentRecord> entry : recordByLineId.entrySet()) {
            PaymentRecord record = entry.getValue();
            if (record == null || record.getId() == null) {
                continue;
            }
            PayrollDistributionItemStatus itemStatus = record.getStatus() == PaymentStatus.FAILED
                    ? PayrollDistributionItemStatus.FAILED
                    : PayrollDistributionItemStatus.PENDING;
            int updated = payrollDistributionItemMapper.update(null, new UpdateWrapper<PayrollDistributionItem>()
                    .eq("distribution_id", distribution.getId())
                    .eq("line_id", entry.getKey())
                    .set("payment_record_id", record.getId())
                    .set("item_status", itemStatus.getCode())
                    .set("failure_reason", record.getStatus() == PaymentStatus.FAILED ? record.getErrorMsg() : null)
                    .set("retry_count", record.getStatus() == PaymentStatus.FAILED
                            ? safeRetryLimit(distribution)
                            : 0));
            linkedCount += updated;
        }

        if (linkedCount == 0) {
            log.warn("支付批次未匹配到发放明细: distributionId={}, lineIds={}",
                    distribution.getId(), recordByLineId.keySet());
            return;
        }
        refreshDistributionStateAfterPaymentLink(distribution);
    }

    private void refreshDistributionStateAfterPaymentLink(PayrollDistribution distribution) {
        List<PayrollDistributionItem> items = payrollDistributionItemMapper.selectList(
                new LambdaQueryWrapper<PayrollDistributionItem>()
                        .eq(PayrollDistributionItem::getDistributionId, distribution.getId())
        );
        if (items == null || items.isEmpty()) {
            return;
        }

        int successCount = 0;
        int failedCount = 0;
        int processingCount = 0;
        BigDecimal actualAmount = BigDecimal.ZERO;
        for (PayrollDistributionItem item : items) {
            if (item.getItemStatus() == PayrollDistributionItemStatus.SUCCESS) {
                successCount++;
                actualAmount = actualAmount.add(item.getAmount() == null ? BigDecimal.ZERO : item.getAmount());
            } else if (item.getItemStatus() == PayrollDistributionItemStatus.FAILED) {
                failedCount++;
            } else {
                processingCount++;
            }
        }

        PayrollDistributionStatus targetStatus;
        if (processingCount > 0) {
            targetStatus = PayrollDistributionStatus.PROCESSING;
        } else if (failedCount == 0) {
            targetStatus = PayrollDistributionStatus.COMPLETED;
        } else if (successCount == 0) {
            targetStatus = PayrollDistributionStatus.FAILED;
        } else {
            targetStatus = PayrollDistributionStatus.PARTIALLY_COMPLETED;
        }

        payrollDistributionMapper.update(null, new UpdateWrapper<PayrollDistribution>()
                .eq("id", distribution.getId())
                .set("distribution_status", targetStatus.getCode())
                .set("current_attempt", safeCurrentAttempt(distribution) + 1)
                .set("success_count", successCount)
                .set("failed_count", failedCount)
                .set("actual_amount", actualAmount));
    }

    private boolean hasProviderOrder(PaymentRecord record) {
        return record != null
                && (StringUtils.hasText(record.getProviderOrderNo())
                || StringUtils.hasText(record.getProviderTradeNo())
                || StringUtils.hasText(record.getAlipayOrderNo())
                || StringUtils.hasText(record.getAlipayTradeNo()));
    }

    private void syncDistributionRetryState(PaymentBatch paymentBatch, Set<Long> retryableRecordIds) {
        if (paymentBatch == null || paymentBatch.getDistributionId() == null || retryableRecordIds == null
                || retryableRecordIds.isEmpty()) {
            return;
        }

        int updatedItems = payrollDistributionItemMapper.update(null, new UpdateWrapper<PayrollDistributionItem>()
                .eq("distribution_id", paymentBatch.getDistributionId())
                .in("payment_record_id", retryableRecordIds)
                .set("item_status", PayrollDistributionItemStatus.RETRYING.getCode())
                .set("failure_reason", null));
        if (updatedItems == 0) {
            log.warn("支付批次重试未匹配到发放明细: distributionId={}, paymentBatchNo={}, retryableRecordIds={}",
                    paymentBatch.getDistributionId(), paymentBatch.getBatchNo(), retryableRecordIds);
        }

        payrollDistributionMapper.update(null, new UpdateWrapper<PayrollDistribution>()
                .eq("id", paymentBatch.getDistributionId())
                .in("distribution_status",
                        PayrollDistributionStatus.PLANNED.getCode(),
                        PayrollDistributionStatus.FAILED.getCode(),
                        PayrollDistributionStatus.PARTIALLY_COMPLETED.getCode())
                .set("distribution_status", PayrollDistributionStatus.PROCESSING.getCode()));
    }

    private int safeCurrentAttempt(PayrollDistribution distribution) {
        return distribution == null || distribution.getCurrentAttempt() == null || distribution.getCurrentAttempt() < 0
                ? 0
                : distribution.getCurrentAttempt();
    }

    private int safeRetryLimit(PayrollDistribution distribution) {
        return distribution == null || distribution.getRetryLimit() == null || distribution.getRetryLimit() < 1
                ? 3
                : distribution.getRetryLimit();
    }

    private int normalizeRevision(Integer revision) {
        return revision == null || revision < 1 ? 1 : revision;
    }

    private String buildBatchNo(PayrollBatch payrollBatch) {
        String prefix = "PAYROLL-" + payrollBatch.getId();
        return prefix + "-" + BATCH_NO_FORMAT.format(LocalDateTime.now());
    }

    private boolean reservePaymentBatchNo(Long payrollBatchId, String batchNo, PayrollBatchStatus targetStatus) {
        UpdateWrapper<PayrollBatch> wrapper = new UpdateWrapper<PayrollBatch>()
                .eq("id", payrollBatchId)
                .eq("status", PayrollBatchStatus.APPROVED.getCode())
                .and(w -> w.isNull("payment_batch_no")
                        .or()
                        .eq("payment_batch_no", ""));
        wrapper.set("payment_batch_no", batchNo);
        if (targetStatus != null) {
            wrapper.set("status", targetStatus.getCode());
        }
        return payrollBatchMapper.update(null, wrapper) > 0;
    }

    private PaymentBatch resolveConcurrentPaymentBatch(Long payrollBatchId) {
        PayrollBatch latest = payrollBatchMapper.selectById(payrollBatchId);
        if (latest != null && StringUtils.hasText(latest.getPaymentBatchNo())) {
            PaymentBatch existing = paymentBatchService.getByBatchNo(latest.getPaymentBatchNo());
            if (existing != null) {
                log.info("Payroll batch {} was linked to payment batch {} by another transaction",
                        payrollBatchId, latest.getPaymentBatchNo());
                return existing;
            }
        }
        if (latest != null && latest.getStatus() != PayrollBatchStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "当前薪资批次状态不可创建支付批次: " + latest.getStatus());
        }
        throw new BusinessException(ErrorCode.INVALID_STATUS, "支付批次正在创建中，请稍后重试");
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
        AccountResolution settlementAccountResult = decryptAccount(employee.getSettlementAccount());
        if (settlementAccountResult.decryptFailed()) {
            return RecipientRouteResult.failed("ACCOUNT_DECRYPT_FAILED", "收款账号解密失败，请重新维护收款信息");
        }
        String settlementAccount = settlementAccountResult.value();
        boolean hasConfiguredAccount = settlementAccountResult.configured();

        if (!StringUtils.hasText(settlementAccount)) {
            AccountResolution bankAccountResult = decryptAccount(employee.getBankAccount());
            if (bankAccountResult.decryptFailed()) {
                return RecipientRouteResult.failed("ACCOUNT_DECRYPT_FAILED", "收款账号解密失败，请重新维护收款信息");
            }
            hasConfiguredAccount = hasConfiguredAccount || bankAccountResult.configured();
            if (StringUtils.hasText(bankAccountResult.value())) {
                settlementAccount = bankAccountResult.value();
                if (!StringUtils.hasText(settlementType)) {
                    settlementType = SettlementAccountType.BANK_CARD.getCode();
                }
            }
        }
        if (!StringUtils.hasText(settlementType) && StringUtils.hasText(settlementAccount)) {
            settlementType = inferSettlementType(settlementAccount);
        }

        if (!StringUtils.hasText(settlementAccount) && !hasConfiguredAccount) {
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
            String providerCode = SettlementRouteProviderResolver.resolveFullTimeProvider(
                    accountType, employee, payrollBatch);
            if (SettlementAccountType.ALIPAY.getCode().equals(accountType)) {
                return RecipientRouteResult.supported(accountType, settlementAccount, "ALIPAY", providerCode);
            }
            if (SettlementAccountType.BANK_CARD.getCode().equals(accountType)) {
                return RecipientRouteResult.supported(accountType, settlementAccount, "BANK_CARD", providerCode);
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

    private AccountResolution decryptAccount(String encryptedValue) {
        if (!StringUtils.hasText(encryptedValue)) {
            return AccountResolution.missing();
        }
        try {
            String plainAccount = encryptionService.decrypt(encryptedValue);
            if (StringUtils.hasText(plainAccount)) {
                return AccountResolution.resolved(plainAccount.trim());
            }
            log.warn("解密收款账户结果为空，阻断支付记录生成");
            return AccountResolution.failed();
        } catch (Exception ex) {
            if (SettlementAccountPlaintextGuard.isRecognizedPlainAccount(encryptedValue)) {
                log.warn("解密收款账户失败，按历史明文账号兼容处理: {}", ex.getMessage());
                return AccountResolution.resolved(encryptedValue.trim());
            }
            log.warn("解密收款账户失败，且原始值不像合法明文账号，阻断支付记录生成: {}", ex.getMessage());
            return AccountResolution.failed();
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

    private record AccountResolution(String value, boolean configured, boolean decryptFailed) {
        private static AccountResolution missing() {
            return new AccountResolution(null, false, false);
        }

        private static AccountResolution resolved(String value) {
            return new AccountResolution(value, true, false);
        }

        private static AccountResolution failed() {
            return new AccountResolution(null, true, true);
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
