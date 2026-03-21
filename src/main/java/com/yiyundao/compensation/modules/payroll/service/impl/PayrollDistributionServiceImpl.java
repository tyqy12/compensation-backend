package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentBatchProcessStatus;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.enums.PayrollDistributionItemStatus;
import com.yiyundao.compensation.enums.PayrollDistributionStatus;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollDistributionItemMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollDistributionMapper;
import com.yiyundao.compensation.infrastructure.dao.EmployeeMapper;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistribution;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistributionItem;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.PayrollDistributionService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.payroll.service.PayrollReconciliationTaskService;
import com.yiyundao.compensation.modules.payroll.support.PayrollDistributionRoutingSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollDistributionServiceImpl
        extends ServiceImpl<PayrollDistributionMapper, PayrollDistribution>
        implements PayrollDistributionService {

    private static final DateTimeFormatter NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PayrollBatchMapper payrollBatchMapper;
    private final PayrollDistributionItemMapper distributionItemMapper;
    private final PayrollLineService payrollLineService;
    private final EmployeeMapper employeeMapper;
    private final PaymentRecordService paymentRecordService;
    private final PayrollDistributionRoutingSupport routingSupport;
    private final PayrollReconciliationTaskService reconciliationTaskService;

    @Override
    @Transactional
    public PayrollDistribution createOrReuseForBatch(PayrollBatch batch) {
        if (batch == null || batch.getId() == null) {
            return null;
        }
        int batchRevision = normalizeRevision(batch.getBatchRevision());
        supersedeObsolete(batch.getId(), batchRevision);

        PayrollDistribution distribution = getByBatchIdAndRevision(batch.getId(), batchRevision);
        if (distribution == null) {
            distribution = new PayrollDistribution();
            distribution.setDistributionNo(buildDistributionNo(batch.getId(), batchRevision));
            distribution.setBatchId(batch.getId());
            distribution.setBatchRevision(batchRevision);
            distribution.setRetryLimit(3);
            distribution.setAllowPartial(Boolean.FALSE);
            distribution.setCurrentAttempt(0);
            distribution.setDistributionStatus(PayrollDistributionStatus.PLANNED);
        }

        List<PayrollLine> lines = payrollLineService.list(new LambdaQueryWrapper<PayrollLine>()
                .eq(PayrollLine::getBatchId, batch.getId())
                .orderByAsc(PayrollLine::getId));
        distribution.setTotalCount(lines.size());
        distribution.setTotalAmount(lines.stream()
                .map(PayrollLine::getNetAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        if (distribution.getScheduledDate() == null) {
            distribution.setScheduledDate(LocalDate.now());
        }
        if (distribution.getActualAmount() == null) {
            distribution.setActualAmount(BigDecimal.ZERO);
        }
        if (distribution.getSuccessCount() == null) {
            distribution.setSuccessCount(0);
        }
        if (distribution.getFailedCount() == null) {
            distribution.setFailedCount(0);
        }

        if (distribution.getId() == null) {
            save(distribution);
        } else {
            updateById(distribution);
        }
        createOrRefreshItems(distribution);
        return distribution;
    }

    @Override
    public PayrollDistribution getByBatchIdAndRevision(Long batchId, Integer batchRevision) {
        return getOne(new LambdaQueryWrapper<PayrollDistribution>()
                .eq(PayrollDistribution::getBatchId, batchId)
                .eq(PayrollDistribution::getBatchRevision, normalizeRevision(batchRevision))
                .last("limit 1"));
    }

    @Override
    @Transactional
    public List<PayrollDistributionItem> createOrRefreshItems(PayrollDistribution distribution) {
        if (distribution == null || distribution.getId() == null) {
            return List.of();
        }
        PayrollBatch batch = payrollBatchMapper.selectById(distribution.getBatchId());
        List<PayrollLine> lines = payrollLineService.list(new LambdaQueryWrapper<PayrollLine>()
                .eq(PayrollLine::getBatchId, distribution.getBatchId())
                .orderByAsc(PayrollLine::getId));
        if (CollectionUtils.isEmpty(lines)) {
            return List.of();
        }

        Map<Long, Employee> employeeMap = loadEmployees(lines);
        Map<Long, PayrollDistributionItem> existingByLineId = loadExistingItemMap(distribution.getId());
        Set<Long> keepIds = new HashSet<>();
        int failedSnapshots = 0;
        for (PayrollLine line : lines) {
            PayrollDistributionItem item = existingByLineId.get(line.getId());
            if (item == null) {
                item = new PayrollDistributionItem();
                item.setDistributionId(distribution.getId());
                item.setEmployeeId(line.getEmployeeId());
                item.setLineId(line.getId());
                item.setRetryCount(0);
            }
            item.setAmount(line.getNetAmount() == null ? BigDecimal.ZERO : line.getNetAmount());
            Employee employee = employeeMap.get(line.getEmployeeId());
            PayrollDistributionRoutingSupport.RouteSnapshot snapshot = routingSupport.buildSnapshot(batch, line, employee);
            if (snapshot.supported()) {
                item.setEmployeeName(snapshot.employeeName());
                item.setRecipientName(snapshot.recipientName());
                item.setAccountNoEncrypted(snapshot.accountNoEncrypted());
                item.setAccountNoMasked(snapshot.accountNoMasked());
                item.setAccountType(snapshot.accountType());
                item.setPaymentMethod(snapshot.paymentMethod());
                item.setProviderCode(snapshot.providerCode());
                if (item.getItemStatus() == null || item.getItemStatus() == PayrollDistributionItemStatus.FAILED) {
                    item.setItemStatus(PayrollDistributionItemStatus.PENDING);
                }
                if (item.getRetryCount() == null) {
                    item.setRetryCount(0);
                }
                item.setFailureReason(null);
            } else {
                failedSnapshots++;
                item.setEmployeeName(employee != null ? employee.getName() : null);
                item.setRecipientName(employee != null ? employee.getName() : null);
                item.setItemStatus(PayrollDistributionItemStatus.FAILED);
                item.setFailureReason(snapshot.failureReason());
                item.setRetryCount(distribution.getRetryLimit() == null ? 3 : distribution.getRetryLimit());
                item.setPaymentMethod("UNKNOWN");
                item.setProviderCode(null);
            }
            if (item.getId() == null) {
                distributionItemMapper.insert(item);
            } else {
                distributionItemMapper.updateById(item);
            }
            keepIds.add(item.getId());
        }
        deleteStaleItems(distribution.getId(), keepIds);

        if (failedSnapshots > 0) {
            log.warn("发放快照构建存在 {} 条不可发放明细: distributionId={}", failedSnapshots, distribution.getId());
        }
        return listActiveItems(distribution.getId());
    }

    @Override
    public List<PayrollDistributionItem> listActiveItems(Long distributionId) {
        return distributionItemMapper.selectList(new LambdaQueryWrapper<PayrollDistributionItem>()
                .eq(PayrollDistributionItem::getDistributionId, distributionId)
                .orderByAsc(PayrollDistributionItem::getId));
    }

    @Override
    public List<PayrollDistributionItem> listRetryableItems(Long distributionId) {
        return distributionItemMapper.selectList(new LambdaQueryWrapper<PayrollDistributionItem>()
                .eq(PayrollDistributionItem::getDistributionId, distributionId)
                .eq(PayrollDistributionItem::getItemStatus, PayrollDistributionItemStatus.FAILED)
                .orderByAsc(PayrollDistributionItem::getId));
    }

    @Override
    @Transactional
    public void bindApprovalWorkflow(Long distributionId, Long workflowId) {
        update(new LambdaUpdateWrapper<PayrollDistribution>()
                .eq(PayrollDistribution::getId, distributionId)
                .set(PayrollDistribution::getApprovalWorkflowId, workflowId));
    }

    @Override
    @Transactional
    public void syncFromPaymentBatch(PaymentBatch paymentBatch) {
        if (paymentBatch == null || paymentBatch.getDistributionId() == null) {
            return;
        }
        PayrollDistribution distribution = getById(paymentBatch.getDistributionId());
        if (distribution == null) {
            return;
        }

        Map<Long, PayrollDistributionItem> itemByPaymentRecordId = new HashMap<>();
        List<PayrollDistributionItem> items = listActiveItems(distribution.getId());
        for (PayrollDistributionItem item : items) {
            if (item.getPaymentRecordId() != null) {
                itemByPaymentRecordId.put(item.getPaymentRecordId(), item);
            }
        }

        List<PaymentRecord> paymentRecords = paymentRecordService.getByBatchNo(paymentBatch.getBatchNo(), null);
        for (PaymentRecord record : paymentRecords) {
            PayrollDistributionItem item = itemByPaymentRecordId.get(record.getId());
            if (item == null) {
                continue;
            }
            if (record.getStatus() == PaymentStatus.SUCCESS) {
                item.setItemStatus(PayrollDistributionItemStatus.SUCCESS);
                item.setFailureReason(null);
            } else if (record.getStatus() == PaymentStatus.FAILED || record.getStatus() == PaymentStatus.CANCELLED) {
                item.setItemStatus(PayrollDistributionItemStatus.FAILED);
                item.setFailureReason(record.getErrorMsg());
            } else if (record.getStatus() == PaymentStatus.PROCESSING) {
                item.setItemStatus(PayrollDistributionItemStatus.RETRYING);
            } else {
                item.setItemStatus(PayrollDistributionItemStatus.PENDING);
            }
            distributionItemMapper.updateById(item);
        }

        List<PayrollDistributionItem> allItems = listActiveItems(distribution.getId());
        int successCount = 0;
        int failedCount = 0;
        int processingCount = 0;
        BigDecimal actualAmount = BigDecimal.ZERO;
        for (PayrollDistributionItem item : allItems) {
            if (item.getItemStatus() == PayrollDistributionItemStatus.SUCCESS) {
                successCount++;
                actualAmount = actualAmount.add(item.getAmount() == null ? BigDecimal.ZERO : item.getAmount());
            } else if (item.getItemStatus() == PayrollDistributionItemStatus.FAILED) {
                failedCount++;
            } else {
                processingCount++;
            }
        }

        distribution.setSuccessCount(successCount);
        distribution.setFailedCount(failedCount);
        distribution.setActualAmount(actualAmount);
        PayrollDistributionStatus targetStatus = resolveDistributionStatus(distribution, successCount, failedCount, processingCount);
        distribution.setDistributionStatus(targetStatus);
        updateById(distribution);

        if (targetStatus == PayrollDistributionStatus.COMPLETED
                || targetStatus == PayrollDistributionStatus.PARTIALLY_COMPLETED
                || targetStatus == PayrollDistributionStatus.FAILED) {
            reconciliationTaskService.createOrRefresh(distribution);
        }
    }

    @Override
    @Transactional
    public void supersedeObsolete(Long batchId, Integer activeRevision) {
        update(new LambdaUpdateWrapper<PayrollDistribution>()
                .eq(PayrollDistribution::getBatchId, batchId)
                .ne(PayrollDistribution::getBatchRevision, normalizeRevision(activeRevision))
                .in(PayrollDistribution::getDistributionStatus,
                        PayrollDistributionStatus.PLANNED,
                        PayrollDistributionStatus.SUBMITTING,
                        PayrollDistributionStatus.PROCESSING,
                        PayrollDistributionStatus.FAILED,
                        PayrollDistributionStatus.CANCELLED)
                .set(PayrollDistribution::getDistributionStatus, PayrollDistributionStatus.SUPERSEDED));
    }

    public PaymentBatchProcessStatus mapProcessStatus(BatchStatus batchStatus, boolean partial) {
        if (batchStatus == null) {
            return null;
        }
        return switch (batchStatus) {
            case DRAFT -> PaymentBatchProcessStatus.CREATED;
            case SUBMITTED, APPROVED -> PaymentBatchProcessStatus.SUBMITTED;
            case PROCESSING -> PaymentBatchProcessStatus.PROCESSING;
            case COMPLETED -> partial ? PaymentBatchProcessStatus.PARTIAL_SUCCESS : PaymentBatchProcessStatus.SUCCESS;
            case FAILED -> PaymentBatchProcessStatus.FAILED;
        };
    }

    private PayrollDistributionStatus resolveDistributionStatus(PayrollDistribution distribution,
                                                                int successCount,
                                                                int failedCount,
                                                                int processingCount) {
        if (processingCount > 0) {
            return PayrollDistributionStatus.PROCESSING;
        }
        if (failedCount == 0) {
            return PayrollDistributionStatus.COMPLETED;
        }
        boolean hasRetryableFailure = listRetryableItems(distribution.getId()).stream()
                .anyMatch(item -> item.getRetryCount() == null || item.getRetryCount() < safeRetryLimit(distribution));
        if (successCount == 0) {
            return hasRetryableFailure ? PayrollDistributionStatus.PLANNED : PayrollDistributionStatus.FAILED;
        }
        if (Boolean.TRUE.equals(distribution.getAllowPartial())) {
            return PayrollDistributionStatus.PARTIALLY_COMPLETED;
        }
        return hasRetryableFailure ? PayrollDistributionStatus.PLANNED : PayrollDistributionStatus.PARTIALLY_COMPLETED;
    }

    private int safeRetryLimit(PayrollDistribution distribution) {
        return distribution.getRetryLimit() == null || distribution.getRetryLimit() < 1 ? 3 : distribution.getRetryLimit();
    }

    private int normalizeRevision(Integer batchRevision) {
        return batchRevision == null || batchRevision < 1 ? 1 : batchRevision;
    }

    private String buildDistributionNo(Long batchId, int batchRevision) {
        return "PD-" + batchId + "-R" + batchRevision + "-" + NO_FORMATTER.format(LocalDateTime.now());
    }

    private Map<Long, PayrollDistributionItem> loadExistingItemMap(Long distributionId) {
        List<PayrollDistributionItem> existingItems = distributionItemMapper.selectList(
                new LambdaQueryWrapper<PayrollDistributionItem>()
                        .eq(PayrollDistributionItem::getDistributionId, distributionId)
        );
        Map<Long, PayrollDistributionItem> existingByLineId = new HashMap<>();
        for (PayrollDistributionItem existing : existingItems) {
            if (existing.getLineId() != null) {
                existingByLineId.put(existing.getLineId(), existing);
            }
        }
        return existingByLineId;
    }

    private void deleteStaleItems(Long distributionId, Set<Long> keepIds) {
        List<PayrollDistributionItem> existingItems = distributionItemMapper.selectList(
                new LambdaQueryWrapper<PayrollDistributionItem>()
                        .eq(PayrollDistributionItem::getDistributionId, distributionId)
        );
        List<Long> staleIds = existingItems.stream()
                .map(PayrollDistributionItem::getId)
                .filter(id -> id != null && !keepIds.contains(id))
                .toList();
        if (!staleIds.isEmpty()) {
            for (Long staleId : staleIds) {
                distributionItemMapper.deleteById(staleId);
            }
        }
    }

    private Map<Long, Employee> loadEmployees(List<PayrollLine> lines) {
        Set<Long> employeeIds = new HashSet<>();
        for (PayrollLine line : lines) {
            if (line.getEmployeeId() != null) {
                employeeIds.add(line.getEmployeeId());
            }
        }
        if (employeeIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Employee> employeeMap = new HashMap<>();
        for (Employee employee : employeeMapper.selectBatchIds(employeeIds)) {
            if (employee != null && employee.getId() != null) {
                employeeMap.put(employee.getId(), employee);
            }
        }
        return employeeMap;
    }
}
