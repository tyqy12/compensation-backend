package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.PayrollConfirmationRecordStatus;
import com.yiyundao.compensation.enums.PayrollConfirmationSheetStatus;
import com.yiyundao.compensation.enums.PayrollConfirmationStatus;
import com.yiyundao.compensation.enums.PayrollConfirmationTimeoutStrategy;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollConfirmationMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollConfirmationRecordMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollConfirmation;
import com.yiyundao.compensation.modules.payroll.entity.PayrollConfirmationRecord;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.PayrollConfirmationAggregateService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

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
public class PayrollConfirmationAggregateServiceImpl
        extends ServiceImpl<PayrollConfirmationMapper, PayrollConfirmation>
        implements PayrollConfirmationAggregateService {

    private static final DateTimeFormatter NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PayrollBatchMapper payrollBatchMapper;
    private final PayrollConfirmationRecordMapper confirmationRecordMapper;
    private final PayrollLineService payrollLineService;

    @Override
    @Transactional
    public PayrollConfirmation createOrRefreshForBatch(PayrollBatch batch) {
        if (batch == null || batch.getId() == null) {
            return null;
        }
        int batchRevision = normalizeRevision(batch.getBatchRevision());
        supersedeObsolete(batch.getId(), batchRevision);

        PayrollConfirmation confirmation = getByBatchIdAndRevision(batch.getId(), batchRevision);
        if (confirmation == null) {
            confirmation = new PayrollConfirmation();
            confirmation.setConfirmationNo(buildConfirmationNo(batch.getId(), batchRevision));
            confirmation.setBatchId(batch.getId());
            confirmation.setBatchRevision(batchRevision);
        }

        boolean requireConfirmation = !Boolean.FALSE.equals(batch.getConfirmationRequired());
        confirmation.setRequireConfirmation(requireConfirmation);
        confirmation.setPolicyId(batch.getConfirmationMode());
        confirmation.setTimeoutStrategy(PayrollConfirmationTimeoutStrategy.MANUAL_REVIEW);
        confirmation.setDeadline(requireConfirmation ? LocalDateTime.now().plusDays(3) : null);

        if (confirmation.getId() == null) {
            save(confirmation);
        } else {
            updateById(confirmation);
        }

        List<PayrollLine> lines = payrollLineService.list(new LambdaQueryWrapper<PayrollLine>()
                .eq(PayrollLine::getBatchId, batch.getId())
                .orderByAsc(PayrollLine::getId));
        Map<Long, PayrollConfirmationRecord> existingByLineId = loadExistingRecordMap(confirmation.getId());
        Set<Long> keepIds = new HashSet<>();

        int confirmedCount = 0;
        int rejectedCount = 0;
        for (PayrollLine line : lines) {
            PayrollConfirmationRecord record = existingByLineId.get(line.getId());
            if (record == null) {
                record = new PayrollConfirmationRecord();
                record.setConfirmationId(confirmation.getId());
                record.setEmployeeId(line.getEmployeeId());
                record.setLineId(line.getId());
            }

            PayrollConfirmationRecordStatus recordStatus = mapRecordStatus(line, requireConfirmation);
            record.setRecordStatus(recordStatus);
            record.setRejectReason(recordStatus == PayrollConfirmationRecordStatus.REJECTED ? line.getObjectionReason() : null);
            record.setComment(line.getConfirmationComment());
            if (recordStatus == PayrollConfirmationRecordStatus.CONFIRMED && line.getConfirmedAt() != null) {
                record.setConfirmedAt(line.getConfirmedAt());
            } else if (recordStatus == PayrollConfirmationRecordStatus.AUTO_CONFIRMED && record.getConfirmedAt() == null) {
                record.setConfirmedAt(LocalDateTime.now());
            } else if (recordStatus == PayrollConfirmationRecordStatus.PENDING || recordStatus == PayrollConfirmationRecordStatus.REJECTED) {
                record.setConfirmedAt(null);
            }

            if (record.getId() == null) {
                confirmationRecordMapper.insert(record);
            } else {
                confirmationRecordMapper.updateById(record);
            }
            keepIds.add(record.getId());

            if (recordStatus == PayrollConfirmationRecordStatus.CONFIRMED
                    || recordStatus == PayrollConfirmationRecordStatus.AUTO_CONFIRMED) {
                confirmedCount++;
            }
            if (recordStatus == PayrollConfirmationRecordStatus.REJECTED) {
                rejectedCount++;
            }
        }

        deleteStaleRecords(existingByLineId.values().stream().toList(), keepIds);

        confirmation.setTotalEmployees(lines.size());
        confirmation.setConfirmedCount(confirmedCount);
        confirmation.setRejectedCount(rejectedCount);
        confirmation.setConfirmationStatus(resolveSheetStatus(batch, requireConfirmation, lines.size(), confirmedCount, rejectedCount, lines));
        updateById(confirmation);
        return confirmation;
    }

    @Override
    public PayrollConfirmation getByBatchIdAndRevision(Long batchId, Integer batchRevision) {
        return getOne(new LambdaQueryWrapper<PayrollConfirmation>()
                .eq(PayrollConfirmation::getBatchId, batchId)
                .eq(PayrollConfirmation::getBatchRevision, normalizeRevision(batchRevision))
                .last("limit 1"));
    }

    @Override
    @Transactional
    public void skipConfirmation(Long confirmationId) {
        PayrollConfirmation confirmation = getById(confirmationId);
        if (confirmation == null) {
            return;
        }
        confirmation.setConfirmationStatus(PayrollConfirmationSheetStatus.SKIPPED);
        confirmation.setConfirmedCount(confirmation.getTotalEmployees() == null ? 0 : confirmation.getTotalEmployees());
        confirmation.setRejectedCount(0);
        updateById(confirmation);

        List<PayrollConfirmationRecord> records = confirmationRecordMapper.selectList(
                new LambdaQueryWrapper<PayrollConfirmationRecord>().eq(PayrollConfirmationRecord::getConfirmationId, confirmationId)
        );
        for (PayrollConfirmationRecord record : records) {
            record.setRecordStatus(PayrollConfirmationRecordStatus.AUTO_CONFIRMED);
            if (record.getConfirmedAt() == null) {
                record.setConfirmedAt(LocalDateTime.now());
            }
            confirmationRecordMapper.updateById(record);
        }
    }

    @Override
    @Transactional
    public void syncFromLegacyBatch(Long batchId, Integer batchRevision) {
        if (batchId == null) {
            return;
        }
        PayrollBatch batch = payrollBatchMapper.selectById(batchId);
        if (batch == null) {
            return;
        }
        if (batchRevision != null && batchRevision > 0 && batch.getBatchRevision() == null) {
            batch.setBatchRevision(batchRevision);
        }
        createOrRefreshForBatch(batch);
    }

    @Override
    public boolean isCompletedForApproval(Long batchId, Integer batchRevision) {
        PayrollConfirmation confirmation = getByBatchIdAndRevision(batchId, batchRevision);
        if (confirmation == null) {
            return false;
        }
        return confirmation.getConfirmationStatus() == PayrollConfirmationSheetStatus.CONFIRMED
                || confirmation.getConfirmationStatus() == PayrollConfirmationSheetStatus.SKIPPED;
    }

    @Override
    @Transactional
    public void supersedeObsolete(Long batchId, Integer activeRevision) {
        update(new LambdaUpdateWrapper<PayrollConfirmation>()
                .eq(PayrollConfirmation::getBatchId, batchId)
                .ne(PayrollConfirmation::getBatchRevision, normalizeRevision(activeRevision))
                .in(PayrollConfirmation::getConfirmationStatus,
                        PayrollConfirmationSheetStatus.PENDING,
                        PayrollConfirmationSheetStatus.CONFIRMING,
                        PayrollConfirmationSheetStatus.REJECTED,
                        PayrollConfirmationSheetStatus.TIMEOUT,
                        PayrollConfirmationSheetStatus.SKIPPED)
                .set(PayrollConfirmation::getConfirmationStatus, PayrollConfirmationSheetStatus.SUPERSEDED));
    }

    private Map<Long, PayrollConfirmationRecord> loadExistingRecordMap(Long confirmationId) {
        List<PayrollConfirmationRecord> existingRecords = confirmationRecordMapper.selectList(
                new LambdaQueryWrapper<PayrollConfirmationRecord>()
                        .eq(PayrollConfirmationRecord::getConfirmationId, confirmationId)
        );
        Map<Long, PayrollConfirmationRecord> existingByLineId = new HashMap<>();
        for (PayrollConfirmationRecord existing : existingRecords) {
            if (existing.getLineId() != null) {
                existingByLineId.put(existing.getLineId(), existing);
            }
        }
        return existingByLineId;
    }

    private void deleteStaleRecords(List<PayrollConfirmationRecord> existingRecords, Set<Long> keepIds) {
        if (CollectionUtils.isEmpty(existingRecords)) {
            return;
        }
        List<Long> staleIds = existingRecords.stream()
                .map(PayrollConfirmationRecord::getId)
                .filter(id -> id != null && !keepIds.contains(id))
                .toList();
        if (!staleIds.isEmpty()) {
            for (Long staleId : staleIds) {
                confirmationRecordMapper.deleteById(staleId);
            }
        }
    }

    private PayrollConfirmationRecordStatus mapRecordStatus(PayrollLine line, boolean requireConfirmation) {
        if (!requireConfirmation) {
            return PayrollConfirmationRecordStatus.AUTO_CONFIRMED;
        }
        PayrollConfirmationStatus legacyStatus = PayrollConfirmationStatus.fromCode(line.getConfirmationStatus());
        return switch (legacyStatus) {
            case CONFIRMED, OBJECTED_APPROVED -> PayrollConfirmationRecordStatus.CONFIRMED;
            case OBJECTED, OBJECTED_REJECTED -> PayrollConfirmationRecordStatus.REJECTED;
            default -> PayrollConfirmationRecordStatus.PENDING;
        };
    }

    private PayrollConfirmationSheetStatus resolveSheetStatus(PayrollBatch batch,
                                                              boolean requireConfirmation,
                                                              int totalEmployees,
                                                              int confirmedCount,
                                                              int rejectedCount,
                                                              List<PayrollLine> lines) {
        if (!requireConfirmation) {
            return PayrollConfirmationSheetStatus.SKIPPED;
        }
        if (batch != null && batch.getStatus() == PayrollBatchStatus.CONFIRMED) {
            return PayrollConfirmationSheetStatus.CONFIRMED;
        }
        if (batch != null && batch.getStatus() == PayrollBatchStatus.DISPUTE_PROCESSING) {
            return PayrollConfirmationSheetStatus.REJECTED;
        }
        if (totalEmployees <= 0) {
            return PayrollConfirmationSheetStatus.PENDING;
        }
        if (rejectedCount > 0) {
            return PayrollConfirmationSheetStatus.REJECTED;
        }
        if (confirmedCount >= totalEmployees) {
            return PayrollConfirmationSheetStatus.CONFIRMED;
        }
        boolean hasAnyStarted = lines.stream()
                .map(PayrollLine::getConfirmationStatus)
                .map(PayrollConfirmationStatus::fromCode)
                .anyMatch(status -> status != PayrollConfirmationStatus.PENDING);
        return hasAnyStarted ? PayrollConfirmationSheetStatus.CONFIRMING : PayrollConfirmationSheetStatus.PENDING;
    }

    private int normalizeRevision(Integer batchRevision) {
        return batchRevision == null || batchRevision < 1 ? 1 : batchRevision;
    }

    private String buildConfirmationNo(Long batchId, int batchRevision) {
        return "PC-" + batchId + "-R" + batchRevision + "-" + NO_FORMATTER.format(LocalDateTime.now());
    }
}
