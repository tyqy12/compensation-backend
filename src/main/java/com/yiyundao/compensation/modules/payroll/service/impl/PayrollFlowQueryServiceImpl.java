package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.common.response.PageResponse;
import com.yiyundao.compensation.enums.PayrollDistributionStatus;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollDistributionDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollDistributionItemDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollReconciliationTaskDto;
import com.yiyundao.compensation.infrastructure.dao.PayrollDistributionItemMapper;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollApprovalProjection;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistribution;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistributionItem;
import com.yiyundao.compensation.modules.payroll.entity.PayrollReconciliationTask;
import com.yiyundao.compensation.modules.payroll.service.PayrollApprovalProjectionService;
import com.yiyundao.compensation.modules.payroll.service.PayrollDistributionService;
import com.yiyundao.compensation.modules.payroll.service.PayrollFlowQueryService;
import com.yiyundao.compensation.modules.payroll.service.PayrollReconciliationTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PayrollFlowQueryServiceImpl implements PayrollFlowQueryService {

    private final PayrollDistributionService payrollDistributionService;
    private final PayrollReconciliationTaskService reconciliationTaskService;
    private final PayrollDistributionItemMapper payrollDistributionItemMapper;
    private final PayrollApprovalProjectionService approvalProjectionService;
    private final PaymentBatchService paymentBatchService;
    private final PaymentRecordService paymentRecordService;
    private final PayrollBatchMapper payrollBatchMapper;

    @Override
    public PageResponse<PayrollDistributionDto> pageDistributions(Integer page,
                                                                  Integer size,
                                                                  Long batchId,
                                                                  Integer batchRevision,
                                                                  String distributionStatus) {
        Page<PayrollDistribution> pageParam = new Page<>(safePage(page), safeSize(size));
        LambdaQueryWrapper<PayrollDistribution> wrapper = new LambdaQueryWrapper<>();
        if (batchId != null) {
            wrapper.eq(PayrollDistribution::getBatchId, batchId);
        }
        if (batchRevision != null && batchRevision > 0) {
            wrapper.eq(PayrollDistribution::getBatchRevision, batchRevision);
        }
        if (StringUtils.hasText(distributionStatus)) {
            String normalizedStatus = distributionStatus.trim();
            PayrollDistributionStatus status = PayrollDistributionStatus.fromCode(normalizedStatus);
            if (status == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "无效的发放状态: " + distributionStatus);
            }
            wrapper.eq(PayrollDistribution::getDistributionStatus, status);
        }
        wrapper.orderByDesc(PayrollDistribution::getUpdateTime).orderByDesc(PayrollDistribution::getId);

        Page<PayrollDistribution> result = payrollDistributionService.page(pageParam, wrapper);
        List<PayrollDistribution> records = result.getRecords();
        return PageResponse.of(result, buildDistributionDtos(records));
    }

    @Override
    public PayrollDistributionDto getDistributionDetail(Long distributionId) {
        PayrollDistribution distribution = payrollDistributionService.getById(distributionId);
        if (distribution == null) {
            return null;
        }
        return buildDistributionDtos(List.of(distribution)).stream().findFirst().orElse(null);
    }

    @Override
    public List<PayrollDistributionItemDto> listDistributionItems(Long distributionId) {
        List<PayrollDistributionItem> items = payrollDistributionItemMapper.selectList(new LambdaQueryWrapper<PayrollDistributionItem>()
                .eq(PayrollDistributionItem::getDistributionId, distributionId)
                .orderByAsc(PayrollDistributionItem::getId));
        if (items.isEmpty()) {
            return List.of();
        }
        Set<Long> paymentRecordIds = items.stream()
                .map(PayrollDistributionItem::getPaymentRecordId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, PaymentRecord> paymentRecordMap = paymentRecordIds.isEmpty()
                ? Map.of()
                : paymentRecordService.listByIds(paymentRecordIds).stream()
                .collect(Collectors.toMap(PaymentRecord::getId, Function.identity()));
        return items.stream()
                .map(item -> toDistributionItemDto(item, paymentRecordMap.get(item.getPaymentRecordId())))
                .toList();
    }

    @Override
    public PayrollReconciliationTaskDto getDistributionReconciliation(Long distributionId) {
        PayrollReconciliationTask task = reconciliationTaskService.getOne(new LambdaQueryWrapper<PayrollReconciliationTask>()
                .eq(PayrollReconciliationTask::getDistributionId, distributionId)
                .orderByDesc(PayrollReconciliationTask::getUpdateTime)
                .orderByDesc(PayrollReconciliationTask::getId)
                .last("limit 1"));
        if (task == null) {
            return null;
        }
        return buildReconciliationDtos(List.of(task)).stream().findFirst().orElse(null);
    }

    @Override
    public PageResponse<PayrollReconciliationTaskDto> pageReconciliations(Integer page,
                                                                          Integer size,
                                                                          Long batchId,
                                                                          Integer batchRevision,
                                                                          Long distributionId,
                                                                          String taskStatus,
                                                                          String result) {
        Page<PayrollReconciliationTask> pageParam = new Page<>(safePage(page), safeSize(size));
        LambdaQueryWrapper<PayrollReconciliationTask> wrapper = new LambdaQueryWrapper<>();

        if (distributionId != null) {
            wrapper.eq(PayrollReconciliationTask::getDistributionId, distributionId);
        } else if (batchId != null) {
            List<Long> distributionIds = payrollDistributionService.list(new LambdaQueryWrapper<PayrollDistribution>()
                            .eq(PayrollDistribution::getBatchId, batchId)
                            .eq(batchRevision != null && batchRevision > 0,
                                    PayrollDistribution::getBatchRevision,
                                    batchRevision)
                            .select(PayrollDistribution::getId))
                    .stream()
                    .map(PayrollDistribution::getId)
                    .toList();
            if (distributionIds.isEmpty()) {
                return PageResponse.of(List.of(), safePage(page), safeSize(size), 0);
            }
            wrapper.in(PayrollReconciliationTask::getDistributionId, distributionIds);
        }

        if (StringUtils.hasText(taskStatus)) {
            wrapper.eq(PayrollReconciliationTask::getTaskStatus, taskStatus.trim().toUpperCase());
        }
        if (StringUtils.hasText(result)) {
            wrapper.eq(PayrollReconciliationTask::getResult, result.trim().toUpperCase());
        }
        wrapper.orderByDesc(PayrollReconciliationTask::getUpdateTime)
                .orderByDesc(PayrollReconciliationTask::getId);

        Page<PayrollReconciliationTask> resultPage = reconciliationTaskService.page(pageParam, wrapper);
        return PageResponse.of(resultPage, buildReconciliationDtos(resultPage.getRecords()));
    }

    @Override
    public PayrollReconciliationTaskDto getReconciliationDetail(Long reconciliationTaskId) {
        PayrollReconciliationTask task = reconciliationTaskService.getById(reconciliationTaskId);
        if (task == null) {
            return null;
        }
        return buildReconciliationDtos(List.of(task)).stream().findFirst().orElse(null);
    }

    private List<PayrollDistributionDto> buildDistributionDtos(List<PayrollDistribution> distributions) {
        if (distributions == null || distributions.isEmpty()) {
            return List.of();
        }
        Set<Long> batchIds = distributions.stream()
                .map(PayrollDistribution::getBatchId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Set<Long> distributionIds = distributions.stream()
                .map(PayrollDistribution::getId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        Map<Long, PayrollBatch> batchMap = batchIds.isEmpty()
                ? Map.of()
                : payrollBatchMapper.selectBatchIds(batchIds).stream()
                .collect(Collectors.toMap(PayrollBatch::getId, Function.identity()));
        Map<Long, PayrollApprovalProjection> projectionMap = distributionIds.isEmpty()
                ? Map.of()
                : approvalProjectionService.list(new LambdaQueryWrapper<PayrollApprovalProjection>()
                        .in(PayrollApprovalProjection::getDistributionId, distributionIds)
                        .orderByDesc(PayrollApprovalProjection::getUpdateTime)
                        .orderByDesc(PayrollApprovalProjection::getId))
                .stream()
                .collect(Collectors.toMap(PayrollApprovalProjection::getDistributionId,
                        Function.identity(),
                        (left, right) -> left));
        Map<Long, PaymentBatch> paymentBatchMap = distributionIds.isEmpty()
                ? Map.of()
                : paymentBatchService.list(new LambdaQueryWrapper<PaymentBatch>()
                        .in(PaymentBatch::getDistributionId, distributionIds)
                        .orderByDesc(PaymentBatch::getUpdateTime)
                        .orderByDesc(PaymentBatch::getId))
                .stream()
                .collect(Collectors.toMap(PaymentBatch::getDistributionId,
                        Function.identity(),
                        (left, right) -> left));
        Map<Long, PayrollReconciliationTask> reconciliationMap = distributionIds.isEmpty()
                ? Map.of()
                : reconciliationTaskService.list(new LambdaQueryWrapper<PayrollReconciliationTask>()
                        .in(PayrollReconciliationTask::getDistributionId, distributionIds)
                        .orderByDesc(PayrollReconciliationTask::getUpdateTime)
                        .orderByDesc(PayrollReconciliationTask::getId))
                .stream()
                .collect(Collectors.toMap(PayrollReconciliationTask::getDistributionId,
                        Function.identity(),
                        (left, right) -> left));

        return distributions.stream()
                .map(distribution -> toDistributionDto(distribution,
                        batchMap.get(distribution.getBatchId()),
                        projectionMap.get(distribution.getId()),
                        paymentBatchMap.get(distribution.getId()),
                        reconciliationMap.get(distribution.getId())))
                .toList();
    }

    private List<PayrollReconciliationTaskDto> buildReconciliationDtos(List<PayrollReconciliationTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        Set<Long> distributionIds = tasks.stream()
                .map(PayrollReconciliationTask::getDistributionId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, PayrollDistribution> distributionMap = distributionIds.isEmpty()
                ? Map.of()
                : payrollDistributionService.listByIds(distributionIds).stream()
                .collect(Collectors.toMap(PayrollDistribution::getId, Function.identity()));
        Set<Long> batchIds = distributionMap.values().stream()
                .map(PayrollDistribution::getBatchId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, PayrollBatch> batchMap = batchIds.isEmpty()
                ? Collections.emptyMap()
                : payrollBatchMapper.selectBatchIds(batchIds).stream()
                .collect(Collectors.toMap(PayrollBatch::getId, Function.identity()));

        return tasks.stream()
                .map(task -> {
                    PayrollDistribution distribution = distributionMap.get(task.getDistributionId());
                    PayrollBatch batch = distribution == null ? null : batchMap.get(distribution.getBatchId());
                    return toReconciliationDto(task, distribution, batch);
                })
                .toList();
    }

    private PayrollDistributionDto toDistributionDto(PayrollDistribution distribution,
                                                     PayrollBatch batch,
                                                     PayrollApprovalProjection projection,
                                                     PaymentBatch paymentBatch,
                                                     PayrollReconciliationTask task) {
        PayrollDistributionDto dto = new PayrollDistributionDto();
        dto.setId(distribution.getId());
        dto.setDistributionNo(distribution.getDistributionNo());
        dto.setBatchId(distribution.getBatchId());
        dto.setBatchRevision(distribution.getBatchRevision());
        dto.setDistributionStatus(distribution.getDistributionStatus() != null
                ? distribution.getDistributionStatus().getCode() : null);
        dto.setTotalAmount(distribution.getTotalAmount());
        dto.setTotalCount(distribution.getTotalCount());
        dto.setScheduledDate(distribution.getScheduledDate());
        dto.setRetryLimit(distribution.getRetryLimit());
        dto.setAllowPartial(distribution.getAllowPartial());
        dto.setActualAmount(distribution.getActualAmount());
        dto.setSuccessCount(distribution.getSuccessCount());
        dto.setFailedCount(distribution.getFailedCount());
        dto.setCurrentAttempt(distribution.getCurrentAttempt());
        dto.setApprovalWorkflowId(distribution.getApprovalWorkflowId());
        dto.setCreateTime(distribution.getCreateTime());
        dto.setUpdateTime(distribution.getUpdateTime());

        if (batch != null) {
            dto.setPeriodLabel(batch.getPeriodLabel());
            dto.setPayrollType(batch.getType());
            dto.setSettlementProviderCode(batch.getSettlementProviderCode());
            if (!StringUtils.hasText(dto.getPaymentBatchNo())) {
                dto.setPaymentBatchNo(batch.getPaymentBatchNo());
            }
        }
        if (paymentBatch != null && StringUtils.hasText(paymentBatch.getBatchNo())) {
            dto.setPaymentBatchNo(paymentBatch.getBatchNo());
        }
        if (projection != null) {
            dto.setApprovalStatus(projection.getBusinessStatus());
            dto.setApprovalResult(projection.getResult());
            dto.setApprovalSubmittedAt(projection.getSubmittedAt());
            dto.setApprovalCompletedAt(projection.getCompletedAt());
            if (dto.getApprovalWorkflowId() == null) {
                dto.setApprovalWorkflowId(projection.getWorkflowId());
            }
        }
        if (task != null) {
            dto.setReconciliationTaskId(task.getId());
            dto.setReconciliationTaskStatus(task.getTaskStatus());
            dto.setReconciliationResult(task.getResult());
            dto.setReconciliationDifference(task.getDifference());
        }
        return dto;
    }

    private PayrollDistributionItemDto toDistributionItemDto(PayrollDistributionItem item, PaymentRecord paymentRecord) {
        PayrollDistributionItemDto dto = new PayrollDistributionItemDto();
        dto.setId(item.getId());
        dto.setDistributionId(item.getDistributionId());
        dto.setEmployeeId(item.getEmployeeId());
        dto.setLineId(item.getLineId());
        dto.setEmployeeName(item.getEmployeeName());
        dto.setRecipientName(item.getRecipientName());
        dto.setAccountNoMasked(item.getAccountNoMasked());
        dto.setAccountType(item.getAccountType());
        dto.setPaymentMethod(item.getPaymentMethod());
        dto.setProviderCode(item.getProviderCode());
        dto.setAmount(item.getAmount());
        dto.setItemStatus(item.getItemStatus() != null ? item.getItemStatus().getCode() : null);
        dto.setPaymentRecordId(item.getPaymentRecordId());
        dto.setRetryCount(item.getRetryCount());
        dto.setFailureReason(item.getFailureReason());
        dto.setCreateTime(item.getCreateTime());
        dto.setUpdateTime(item.getUpdateTime());
        if (paymentRecord != null) {
            dto.setPaymentRecordStatus(paymentRecord.getStatus() != null ? paymentRecord.getStatus().getCode() : null);
            dto.setProviderOrderNo(paymentRecord.getProviderOrderNo());
            dto.setProviderTradeNo(paymentRecord.getProviderTradeNo());
            dto.setErrorCode(paymentRecord.getErrorCode());
            dto.setErrorMsg(paymentRecord.getErrorMsg());
            dto.setPaymentTime(paymentRecord.getPaymentTime());
        }
        return dto;
    }

    private PayrollReconciliationTaskDto toReconciliationDto(PayrollReconciliationTask task,
                                                             PayrollDistribution distribution,
                                                             PayrollBatch batch) {
        PayrollReconciliationTaskDto dto = new PayrollReconciliationTaskDto();
        dto.setId(task.getId());
        dto.setDistributionId(task.getDistributionId());
        dto.setTaskStatus(task.getTaskStatus());
        dto.setExpectedAmount(task.getExpectedAmount());
        dto.setActualAmount(task.getActualAmount());
        dto.setDifference(task.getDifference());
        dto.setResult(task.getResult());
        dto.setDifferenceDetail(task.getDifferenceDetail());
        dto.setCreateTime(task.getCreateTime());
        dto.setUpdateTime(task.getUpdateTime());
        if (distribution != null) {
            dto.setDistributionNo(distribution.getDistributionNo());
            dto.setDistributionStatus(distribution.getDistributionStatus() != null
                    ? distribution.getDistributionStatus().getCode() : null);
            dto.setBatchId(distribution.getBatchId());
            dto.setBatchRevision(distribution.getBatchRevision());
        }
        if (batch != null) {
            dto.setPeriodLabel(batch.getPeriodLabel());
            dto.setPayrollType(batch.getType());
        }
        return dto;
    }

    private int safePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int safeSize(Integer size) {
        return size == null || size < 1 ? 10 : Math.min(size, 200);
    }
}
