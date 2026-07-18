package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.enums.PayrollConfirmationMode;
import com.yiyundao.compensation.enums.PayrollConfirmationStatus;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.PayrollCalculationStatus;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollLedgerDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollManagerReviewDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollPreviewDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollValidationIssueDto;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.infrastructure.dao.EmployeeMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollImportItem;
import com.yiyundao.compensation.modules.payroll.entity.SalaryItem;
import com.yiyundao.compensation.modules.payroll.entity.SalaryTemplate;
import com.yiyundao.compensation.modules.payroll.entity.SalaryTemplateVersion;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.infrastructure.dao.PayrollImportItemMapper;
import com.yiyundao.compensation.modules.payroll.service.SalaryItemService;
import com.yiyundao.compensation.modules.payroll.service.SalaryTemplateService;
import com.yiyundao.compensation.modules.payroll.service.SalaryTemplateVersionService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.modules.payroll.service.PayrollCalculationService;
import com.yiyundao.compensation.modules.payroll.service.PayrollCumulativeTaxService;
import com.yiyundao.compensation.modules.payroll.service.PayrollContributionCalculationService;
import com.yiyundao.compensation.modules.payroll.service.PayrollContributionRecordService;
import com.yiyundao.compensation.modules.payroll.compliance.CumulativeWithholdingTaxCalculator;
import com.yiyundao.compensation.modules.payroll.support.PayrollCalculationSnapshotSupport;
import com.yiyundao.compensation.modules.payroll.support.PayrollValidationIssueSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollCalculationServiceImpl implements PayrollCalculationService {

    private final PayrollBatchMapper payrollBatchMapper;
    private final PayrollImportItemMapper importItemMapper;
    private final SalaryItemService salaryItemService;
    private final SalaryTemplateService salaryTemplateService;
    private final SalaryTemplateVersionService salaryTemplateVersionService;
    private final PayrollLineService payrollLineService;
    private final EmployeeMapper employeeMapper;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final PayrollValidationIssueSupport validationIssueSupport;
    private final PayrollCalculationFailureMarker calculationFailureMarker;

    /** 累计预扣服务是新薪酬模型的唯一税额计算入口；本地离线试算仍允许注入内置政策表。 */
    private PayrollCumulativeTaxService payrollCumulativeTaxService;
    private PayrollContributionCalculationService payrollContributionCalculationService;
    private PayrollContributionRecordService payrollContributionRecordService;
    private com.yiyundao.compensation.modules.payroll.service.PayrollCalculationTraceService payrollCalculationTraceService;

    /**
     * 模板社保比例只允许用于本地影子核算。生产核算必须命中完整的参保关系和属地政策。
     */
    @Value("${payroll.compliance.allow-template-fallback:false}")
    private boolean allowTemplateContributionFallback;

    @Value("${payroll.compliance.allow-builtin-tax-fallback:false}")
    private boolean allowBuiltinTaxFallback;

    @Autowired(required = false)
    public void setPayrollCumulativeTaxService(PayrollCumulativeTaxService payrollCumulativeTaxService) {
        this.payrollCumulativeTaxService = payrollCumulativeTaxService;
    }

    @Autowired(required = false)
    public void setPayrollContributionCalculationService(
            PayrollContributionCalculationService payrollContributionCalculationService) {
        this.payrollContributionCalculationService = payrollContributionCalculationService;
    }

    @Autowired(required = false)
    public void setPayrollContributionRecordService(
            PayrollContributionRecordService payrollContributionRecordService) {
        this.payrollContributionRecordService = payrollContributionRecordService;
    }

    @Autowired(required = false)
    public void setPayrollCalculationTraceService(
            com.yiyundao.compensation.modules.payroll.service.PayrollCalculationTraceService payrollCalculationTraceService) {
        this.payrollCalculationTraceService = payrollCalculationTraceService;
    }

    @Override
    public boolean dryRun(Long batchId) {
        log.info("Dry-run payroll calculation for batch: {}", batchId);
        PayrollBatch batch = payrollBatchMapper.selectById(batchId);
        // 从数据库加载薪资模板和规则，构建内存规则模型用于计算预览
        return validateReady(batch);
    }

    @Override
    @Transactional
    public boolean computeAndSave(Long batchId) {
        log.info("Compute payroll and save for batch: {}", batchId);
        PreviewContext ctx = prepareContext(batchId, true);
        if (ctx == null) return false;
        if (!canPersist(ctx.batch)) {
            log.warn("Batch status not allowed for compute: id={} status={}", ctx.batch.getId(), ctx.batch.getStatus());
            return false;
        }

        java.util.List<PayrollLine> existingLines = payrollLineService.list(
                new LambdaQueryWrapper<PayrollLine>().eq(PayrollLine::getBatchId, batchId)
        );
        boolean revisionAdvance = existingLines != null && !existingLines.isEmpty();
        ctx.createNewRevision = revisionAdvance;
        int currentRevision = normalizeRevision(ctx.batch.getBatchRevision());
        ctx.calculationRevision = revisionAdvance ? currentRevision + 1 : currentRevision;
        ctx.batch.setBatchRevision(ctx.calculationRevision);
        LinesSummary summary = computeLines(ctx);
        pinTaxPolicyVersion(ctx);
        validatePersistedSnapshot(ctx);
        rejectBlockingIssuesBeforePersist(summary, ctx.globalIssues);
        applySnapshotMetadata(ctx);
        FailureGuard rollbackFailureGuard = FailureGuard.from(ctx.batch);
        FailureGuard calculatingFailureGuard = markBatchCalculating(ctx.batch);

        try {
            java.util.Map<Long, PayrollLine> existingByEmployee = new java.util.HashMap<>();
            int activeRevision = normalizeRevision(ctx.batch.getBatchRevision()) - (revisionAdvance ? 1 : 0);
            for (PayrollLine existing : existingLines) {
                if (existing != null && existing.getEmployeeId() != null
                        && normalizeRevision(existing.getBatchRevision()) == activeRevision) {
                    existingByEmployee.put(existing.getEmployeeId(), existing);
                }
            }

            java.util.List<PayrollLine> entities = new java.util.ArrayList<>();
            for (var entry : summary.linesByEmployee.entrySet()) {
                entities.add(toPayrollLine(ctx, entry.getKey(), entry.getValue(), existingByEmployee.get(entry.getKey())));
            }

            // Revision is append-only. The batch's batch_revision points at the active result;
            // prior payroll lines remain queryable for audit, dispute and reconciliation.

            if (!entities.isEmpty()) {
                payrollLineService.saveOrUpdateBatch(entities);
                recordTaxLedgers(ctx, entities);
                recordContributionRecords(ctx, entities);
                recordCalculationTraces(ctx, entities, summary);
            }

            updateBatchToConfirming(ctx.batch);
            return true;
        } catch (Exception ex) {
            markBatchCalculationFailedAfterRollback(ctx.batch.getId(), rollbackFailureGuard, calculatingFailureGuard);
            throw ex;
        }
    }

    private void rejectBlockingIssuesBeforePersist(LinesSummary summary,
                                                   java.util.List<PayrollValidationIssueDto> globalIssues) {
        java.util.List<PayrollValidationIssueDto> issues = new java.util.ArrayList<>();
        if (globalIssues != null) {
            issues.addAll(globalIssues);
        }
        if (summary != null && summary.orderedLines != null) {
            for (PayrollPreviewDto.PayrollPreviewLineDto line : summary.orderedLines) {
                if (line != null && line.getIssues() != null) {
                    issues.addAll(line.getIssues());
                }
            }
        }
        if (!validationIssueSupport.hasBlocking(issues)) {
            return;
        }
        java.util.List<String> messages = validationIssueSupport.toMessages(issues);
        String message = messages.isEmpty()
                ? "存在阻塞问题，暂不可落地薪资计算"
                : "存在阻塞问题，暂不可落地薪资计算：" + String.join("；", messages);
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }

    @Override
    @Transactional
    public boolean recomputeLine(Long batchId, Long employeeId) {
        log.info("Recompute payroll revision for employee: batch={}, employeeId={}", batchId, employeeId);
        if (employeeId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "员工ID不能为空");
        }
        // 工资行属于批次 revision 的完整结果集，单行重算也必须生成一套新的整批结果，
        // 避免活动 revision 同时混用新旧工资行。
        return computeAndSave(batchId);
    }

    @Override
    public boolean validateReady(PayrollBatch batch) {
        if (batch == null) return false;
        if (batch.getStatus() == null) return false;
        PayrollBatchStatus status = batch.getStatus();
        return status == PayrollBatchStatus.DRAFT ||
               status == PayrollBatchStatus.LOCKED ||
               status == PayrollBatchStatus.CONFIRMING ||
               status == PayrollBatchStatus.DISPUTE_PROCESSING ||
               status == PayrollBatchStatus.CONFIRMED ||
               status == PayrollBatchStatus.SUBMITTED ||
               status == PayrollBatchStatus.APPROVED ||
               status == PayrollBatchStatus.REJECTED;
    }

    @Override
public PayrollPreviewDto dryRunPreview(Long batchId) {
    PreviewContext ctx = prepareContext(batchId, true);
    if (ctx == null) return null;

    LinesSummary summary = computeLines(ctx);
    java.util.List<PayrollValidationIssueDto> globalIssues = ctx.globalIssues == null
            ? java.util.Collections.emptyList()
            : new java.util.ArrayList<>(ctx.globalIssues);
    java.util.List<String> globalWarnings = validationIssueSupport.toMessages(globalIssues);

    PayrollPreviewDto preview = new PayrollPreviewDto();
    preview.setBatchId(batchId);
    preview.setCurrency(ctx.batch.getCurrency());
    preview.setLines(summary.orderedLines);
    preview.setTotalEmployees(summary.orderedLines.size());
    preview.setLinesWithWarnings(summary.linesWithWarnings);
    preview.setLinesWithBlockingIssues(summary.linesWithBlockingIssues);
    preview.setTotalWarnings(summary.totalWarnings + globalWarnings.size());
    preview.setBlockingIssueCount(summary.blockingIssueCount + validationIssueSupport.countBlocking(globalIssues));
    preview.setReviewIssueCount(summary.reviewIssueCount + validationIssueSupport.countReview(globalIssues));
    preview.setHasBlockingIssues(preview.getBlockingIssueCount() != null && preview.getBlockingIssueCount() > 0);
    preview.setEarningsTotal(summary.earningsTotal);
    preview.setDeductionsTotal(summary.deductionsTotal);
    preview.setGrossTotal(summary.grossTotal);
    preview.setTaxTotal(summary.taxTotal);
    preview.setSocialTotal(summary.socialTotal);
    preview.setNetTotal(summary.netTotal);
    preview.setWarnings(globalWarnings);
    preview.setIssues(globalIssues);
    return preview;
}

    @Override
public PayrollLedgerDto ledger(Long batchId) {
    PreviewContext ctx = prepareContext(batchId, false);
    PayrollBatch batch = ctx != null ? ctx.batch : payrollBatchMapper.selectById(batchId);
    if (batch == null) {
        return null;
    }

    var persisted = payrollLineService.list(new LambdaQueryWrapper<PayrollLine>()
            .eq(PayrollLine::getBatchId, batchId)
            .eq(PayrollLine::getBatchRevision, normalizeRevision(batch.getBatchRevision()))
            .orderByAsc(PayrollLine::getEmployeeId));

    LinesSummary computedSummary = ctx != null ? computeLines(ctx) : new LinesSummary();

    java.util.List<PayrollPreviewDto.PayrollPreviewLineDto> lines = new java.util.ArrayList<>();
    BigDecimal earningsTotal = BigDecimal.ZERO;
    BigDecimal deductionsTotal = BigDecimal.ZERO;
    BigDecimal grossTotal = BigDecimal.ZERO;
    BigDecimal taxTotal = BigDecimal.ZERO;
    BigDecimal socialTotal = BigDecimal.ZERO;
    BigDecimal netTotal = BigDecimal.ZERO;
    int linesWithWarnings = 0;
    int linesWithBlockingIssues = 0;
    java.util.Set<Long> seen = new java.util.HashSet<>();

    java.util.List<PayrollValidationIssueDto> globalIssues = createLedgerGlobalIssues(ctx, !persisted.isEmpty());
    java.util.List<String> warningMessages = validationIssueSupport.toMessages(globalIssues);
    int totalWarnings = warningMessages.size();
    int blockingIssueCount = validationIssueSupport.countBlocking(globalIssues);
    int reviewIssueCount = validationIssueSupport.countReview(globalIssues);

    if (!persisted.isEmpty()) {
        java.util.Set<Long> employeeIds = new java.util.HashSet<>();
        for (PayrollLine line : persisted) {
            if (line.getEmployeeId() != null) {
                employeeIds.add(line.getEmployeeId());
            }
        }
        ensureEmployeesLoaded(ctx, employeeIds);

        for (PayrollLine stored : persisted) {
            Long empId = stored.getEmployeeId();
            seen.add(empId);
            PayrollPreviewDto.PayrollPreviewLineDto base = computedSummary.linesByEmployee.get(empId);
            PayrollPreviewDto.PayrollPreviewLineDto view = new PayrollPreviewDto.PayrollPreviewLineDto();
            view.setEmployeeId(empId);

            if (base != null) {
                view.setEmployeeNo(base.getEmployeeNo());
                view.setEmployeeName(base.getEmployeeName());
                view.setDepartment(base.getDepartment());
                view.setManagerId(base.getManagerId());
                view.setManagerName(base.getManagerName());
                view.setEmploymentType(base.getEmploymentType());
                view.setDiff(base.getDiff());
            } else if (ctx != null) {
                Employee emp = ctx.employeeMap.get(empId);
                if (emp != null) {
                    view.setEmployeeNo(emp.getEmployeeId());
                    view.setEmployeeName(emp.getName());
                    view.setDepartment(emp.getDepartment());
                    view.setManagerId(emp.getManagerId());
                    view.setEmploymentType(emp.getEmploymentType());
                    if (emp.getManagerId() != null) {
                        Employee manager = ctx.managerMap.get(emp.getManagerId());
                        if (manager != null) {
                            view.setManagerName(manager.getName());
                        }
                    }
                }
                view.setMissingItems(java.util.Collections.emptyList());
            }

            java.util.List<PayrollPreviewDto.PayrollPreviewItemDto> items = parseItemsSnapshot(stored.getItemsSnapshotJson());
            if ((items == null || items.isEmpty()) && base != null && base.getItems() != null) {
                items = base.getItems();
            }
            if (items == null) {
                items = java.util.Collections.emptyList();
            }
            view.setItems(items);

            BigDecimal earnings = BigDecimal.ZERO;
            BigDecimal deductions = BigDecimal.ZERO;
            for (var item : items) {
                if (item == null) continue;
                BigDecimal amt = safe(item.getAmount());
                if ("earning".equalsIgnoreCase(item.getType())) {
                    earnings = earnings.add(amt);
                } else {
                    deductions = deductions.add(amt);
                }
            }
            if (items.isEmpty() && base != null) {
                earnings = safe(base.getEarningsTotal());
                deductions = safe(base.getDeductionsTotal());
            }
            view.setEarningsTotal(earnings);
            view.setDeductionsTotal(deductions);

            BigDecimal gross = stored.getGrossAmount() != null ? stored.getGrossAmount() : earnings;
            BigDecimal tax = stored.getTaxAmount() != null ? stored.getTaxAmount() : (base != null ? safe(base.getTaxAmount()) : BigDecimal.ZERO);
            BigDecimal social = stored.getSocialAmount() != null ? stored.getSocialAmount() : (base != null ? safe(base.getSocialAmount()) : BigDecimal.ZERO);
            BigDecimal net = stored.getNetAmount() != null ? stored.getNetAmount()
                    : gross.subtract(deductions).subtract(tax).subtract(social);

            view.setGrossAmount(gross);
            view.setTaxAmount(tax);
            view.setSocialAmount(social);
            view.setNetAmount(net);

            java.util.List<PayrollValidationIssueDto> issues = parseIssueSnapshot(stored.getWarning());
            view.setMissingItems(java.util.Collections.emptyList());
            applyLineIssueSummary(view, issues);

            lines.add(view);
            earningsTotal = earningsTotal.add(earnings);
            deductionsTotal = deductionsTotal.add(deductions);
            grossTotal = grossTotal.add(gross);
            taxTotal = taxTotal.add(tax);
            socialTotal = socialTotal.add(social);
            netTotal = netTotal.add(net);

            int warningCount = view.getWarnings() == null ? 0 : view.getWarnings().size();
            int lineBlockingCount = view.getBlockingIssueCount() == null ? 0 : view.getBlockingIssueCount();
            int lineReviewCount = view.getReviewIssueCount() == null ? 0 : view.getReviewIssueCount();
            if (warningCount > 0 || (view.getMissingItems() != null && !view.getMissingItems().isEmpty())) {
                linesWithWarnings++;
            }
            if (Boolean.TRUE.equals(view.getHasBlockingIssues())) {
                linesWithBlockingIssues++;
            }
            totalWarnings += warningCount;
            blockingIssueCount += lineBlockingCount;
            reviewIssueCount += lineReviewCount;
        }
    }

    for (var entry : computedSummary.linesByEmployee.entrySet()) {
        Long empId = entry.getKey();
        if (empId == null || seen.contains(empId)) {
            continue;
        }
        PayrollPreviewDto.PayrollPreviewLineDto line = entry.getValue();
        if (line == null) continue;
        lines.add(line);
        earningsTotal = earningsTotal.add(safe(line.getEarningsTotal()));
        deductionsTotal = deductionsTotal.add(safe(line.getDeductionsTotal()));
        grossTotal = grossTotal.add(safe(line.getGrossAmount()));
        taxTotal = taxTotal.add(safe(line.getTaxAmount()));
        socialTotal = socialTotal.add(safe(line.getSocialAmount()));
        netTotal = netTotal.add(safe(line.getNetAmount()));
        int warningCount = line.getWarnings() == null ? 0 : line.getWarnings().size();
        int lineBlockingCount = line.getBlockingIssueCount() == null ? 0 : line.getBlockingIssueCount();
        int lineReviewCount = line.getReviewIssueCount() == null ? 0 : line.getReviewIssueCount();
        if (warningCount > 0 || (line.getMissingItems() != null && !line.getMissingItems().isEmpty())) {
            linesWithWarnings++;
        }
        if (Boolean.TRUE.equals(line.getHasBlockingIssues())) {
            linesWithBlockingIssues++;
        }
        totalWarnings += warningCount;
        blockingIssueCount += lineBlockingCount;
        reviewIssueCount += lineReviewCount;
    }

    PayrollLedgerDto dto = new PayrollLedgerDto();
    dto.setBatchId(batchId);
    dto.setBatchRevision(normalizeRevision(batch.getBatchRevision()));
    dto.setInputSnapshotHash(batch.getInputSnapshotHash());
    dto.setRuleSnapshotHash(batch.getRuleSnapshotHash());
    dto.setCalculationEngineVersion(batch.getCalculationEngineVersion());
    dto.setStatus(batch.getStatus().getCode());
    dto.setPeriodLabel(batch.getPeriodLabel());
    dto.setCurrency(batch.getCurrency());
    dto.setLines(lines);
    dto.setTotalEmployees(lines.size());
    dto.setEarningsTotal(earningsTotal);
    dto.setDeductionsTotal(deductionsTotal);
    dto.setGrossTotal(grossTotal);
    dto.setTaxTotal(taxTotal);
    dto.setSocialTotal(socialTotal);
    dto.setNetTotal(netTotal);
    dto.setLinesWithWarnings(linesWithWarnings);
    dto.setLinesWithBlockingIssues(linesWithBlockingIssues);
    dto.setTotalWarnings(totalWarnings);
    dto.setBlockingIssueCount(blockingIssueCount);
    dto.setReviewIssueCount(reviewIssueCount);
    dto.setHasBlockingIssues(blockingIssueCount > 0);
    dto.setWarnings(warningMessages);
    dto.setIssues(globalIssues);
    return dto;
}

    @Override
public PayrollManagerReviewDto managerReview(Long batchId, String department, Long managerId, String keyword) {
    PreviewContext ctx = prepareContext(batchId, false);
    if (ctx == null) {
        return null;
    }

    LinesSummary summary = computeLines(ctx);

    String deptFilter = (department == null || department.isBlank()) ? null : department.trim();
    Long managerFilter = managerId;
    String keywordFilter = (keyword == null || keyword.isBlank()) ? null : keyword.trim().toLowerCase(Locale.ROOT);

    java.util.List<PayrollValidationIssueDto> globalIssues = ctx.globalIssues == null
            ? java.util.Collections.emptyList()
            : new java.util.ArrayList<>(ctx.globalIssues);
    java.util.List<String> warningMessages = validationIssueSupport.toMessages(globalIssues);

    java.util.List<PayrollPreviewDto.PayrollPreviewLineDto> filtered = new java.util.ArrayList<>();
    BigDecimal earningsTotal = BigDecimal.ZERO;
    BigDecimal deductionsTotal = BigDecimal.ZERO;
    BigDecimal grossTotal = BigDecimal.ZERO;
    BigDecimal taxTotal = BigDecimal.ZERO;
    BigDecimal socialTotal = BigDecimal.ZERO;
    BigDecimal netTotal = BigDecimal.ZERO;
    int linesWithWarnings = 0;
    int linesWithBlockingIssues = 0;
    int totalWarnings = warningMessages.size();
    int blockingIssueCount = validationIssueSupport.countBlocking(globalIssues);
    int reviewIssueCount = validationIssueSupport.countReview(globalIssues);

    for (var line : summary.orderedLines) {
        if (line == null) continue;
        if (deptFilter != null) {
            String dept = line.getDepartment();
            if (dept == null || !dept.equalsIgnoreCase(deptFilter)) {
                continue;
            }
        }
        if (managerFilter != null) {
            if (line.getManagerId() == null || !managerFilter.equals(line.getManagerId())) {
                continue;
            }
        }
        if (keywordFilter != null) {
            boolean matched = false;
            if (line.getEmployeeName() != null && line.getEmployeeName().toLowerCase(Locale.ROOT).contains(keywordFilter)) {
                matched = true;
            }
            if (!matched && line.getEmployeeNo() != null && line.getEmployeeNo().toLowerCase(Locale.ROOT).contains(keywordFilter)) {
                matched = true;
            }
            if (!matched) {
                continue;
            }
        }

        filtered.add(line);
        earningsTotal = earningsTotal.add(safe(line.getEarningsTotal()));
        deductionsTotal = deductionsTotal.add(safe(line.getDeductionsTotal()));
        grossTotal = grossTotal.add(safe(line.getGrossAmount()));
        taxTotal = taxTotal.add(safe(line.getTaxAmount()));
        socialTotal = socialTotal.add(safe(line.getSocialAmount()));
        netTotal = netTotal.add(safe(line.getNetAmount()));
        int warningCount = line.getWarnings() == null ? 0 : line.getWarnings().size();
        int lineBlockingCount = line.getBlockingIssueCount() == null ? 0 : line.getBlockingIssueCount();
        int lineReviewCount = line.getReviewIssueCount() == null ? 0 : line.getReviewIssueCount();
        if (warningCount > 0 || (line.getMissingItems() != null && !line.getMissingItems().isEmpty())) {
            linesWithWarnings++;
        }
        if (Boolean.TRUE.equals(line.getHasBlockingIssues())) {
            linesWithBlockingIssues++;
        }
        totalWarnings += warningCount;
        blockingIssueCount += lineBlockingCount;
        reviewIssueCount += lineReviewCount;
    }

    PayrollManagerReviewDto dto = new PayrollManagerReviewDto();
    dto.setBatchId(batchId);
    dto.setPeriodLabel(ctx.batch.getPeriodLabel());
    dto.setCurrency(ctx.batch.getCurrency());
    dto.setDepartment(deptFilter);
    dto.setManagerId(managerFilter);
    dto.setKeyword(keyword);
    dto.setLines(filtered);
    dto.setTotalEmployees(filtered.size());
    dto.setEarningsTotal(earningsTotal);
    dto.setDeductionsTotal(deductionsTotal);
    dto.setGrossTotal(grossTotal);
    dto.setTaxTotal(taxTotal);
    dto.setSocialTotal(socialTotal);
    dto.setNetTotal(netTotal);
    dto.setLinesWithWarnings(linesWithWarnings);
    dto.setLinesWithBlockingIssues(linesWithBlockingIssues);
    dto.setTotalWarnings(totalWarnings);
    dto.setBlockingIssueCount(blockingIssueCount);
    dto.setReviewIssueCount(reviewIssueCount);
    dto.setHasBlockingIssues(blockingIssueCount > 0);
    dto.setWarnings(warningMessages);
    dto.setIssues(globalIssues);
    return dto;
}

    private PayrollPreviewDto.PayrollPreviewLineDto buildPreviewLine(PreviewContext ctx,
                                                                 Long empId,
                                                                 java.util.List<PayrollImportItem> list) {
    var line = new PayrollPreviewDto.PayrollPreviewLineDto();
    line.setEmployeeId(empId);
    Employee e = ctx.employeeMap.get(empId);
    if (e != null) {
        line.setEmployeeNo(e.getEmployeeId());
        line.setEmployeeName(e.getName());
        line.setDepartment(e.getDepartment());
        line.setManagerId(e.getManagerId());
        line.setEmploymentType(e.getEmploymentType());
        if (e.getManagerId() != null) {
            Employee manager = ctx.managerMap.get(e.getManagerId());
            if (manager != null) {
                line.setManagerName(manager.getName());
            }
        }
    } else {
        line.setEmploymentType(ctx.batch.getType());
    }

    BigDecimal earnings = BigDecimal.ZERO;
    BigDecimal deductions = BigDecimal.ZERO;
    BigDecimal taxableEarnings = BigDecimal.ZERO;
    java.util.List<PayrollPreviewDto.PayrollPreviewItemDto> pItems = new java.util.ArrayList<>();

    java.util.List<PayrollImportItem> safeList = list == null ? java.util.Collections.emptyList() : list;
    for (var rec : safeList) {
        SalaryItem def = ctx.defByCode.get(rec.getItemCode());
        if (def == null) {
            continue;
        }
        var pi = new PayrollPreviewDto.PayrollPreviewItemDto();
        pi.setCode(def.getCode());
        pi.setName(def.getName());
        pi.setType(def.getType());
        pi.setTaxable(def.getTaxable());
        pi.setAmount(rec.getAmount());
        pItems.add(pi);
        if ("earning".equalsIgnoreCase(def.getType())) {
            earnings = earnings.add(rec.getAmount());
            if (Boolean.TRUE.equals(def.getTaxable())) {
                taxableEarnings = taxableEarnings.add(rec.getAmount());
            }
        } else {
            deductions = deductions.add(rec.getAmount());
        }
    }

    line.setItems(pItems);
    line.setEarningsTotal(earnings);
    line.setDeductionsTotal(deductions);
    line.setGrossAmount(earnings);

    BigDecimal taxBase = switch (ctx.rule.taxApplyOn) {
        case TAXABLE_EARNINGS -> taxableEarnings;
        case GROSS -> earnings;
        case EARNINGS_MINUS_DEDUCTIONS -> earnings.subtract(deductions);
        case TAXABLE_EARNINGS_MINUS_DEDUCTIONS -> taxableEarnings.subtract(deductions);
    };
    BigDecimal socialBase = switch (ctx.rule.socialApplyOn) {
        case TAXABLE_EARNINGS -> taxableEarnings;
        case GROSS -> earnings;
        case EARNINGS_MINUS_DEDUCTIONS -> earnings.subtract(deductions);
        case TAXABLE_EARNINGS_MINUS_DEDUCTIONS -> taxableEarnings.subtract(deductions);
    };

    BigDecimal fallbackSocial = ctx.rule.socialRate.multiply(maxZero(socialBase))
            .setScale(ctx.rule.scale, ctx.rule.roundingMode);
    PayrollContributionCalculationService.Result contributionResult = payrollContributionCalculationService == null
            ? null
            : payrollContributionCalculationService.calculate(
                    empId,
                    ctx.batch,
                    maxZero(socialBase),
                    fallbackSocial,
                    ctx.rule.roundingMode
            );
    boolean contributionPolicyDriven = contributionResult != null && contributionResult.policyDriven();
    BigDecimal social = contributionPolicyDriven
            ? contributionResult.employeeAmount().setScale(ctx.rule.scale, ctx.rule.roundingMode)
            : fallbackSocial;
    if (contributionResult != null) {
        ctx.contributionComputations.put(empId, contributionResult);
    }
    PayrollCumulativeTaxService.TaxComputation computation = payrollCumulativeTaxService == null
            ? calculateBuiltinCumulativeTax(ctx, taxBase, social)
            : payrollCumulativeTaxService.calculate(
                    empId,
                    ctx.batch,
                    maxZero(taxBase),
                    social,
                    BigDecimal.ZERO,
                    currentOtherLawfulDeduction(safeList),
                    ctx.rule.scale,
                    ctx.rule.roundingMode
            );
    BigDecimal tax = computation.result().currentWithholdingTax();
    PayrollPreviewDto.TaxBreakdownDto breakdown = new PayrollPreviewDto.TaxBreakdownDto();
    breakdown.setMode("cumulative-withholding");
    breakdown.setTaxYear(computation.taxYear());
    breakdown.setTaxMonth(computation.taxMonth());
    breakdown.setCumulativeTaxableIncome(computation.result().cumulativeTaxableIncome());
    breakdown.setRate(computation.result().rate());
    breakdown.setQuickDeduction(computation.result().quickDeduction());
    breakdown.setCumulativeTax(computation.result().cumulativeTaxBeforeReduction());
    breakdown.setCurrentWithholdingTax(computation.result().currentWithholdingTax());
    breakdown.setBracketLevel(computation.result().bracketLevel());
    breakdown.setFormula(computation.result().formula());
    breakdown.setPolicyCode(computation.policyCode());
    line.setTaxBreakdown(breakdown);
    ctx.taxComputations.put(empId, computation);
    tax = tax.setScale(ctx.rule.scale, ctx.rule.roundingMode);
    line.setTaxAmount(tax);
    line.setSocialAmount(social);
    line.setNetAmount(earnings.subtract(deductions).subtract(tax).subtract(social)
            .setScale(ctx.rule.scale, ctx.rule.roundingMode));

    java.util.List<PayrollValidationIssueDto> issues = new java.util.ArrayList<>();
    if (payrollCumulativeTaxService == null && !allowBuiltinTaxFallback) {
        issues.add(validationIssueSupport.blocking(
                "TAX_ENGINE_MISSING",
                "累计预扣个税合规计算组件不可用，已阻止正式核算",
                String.valueOf(empId),
                ctx.batch.getPeriodLabel(),
                null
        ));
    }
    if (!contributionPolicyDriven && !allowTemplateContributionFallback) {
        issues.add(validationIssueSupport.blocking(
                "CONTRIBUTION_POLICY_MISSING",
                "员工缺少有效参保关系或已发布的属地五险一金政策，已阻止正式核算",
                String.valueOf(empId),
                ctx.batch.getPeriodLabel(),
                null
        ));
    }
    if (payrollCalculationTraceService == null && !allowBuiltinTaxFallback) {
        issues.add(validationIssueSupport.blocking(
                "CALCULATION_TRACE_WRITER_MISSING",
                "薪酬计算证据链组件不可用，已阻止正式核算",
                String.valueOf(empId),
                ctx.batch.getPeriodLabel(),
                null
        ));
    }
    java.util.List<String> missingItems = new java.util.ArrayList<>();
    java.util.Set<String> presentCodes = new java.util.HashSet<>();
    for (var item : pItems) {
        if (item != null && item.getCode() != null) {
            presentCodes.add(item.getCode());
        }
    }
    if (ctx.expectedItemRules != null && !ctx.expectedItemRules.isEmpty()) {
        for (var entryRule : ctx.expectedItemRules.entrySet()) {
            String code = entryRule.getKey();
            ItemRule rule = entryRule.getValue();
            boolean present = presentCodes.contains(code);
            if (Boolean.TRUE.equals(rule.required) && !present) {
                missingItems.add(code);
                issues.add(validationIssueSupport.blocking(
                        "MISSING_REQUIRED_ITEM",
                        "缺少必填薪资项：" + code,
                        code,
                        null,
                        null
                ));
            }
            if (present && (rule.min != null || rule.max != null)) {
                for (var item : pItems) {
                    if (item == null || !code.equals(item.getCode()) || item.getAmount() == null) {
                        continue;
                    }
                    if (rule.min != null && item.getAmount().compareTo(rule.min) < 0) {
                        issues.add(validationIssueSupport.review(
                                "ITEM_BELOW_MIN",
                                "薪资项 " + code + " 低于最小值：" + item.getAmount(),
                                code,
                                item.getAmount().toPlainString(),
                                rule.min.toPlainString()
                        ));
                    }
                    if (rule.max != null && item.getAmount().compareTo(rule.max) > 0) {
                        issues.add(validationIssueSupport.review(
                                "ITEM_ABOVE_MAX",
                                "薪资项 " + code + " 超过最大值：" + item.getAmount(),
                                code,
                                item.getAmount().toPlainString(),
                                rule.max.toPlainString()
                        ));
                    }
                }
            }
        }
    }

    var diff = buildDiff(ctx.batch, empId, line.getGrossAmount(), line.getNetAmount(), ctx.rule);
    if (diff != null && diff.getNetDeltaPercent() != null && ctx.rule.netDeltaWarnPct != null) {
        BigDecimal absPct = diff.getNetDeltaPercent().abs();
        if (absPct.compareTo(ctx.rule.netDeltaWarnPct) > 0) {
            issues.add(validationIssueSupport.review(
                    "NET_CHANGE_EXCEEDS_THRESHOLD",
                    "实发变动超过阈值：" + diff.getNetDeltaPercent(),
                    null,
                    diff.getNetDeltaPercent().toPlainString(),
                    ctx.rule.netDeltaWarnPct.toPlainString()
            ));
        }
    }
    if (ctx.template == null) {
        issues.add(validationIssueSupport.blocking(
                "NO_ACTIVE_SALARY_TEMPLATE",
                "未配置适用于当前批次类型的启用薪资模板",
                null,
                ctx.batch.getType(),
                null
        ));
    }

    line.setWarnings(validationIssueSupport.toMessages(issues));
    line.setIssues(issues);
    line.setBlockingIssueCount(validationIssueSupport.countBlocking(issues));
    line.setReviewIssueCount(validationIssueSupport.countReview(issues));
    line.setHasBlockingIssues(validationIssueSupport.hasBlocking(issues));
    line.setMissingItems(missingItems);
    line.setDiff(diff);
    return line;
}

    private PayrollCumulativeTaxService.TaxComputation calculateBuiltinCumulativeTax(
            PreviewContext ctx,
            BigDecimal taxBase,
            BigDecimal social
    ) {
        int year = LocalDateTime.now().getYear();
        int month = LocalDateTime.now().getMonthValue();
        String period = ctx.batch == null ? null : ctx.batch.getPeriodLabel();
        if (period != null && period.length() >= 7) {
            try {
                year = Integer.parseInt(period.substring(0, 4));
                month = Integer.parseInt(period.substring(5, 7));
            } catch (NumberFormatException ignored) {
                // 兼容历史批次的非标准周期标签。
            }
        }
        CumulativeWithholdingTaxCalculator.Result result = CumulativeWithholdingTaxCalculator.calculate(
                new CumulativeWithholdingTaxCalculator.Input(
                        maxZero(taxBase),
                        BigDecimal.ZERO,
                        new BigDecimal("5000"),
                        social,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        ctx.rule.scale,
                        ctx.rule.roundingMode
                )
        );
        return new PayrollCumulativeTaxService.TaxComputation(
                year,
                month,
                result,
                "builtin:cn-resident-wage-withholding-v1"
        );
    }

    private BigDecimal currentOtherLawfulDeduction(java.util.List<PayrollImportItem> items) {
        if (items == null) {
            return BigDecimal.ZERO;
        }
        return items.stream()
                .filter(item -> item != null && item.getItemCode() != null)
                .filter(item -> {
                    String code = item.getItemCode().toLowerCase(Locale.ROOT);
                    return code.contains("pension") || code.contains("annuity") || code.contains("other_tax_deduction");
                })
                .map(PayrollImportItem::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void recordTaxLedgers(PreviewContext ctx, java.util.List<PayrollLine> entities) {
        if (payrollCumulativeTaxService == null || ctx == null || entities == null) {
            return;
        }
        for (PayrollLine line : entities) {
            PayrollCumulativeTaxService.TaxComputation computation = ctx.taxComputations.get(line.getEmployeeId());
            if (computation != null) {
                payrollCumulativeTaxService.recordLedger(line.getEmployeeId(), ctx.batch, line.getId(), computation);
            }
        }
    }

    private void recordContributionRecords(PreviewContext ctx, java.util.List<PayrollLine> entities) {
        if (payrollContributionRecordService == null || ctx == null || entities == null) {
            return;
        }
        for (PayrollLine line : entities) {
            PayrollContributionCalculationService.Result result = ctx.contributionComputations.get(line.getEmployeeId());
            if (result == null || !result.policyDriven()) {
                continue;
            }
            java.util.List<com.yiyundao.compensation.modules.payroll.entity.PayrollContributionRecord> records = new java.util.ArrayList<>();
            for (PayrollContributionCalculationService.Line contribution : result.lines()) {
                com.yiyundao.compensation.modules.payroll.entity.PayrollContributionRecord record = new com.yiyundao.compensation.modules.payroll.entity.PayrollContributionRecord();
                record.setPayrollBatchId(ctx.batch.getId());
                record.setPayrollLineId(line.getId());
                record.setEmployeeId(line.getEmployeeId());
                record.setContributionType(contribution.contributionType());
                record.setRegionCode(contribution.regionCode());
                record.setPolicyId(contribution.policyId());
                record.setDeclaredWage(contribution.declaredWage());
                record.setContributionBase(contribution.contributionBase());
                record.setEmployerRate(contribution.employerRate());
                record.setEmployeeRate(contribution.employeeRate());
                record.setEmployerAmount(contribution.employerAmount());
                record.setEmployeeAmount(contribution.employeeAmount());
                record.setStatus("calculated");
                record.setCalculationHash(contribution.calculationHash());
                records.add(record);
            }
            if (!records.isEmpty()) {
                payrollContributionRecordService.saveBatch(records);
            }
        }
    }

    private void recordCalculationTraces(PreviewContext ctx,
                                         java.util.List<PayrollLine> entities,
                                         LinesSummary summary) {
        if (payrollCalculationTraceService == null || ctx == null || entities == null || summary == null) {
            return;
        }
        java.util.List<com.yiyundao.compensation.modules.payroll.entity.PayrollCalculationTrace> traces = new java.util.ArrayList<>();
        for (PayrollLine line : entities) {
            PayrollPreviewDto.PayrollPreviewLineDto previewLine = summary.linesByEmployee.get(line.getEmployeeId());
            if (previewLine == null) {
                continue;
            }
            int sequence = 1;
            if (previewLine.getItems() != null) {
                for (PayrollPreviewDto.PayrollPreviewItemDto item : previewLine.getItems()) {
                    com.yiyundao.compensation.modules.payroll.entity.PayrollCalculationTrace trace = new com.yiyundao.compensation.modules.payroll.entity.PayrollCalculationTrace();
                    trace.setPayrollBatchId(ctx.batch.getId());
                    trace.setPayrollLineId(line.getId());
                    trace.setEmployeeId(line.getEmployeeId());
                    trace.setSequence(sequence++);
                    trace.setStepCode("SALARY_ITEM");
                    trace.setItemCode(item.getCode());
                    trace.setOutputValue(item.getAmount());
                    trace.setFormula("item[" + item.getCode() + "]");
                    trace.setRuleVersion(ctx.calculationEngineVersion);
                    trace.setSourceRef(ctx.inputSnapshotHash);
                    trace.setRoundingMode(ctx.rule.roundingMode.name());
                    traces.add(trace);
                }
            }
            if (previewLine.getTaxBreakdown() != null) {
                PayrollPreviewDto.TaxBreakdownDto tax = previewLine.getTaxBreakdown();
                com.yiyundao.compensation.modules.payroll.entity.PayrollCalculationTrace trace = new com.yiyundao.compensation.modules.payroll.entity.PayrollCalculationTrace();
                trace.setPayrollBatchId(ctx.batch.getId());
                trace.setPayrollLineId(line.getId());
                trace.setEmployeeId(line.getEmployeeId());
                trace.setSequence(sequence++);
                trace.setStepCode("CUMULATIVE_WITHHOLDING_TAX");
                trace.setOutputValue(tax.getCurrentWithholdingTax());
                trace.setFormula(tax.getFormula());
                trace.setRuleVersion(tax.getPolicyCode());
                trace.setSourceRef(ctx.ruleSnapshotHash);
                trace.setRoundingMode(ctx.rule.roundingMode.name());
                try {
                    trace.setInputJson(objectMapper.writeValueAsString(tax));
                } catch (Exception ignored) {
                    trace.setInputJson("{}");
                }
                traces.add(trace);
            }
            com.yiyundao.compensation.modules.payroll.entity.PayrollCalculationTrace finalTrace = new com.yiyundao.compensation.modules.payroll.entity.PayrollCalculationTrace();
            finalTrace.setPayrollBatchId(ctx.batch.getId());
            finalTrace.setPayrollLineId(line.getId());
            finalTrace.setEmployeeId(line.getEmployeeId());
            finalTrace.setSequence(sequence);
            finalTrace.setStepCode("NET_SETTLEMENT");
            finalTrace.setOutputValue(line.getNetAmount());
            finalTrace.setFormula("gross - deductions - tax - social");
            finalTrace.setRuleVersion(ctx.calculationEngineVersion);
            finalTrace.setSourceRef(ctx.inputSnapshotHash);
            finalTrace.setRoundingMode(ctx.rule.roundingMode.name());
            traces.add(finalTrace);
        }
        if (!traces.isEmpty()) {
            payrollCalculationTraceService.saveBatch(traces);
        }
    }

    private LinesSummary computeLines(PreviewContext ctx) {
    LinesSummary summary = new LinesSummary();
    if (ctx == null || ctx.itemsByEmployee == null || ctx.itemsByEmployee.isEmpty()) {
        return summary;
    }
    int linesWithWarnings = 0;
    int linesWithBlockingIssues = 0;
    int totalWarnings = 0;
    int blockingIssueCount = 0;
    int reviewIssueCount = 0;
    for (var entry : ctx.itemsByEmployee.entrySet()) {
        Long empId = entry.getKey();
        var line = buildPreviewLine(ctx, empId, entry.getValue());
        summary.linesByEmployee.put(empId, line);
        summary.orderedLines.add(line);
        summary.earningsTotal = summary.earningsTotal.add(safe(line.getEarningsTotal()));
        summary.deductionsTotal = summary.deductionsTotal.add(safe(line.getDeductionsTotal()));
        summary.grossTotal = summary.grossTotal.add(safe(line.getGrossAmount()));
        summary.taxTotal = summary.taxTotal.add(safe(line.getTaxAmount()));
        summary.socialTotal = summary.socialTotal.add(safe(line.getSocialAmount()));
        summary.netTotal = summary.netTotal.add(safe(line.getNetAmount()));

        int warningCount = line.getWarnings() == null ? 0 : line.getWarnings().size();
        int lineBlockingCount = line.getBlockingIssueCount() == null ? 0 : line.getBlockingIssueCount();
        int lineReviewCount = line.getReviewIssueCount() == null ? 0 : line.getReviewIssueCount();
        if (warningCount > 0 || (line.getMissingItems() != null && !line.getMissingItems().isEmpty())) {
            linesWithWarnings++;
        }
        if (Boolean.TRUE.equals(line.getHasBlockingIssues())) {
            linesWithBlockingIssues++;
        }
        totalWarnings += warningCount;
        blockingIssueCount += lineBlockingCount;
        reviewIssueCount += lineReviewCount;
    }
    summary.linesWithWarnings = linesWithWarnings;
    summary.linesWithBlockingIssues = linesWithBlockingIssues;
    summary.totalWarnings = totalWarnings;
    summary.blockingIssueCount = blockingIssueCount;
    summary.reviewIssueCount = reviewIssueCount;
    return summary;
}

    private PreviewContext prepareContext(Long batchId, boolean requireReady) {
    PayrollBatch batch = payrollBatchMapper.selectById(batchId);
    if (batch == null) {
        return null;
    }
    boolean ready = validateReady(batch);
    if (requireReady && !ready) {
        return null;
    }

    PreviewContext ctx = new PreviewContext();
    ctx.batch = batch;
    ctx.persistedLines = payrollLineService.list(new LambdaQueryWrapper<PayrollLine>()
            .eq(PayrollLine::getBatchId, batchId)
            .eq(PayrollLine::getBatchRevision, normalizeRevision(batch.getBatchRevision()))
            .orderByAsc(PayrollLine::getId));
    if (!ready) {
        PayrollBatchStatus status = batch.getStatus();
        if (status == PayrollBatchStatus.PAY_FAILED) {
            ctx.globalIssues.add(validationIssueSupport.info(
                    "BATCH_PAY_FAILED",
                    "当前批次支付失败，可返回批次列表执行重试支付"
            ));
        } else if (status == PayrollBatchStatus.PAID || status == PayrollBatchStatus.ARCHIVED) {
            ctx.globalIssues.add(validationIssueSupport.info(
                    "BATCH_READ_ONLY",
                    "当前批次已完成发薪，页面仅供查看"
            ));
        } else {
            ctx.globalIssues.add(validationIssueSupport.info(
                    "BATCH_STATUS_READ_ONLY",
                    "当前批次状态为 " + status.getCode() + "，页面仅供查看"
            ));
        }
    }
    ctx.template = resolveTemplateForBatch(batch, ctx.persistedLines);
    ctx.ruleSnapshotJson = PayrollCalculationSnapshotSupport.ruleSnapshotJson(objectMapper, ctx.template);
    ctx.ruleSnapshotHash = PayrollCalculationSnapshotSupport.ruleHash(objectMapper, ctx.template);
    ctx.rule = parseBasicRule(ctx.template);
    if (ctx.rule.cumulativeWithholding) {
        ctx.calculationEngineVersion = "cumulative-withholding-v1";
    }
    ctx.expectedItemRules = parseItemRules(ctx.template);
    if (ctx.rule.parseError != null) {
        ctx.globalIssues.add(validationIssueSupport.blocking(
                "INVALID_PAYROLL_RULE_JSON",
                "薪资模板规则无法解析，已阻止本次计算"
        ));
    }
    if (ctx.template == null) {
        boolean pinnedRuleMissing = batch.getRuleTemplateId() != null;
        ctx.globalIssues.add(validationIssueSupport.blocking(
                pinnedRuleMissing ? "PINNED_PAYROLL_RULE_VERSION_MISSING" : "NO_ACTIVE_SALARY_TEMPLATE",
                pinnedRuleMissing ? "批次锁定的薪资规则包版本不存在或已变化，请创建新的批次版本"
                        : "未配置适用于当前批次类型的启用薪资模板",
                null,
                batch.getType(),
                null
        ));
    }

    ctx.defByCode = new java.util.HashMap<>();
    for (SalaryItem item : salaryItemService.list(new LambdaQueryWrapper<SalaryItem>().eq(SalaryItem::getStatus, "enabled"))) {
        ctx.defByCode.put(item.getCode(), item);
    }
    applyPersistedSalaryItemSnapshots(ctx);

    ctx.itemsByEmployee = new java.util.LinkedHashMap<>();
    var items = importItemMapper.selectList(new LambdaQueryWrapper<PayrollImportItem>()
            .eq(PayrollImportItem::getBatchId, batchId)
            .eq(PayrollImportItem::getStatus, "valid")
            .orderByAsc(PayrollImportItem::getEmployeeId));
    ctx.inputSnapshotJson = PayrollCalculationSnapshotSupport.inputSnapshotJson(objectMapper, items);
    ctx.inputSnapshotHash = PayrollCalculationSnapshotSupport.inputHash(objectMapper, items);
    for (var item : items) {
        ctx.itemsByEmployee.computeIfAbsent(item.getEmployeeId(), key -> new java.util.ArrayList<>()).add(item);
    }

    ctx.employeeMap = new java.util.HashMap<>();
    if (!ctx.itemsByEmployee.isEmpty()) {
        java.util.Set<Long> employeeIds = new java.util.HashSet<>(ctx.itemsByEmployee.keySet());
        for (Employee employee : employeeMapper.selectBatchIds(employeeIds)) {
            ctx.employeeMap.put(employee.getId(), employee);
        }
    }

    ctx.managerMap = new java.util.HashMap<>();
    if (!ctx.employeeMap.isEmpty()) {
        java.util.Set<Long> managerIds = new java.util.HashSet<>();
        for (Employee employee : ctx.employeeMap.values()) {
            if (employee.getManagerId() != null) {
                managerIds.add(employee.getManagerId());
            }
        }
        if (!managerIds.isEmpty()) {
            for (Employee manager : employeeMapper.selectBatchIds(managerIds)) {
                ctx.managerMap.put(manager.getId(), manager);
            }
        }
    }
    return ctx;
}

    private void applySnapshotMetadata(PreviewContext ctx) {
        if (ctx == null || ctx.batch == null) {
            return;
        }
        ctx.calculationEngineVersion = ctx.rule != null && ctx.rule.cumulativeWithholding
                ? "cumulative-withholding-v1"
                : PayrollCalculationSnapshotSupport.LEGACY_BASIC_ENGINE_VERSION;
        ctx.batch.setInputSnapshotHash(ctx.inputSnapshotHash);
        ctx.batch.setInputSnapshotJson(ctx.inputSnapshotJson);
        ctx.batch.setRuleSnapshotHash(ctx.ruleSnapshotHash);
        ctx.batch.setRuleSnapshotJson(ctx.ruleSnapshotJson);
        ctx.batch.setCalculationEngineVersion(ctx.calculationEngineVersion);
    }

    private void validatePersistedSnapshot(PreviewContext ctx) {
        if (ctx == null || ctx.batch == null) {
            return;
        }
        if (StringUtils.hasText(ctx.batch.getInputSnapshotHash())
                && !ctx.batch.getInputSnapshotHash().equals(ctx.inputSnapshotHash)) {
            ctx.globalIssues.add(validationIssueSupport.blocking(
                    "PAYROLL_INPUT_SNAPSHOT_CHANGED",
                    "薪资输入事实已变化，请创建新的批次版本后再计算"
            ));
        }
        if (StringUtils.hasText(ctx.batch.getRuleSnapshotHash())
                && !ctx.batch.getRuleSnapshotHash().equals(ctx.ruleSnapshotHash)) {
            ctx.globalIssues.add(validationIssueSupport.blocking(
                    "PAYROLL_RULE_SNAPSHOT_CHANGED",
                    "薪资规则已变化，请创建新的批次版本后再计算"
            ));
        }
    }

    private void pinTaxPolicyVersion(PreviewContext ctx) {
        if (ctx == null || ctx.batch == null || ctx.taxComputations == null) {
            return;
        }
        java.util.Set<Long> policyIds = ctx.taxComputations.values().stream()
                .map(PayrollCumulativeTaxService.TaxComputation::policyId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        boolean explicitPolicyMissing = ctx.batch.getPolicyPackageId() != null
                && ctx.taxComputations.values().stream().anyMatch(item -> item.policyId() == null);
        if (explicitPolicyMissing || policyIds.size() > 1) {
            ctx.globalIssues.add(validationIssueSupport.blocking(
                    "PAYROLL_TAX_POLICY_AMBIGUOUS",
                    "批次未能唯一确定已发布的个税政策版本，请检查政策包和批次绑定"
            ));
            return;
        }
        if (ctx.batch.getPolicyPackageId() == null && policyIds.size() == 1) {
            ctx.batch.setPolicyPackageId(policyIds.iterator().next());
        }
    }

    private boolean canPersist(PayrollBatch batch) {
        if (batch == null || batch.getStatus() == null) {
            return false;
        }
        PayrollBatchStatus status = batch.getStatus();
        return status == PayrollBatchStatus.LOCKED
                || status == PayrollBatchStatus.CONFIRMING
                || status == PayrollBatchStatus.DISPUTE_PROCESSING
                || status == PayrollBatchStatus.REJECTED;
    }

    private PayrollLine toPayrollLine(PreviewContext ctx,
                                  Long empId,
                                  PayrollPreviewDto.PayrollPreviewLineDto lineDto,
                                  PayrollLine existing) {
    PayrollLine entity = new PayrollLine();
        entity.setBatchId(ctx.batch.getId());
        entity.setBatchRevision(ctx.calculationRevision);
        entity.setEmployeeId(empId);
        entity.setEmployeeNoSnapshot(lineDto.getEmployeeNo());
        entity.setEmployeeNameSnapshot(lineDto.getEmployeeName());
        entity.setDepartmentSnapshot(lineDto.getDepartment());
        entity.setTemplateId(ctx.template != null ? ctx.template.getId() : null);
        entity.setTemplateVersion(ctx.template != null ? ctx.template.getDataVersion() : null);
        entity.setInputSnapshotHash(ctx.inputSnapshotHash);
        entity.setRuleSnapshotHash(ctx.ruleSnapshotHash);
        entity.setCalculationEngineVersion(ctx.calculationEngineVersion);
        Employee employee = ctx.employeeMap.get(empId);
    entity.setEmploymentType(employee != null ? employee.getEmploymentType() : ctx.batch.getType());
    entity.setCurrency(ctx.batch.getCurrency());
    entity.setGrossAmount(lineDto.getGrossAmount());
    entity.setTaxAmount(lineDto.getTaxAmount());
    entity.setSocialAmount(lineDto.getSocialAmount());
    entity.setNetAmount(lineDto.getNetAmount());
    try {
        entity.setTaxBreakdownJson(objectMapper.writeValueAsString(lineDto.getTaxBreakdown()));
    } catch (Exception e) {
        throw new BusinessException(ErrorCode.DATA_INTEGRITY_ERROR, "个税解释快照序列化失败，已阻止落库");
    }
    if (existing != null && existing.getStatus() != null) {
        entity.setStatus(existing.getStatus());
    } else {
        entity.setStatus("calculated");
    }
    entity.setConfirmationAssigneeEmployeeId(resolveConfirmationAssigneeEmployeeId(existing, empId));
    entity.setConfirmationStatus(PayrollConfirmationStatus.PENDING.getCode());
    entity.setConfirmedByUserId(null);
    entity.setConfirmedByEmployeeId(null);
    entity.setConfirmedAt(null);
    entity.setConfirmationComment(null);
    entity.setObjectionReason(null);
    entity.setObjectionAt(null);
    entity.setDisputeWorkflowId(null);

    if (existing != null && !ctx.createNewRevision) {
        entity.setId(existing.getId());
        entity.setVersion(existing.getVersion());
    }
    try {
        entity.setItemsSnapshotJson(objectMapper.writeValueAsString(lineDto.getItems()));
    } catch (Exception e) {
        throw new BusinessException(ErrorCode.DATA_INTEGRITY_ERROR, "工资项快照序列化失败，已阻止落库");
    }
    entity.setWarning(validationIssueSupport.serialize(lineDto.getIssues()));
    return entity;
}

    private java.util.List<PayrollValidationIssueDto> parseIssueSnapshot(String payload) {
        return validationIssueSupport.deserialize(payload);
    }

    private void applyLineIssueSummary(PayrollPreviewDto.PayrollPreviewLineDto line,
                                       java.util.List<PayrollValidationIssueDto> issues) {
        java.util.List<PayrollValidationIssueDto> safeIssues = issues == null
                ? java.util.Collections.emptyList()
                : issues;
        line.setIssues(safeIssues);
        line.setWarnings(validationIssueSupport.toMessages(safeIssues));
        line.setBlockingIssueCount(validationIssueSupport.countBlocking(safeIssues));
        line.setReviewIssueCount(validationIssueSupport.countReview(safeIssues));
        line.setHasBlockingIssues(validationIssueSupport.hasBlocking(safeIssues));
        if (line.getMissingItems() == null) {
            line.setMissingItems(java.util.Collections.emptyList());
        }
    }

    private java.util.List<PayrollValidationIssueDto> createLedgerGlobalIssues(PreviewContext ctx,
                                                                                boolean hasPersistedLines) {
        java.util.List<PayrollValidationIssueDto> issues = new java.util.ArrayList<>();
        if (ctx != null && ctx.globalIssues != null && !ctx.globalIssues.isEmpty()) {
            issues.addAll(ctx.globalIssues);
        }
        if (!hasPersistedLines) {
            issues.add(validationIssueSupport.blocking(
                    "NO_COMPUTED_LINES",
                    "未发现已落库工资行，请先执行“计算薪酬”后再进入提审或发放流程"
            ));
        }
        return issues;
    }

    private Long resolveConfirmationAssigneeEmployeeId(PayrollLine existing, Long employeeId) {
        if (existing != null && existing.getConfirmationAssigneeEmployeeId() != null) {
            return existing.getConfirmationAssigneeEmployeeId();
        }
        return employeeId;
    }

    private int normalizeRevision(Integer revision) {
        return revision == null || revision < 1 ? 1 : revision;
    }

    private void updateBatchToConfirming(PayrollBatch batch) {
        if (batch == null) {
            return;
        }
        if (batch.getConfirmationRequired() == null) {
            batch.setConfirmationRequired(Boolean.TRUE);
        }
        if (!StringUtils.hasText(batch.getConfirmationMode())) {
            batch.setConfirmationMode(PayrollConfirmationMode.INDIVIDUAL.getCode());
        }
        batch.setCalculationStatus(PayrollCalculationStatus.CALCULATED);
        batch.setConfirmationCompletedTime(null);
        batch.setBatchRevision(normalizeRevision(batch.getBatchRevision()));
        if (Boolean.TRUE.equals(batch.getConfirmationRequired())) {
            if (batch.getStatus() == null || !batch.getStatus().canTransitionTo(PayrollBatchStatus.CONFIRMING)) {
                throw new BusinessException(
                        ErrorCode.INVALID_STATUS,
                        "不允许的薪资批次状态转移: "
                                + (batch.getStatus() == null ? "null" : batch.getStatus().getCode())
                                + " -> " + PayrollBatchStatus.CONFIRMING.getCode()
                );
            }
            batch.setStatus(PayrollBatchStatus.CONFIRMING);
        }
        batch.setUpdateTime(LocalDateTime.now());
        int updated = payrollBatchMapper.updateById(batch);
        if (updated <= 0) {
            throw new BusinessException(ErrorCode.REQUEST_CONFLICT, "批次状态已变更，请刷新后重试");
        }
    }

    private FailureGuard markBatchCalculating(PayrollBatch batch) {
        if (batch == null) {
            return FailureGuard.empty();
        }
        if (batch.getBatchRevision() == null || batch.getBatchRevision() < 1) {
            batch.setBatchRevision(1);
        }
        batch.setCalculationStatus(PayrollCalculationStatus.CALCULATING);
        batch.setUpdateTime(LocalDateTime.now());
        int updated = payrollBatchMapper.updateById(batch);
        if (updated <= 0) {
            throw new BusinessException(ErrorCode.REQUEST_CONFLICT, "批次状态已变更，请刷新后重试");
        }
        return FailureGuard.from(batch);
    }

    private void markBatchCalculationFailedAfterRollback(Long batchId,
                                                         FailureGuard rollbackFailureGuard,
                                                         FailureGuard calculatingFailureGuard) {
        if (batchId == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            FailureGuard guard = calculatingFailureGuard != null ? calculatingFailureGuard : rollbackFailureGuard;
            calculationFailureMarker.markFailed(batchId, guard.updateTime(), guard.version());
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    FailureGuard guard = rollbackFailureGuard != null ? rollbackFailureGuard : FailureGuard.empty();
                    calculationFailureMarker.markFailed(batchId, guard.updateTime(), guard.version());
                }
            }
        });
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal maxZero(BigDecimal v) {
        return v.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : v;
    }

    private void ensureEmployeesLoaded(PreviewContext ctx, java.util.Set<Long> employeeIds) {
        if (ctx == null || employeeIds == null || employeeIds.isEmpty()) {
            return;
        }
        java.util.Set<Long> missing = new java.util.HashSet<>();
        for (Long id : employeeIds) {
            if (id != null && !ctx.employeeMap.containsKey(id)) {
                missing.add(id);
            }
        }
        if (!missing.isEmpty()) {
            for (Employee e : employeeMapper.selectBatchIds(missing)) {
                ctx.employeeMap.put(e.getId(), e);
            }
        }
        if (ctx.managerMap == null) {
            ctx.managerMap = new java.util.HashMap<>();
        }
        java.util.Set<Long> managerIds = new java.util.HashSet<>();
        for (Long id : employeeIds) {
            Employee emp = ctx.employeeMap.get(id);
            if (emp != null && emp.getManagerId() != null && !ctx.managerMap.containsKey(emp.getManagerId())) {
                managerIds.add(emp.getManagerId());
            }
        }
        if (!managerIds.isEmpty()) {
            for (Employee manager : employeeMapper.selectBatchIds(managerIds)) {
                ctx.managerMap.put(manager.getId(), manager);
            }
        }
    }

    private java.util.List<PayrollPreviewDto.PayrollPreviewItemDto> parseItemsSnapshot(String json) {
        if (json == null || json.isBlank()) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY_ERROR, "工资项历史快照损坏，无法继续核算");
        }
        try {
            var node = readJsonTree(json);
            if (node == null || node.isNull() || !node.isArray()) {
                return java.util.Collections.emptyList();
            }
            return objectMapper.convertValue(node, new TypeReference<java.util.List<PayrollPreviewDto.PayrollPreviewItemDto>>() {});
        } catch (Exception e) {
            log.warn("parse items snapshot failed: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    private SalaryTemplate resolveTemplateForBatch(PayrollBatch batch, java.util.List<PayrollLine> persistedLines) {
        try {
            if (batch != null && batch.getRuleTemplateId() != null) {
                if (batch.getRuleTemplateVersion() == null) {
                    log.error("批次缺少锁定的规则包版本: batchId={}, templateId={}",
                            batch.getId(), batch.getRuleTemplateId());
                    return null;
                }
                SalaryTemplate pinnedTemplate = resolvePinnedTemplate(batch);
                if (pinnedTemplate == null) {
                    log.error("批次锁定规则包与当前规则包版本不一致: batchId={}, templateId={}, expectedVersion={}, actualVersion={}",
                            batch.getId(), batch.getRuleTemplateId(), batch.getRuleTemplateVersion(),
                            pinnedTemplate == null ? null : pinnedTemplate.getDataVersion());
                    return null;
                }
                return pinnedTemplate;
            }
            Long persistedTemplateId = resolvePersistedTemplateId(persistedLines);
            if (persistedTemplateId != null) {
                SalaryTemplate persistedTemplate = salaryTemplateService.getById(persistedTemplateId);
                if (persistedTemplate != null) {
                    return persistedTemplate;
                }
                log.warn("工资行引用的薪资模板不存在，将回退到当前启用模板: batchId={}, templateId={}",
                        batch != null ? batch.getId() : null, persistedTemplateId);
            }
            var list = salaryTemplateService.list(new LambdaQueryWrapper<SalaryTemplate>()
                    .eq(SalaryTemplate::getType, batch.getType())
                    .eq(SalaryTemplate::getStatus, "enabled"));
            if (list == null || list.isEmpty()) return null;
            if (list.size() > 1) {
                log.error("同一用工类型存在多个启用规则包，停止隐式选择: batchId={}, type={}, count={}",
                        batch != null ? batch.getId() : null, batch != null ? batch.getType() : null, list.size());
                return null;
            }
            return list.get(0);
        } catch (Exception e) {
            log.warn("resolveTemplateForBatch failed: {}", e.getMessage());
            return null;
        }
    }

    private SalaryTemplate resolvePinnedTemplate(PayrollBatch batch) {
        SalaryTemplate current = salaryTemplateService.getById(batch.getRuleTemplateId());
        if (current != null
                && Objects.equals(batch.getType(), current.getType())
                && Objects.equals(batch.getRuleTemplateVersion(), current.getDataVersion())) {
            return current;
        }
        if (salaryTemplateVersionService == null) {
            return null;
        }
        SalaryTemplateVersion snapshot = salaryTemplateVersionService.getOne(
                new LambdaQueryWrapper<SalaryTemplateVersion>()
                        .eq(SalaryTemplateVersion::getTemplateId, batch.getRuleTemplateId())
                        .eq(SalaryTemplateVersion::getVersionNo, batch.getRuleTemplateVersion())
                        .eq(SalaryTemplateVersion::getDeleted, 0)
        );
        if (snapshot == null || !Objects.equals(batch.getType(), snapshot.getType())) {
            return null;
        }
        SalaryTemplate restored = new SalaryTemplate();
        restored.setId(snapshot.getTemplateId());
        restored.setName(snapshot.getName());
        restored.setType(snapshot.getType());
        restored.setItemsJson(snapshot.getItemsJson());
        restored.setTaxRuleJson(snapshot.getTaxRuleJson());
        restored.setStatus(snapshot.getStatus());
        restored.setDataVersion(snapshot.getVersionNo());
        return restored;
    }

    private Long resolvePersistedTemplateId(java.util.List<PayrollLine> persistedLines) {
        if (persistedLines == null || persistedLines.isEmpty()) {
            return null;
        }
        java.util.Set<Long> templateIds = persistedLines.stream()
                .map(PayrollLine::getTemplateId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (templateIds.isEmpty()) {
            return null;
        }
        if (templateIds.size() > 1) {
            log.warn("同一批次工资行存在多个模板引用，将使用最早记录的模板: templateIds={}", templateIds);
        }
        return templateIds.iterator().next();
    }

    private void applyPersistedSalaryItemSnapshots(PreviewContext ctx) {
        if (ctx == null || ctx.persistedLines == null || ctx.persistedLines.isEmpty()) {
            return;
        }
        for (PayrollLine line : ctx.persistedLines) {
            for (PayrollPreviewDto.PayrollPreviewItemDto item : parseItemsSnapshot(line.getItemsSnapshotJson())) {
                if (item == null || !StringUtils.hasText(item.getCode())) {
                    continue;
                }
                SalaryItem existing = ctx.defByCode.get(item.getCode());
                SalaryItem snapshot = new SalaryItem();
                snapshot.setCode(item.getCode());
                snapshot.setName(StringUtils.hasText(item.getName()) ? item.getName() : existing != null ? existing.getName() : item.getCode());
                snapshot.setType(StringUtils.hasText(item.getType()) ? item.getType() : existing != null ? existing.getType() : null);
                snapshot.setTaxable(item.getTaxable() != null ? item.getTaxable() : existing != null ? existing.getTaxable() : null);
                snapshot.setShowOnPayslip(existing != null && existing.getShowOnPayslip() != null
                        ? existing.getShowOnPayslip()
                        : Boolean.TRUE);
                snapshot.setOrderNum(existing != null ? existing.getOrderNum() : null);
                snapshot.setStatus("enabled");
                ctx.defByCode.put(item.getCode(), snapshot);
            }
        }
    }

    private BasicCalcRule parseBasicRule(SalaryTemplate tpl) {
        BasicCalcRule rule = new BasicCalcRule();
        if (tpl == null || tpl.getTaxRuleJson() == null || tpl.getTaxRuleJson().isBlank()) {
            rule.parseError = "薪资模板缺少累计预扣税务规则，旧版固定税率已下线";
            return rule;
        }
        try {
            var root = readJsonTree(tpl.getTaxRuleJson());
            // tax
            var tax = root.path("tax");
            if (!tax.isMissingNode()) {
                if (tax.has("rate")) {
                    rule.taxRate = new BigDecimal(tax.get("rate").asText("0"));
                    if (!tax.has("mode") || !isCumulativeTaxMode(tax.get("mode").asText())) {
                        rule.parseError = "薪资模板仍使用旧版固定税率，必须迁移到 cumulative-withholding 模式";
                    }
                }
                if (tax.has("mode") && !isCumulativeTaxMode(tax.get("mode").asText())) {
                    rule.parseError = "不支持旧版个税计算模式：" + tax.get("mode").asText();
                }
                if (tax.has("applyOn")) rule.taxApplyOn = BasicCalcRule.ApplyOn.from(tax.get("applyOn").asText());
            }
            // social
            var social = root.path("social");
            if (!social.isMissingNode()) {
                if (social.has("rate")) rule.socialRate = new BigDecimal(social.get("rate").asText("0"));
                if (social.has("applyOn")) rule.socialApplyOn = BasicCalcRule.ApplyOn.from(social.get("applyOn").asText());
            }
            // rounding
            var rounding = root.path("rounding");
            if (!rounding.isMissingNode()) {
                if (rounding.has("scale")) rule.scale = rounding.get("scale").asInt(rule.scale);
                if (rounding.has("mode")) rule.roundingMode = BasicCalcRule.rounding(rounding.get("mode").asText());
            }
            // thresholds
            var thresholds = root.path("thresholds");
            if (!thresholds.isMissingNode()) {
                if (thresholds.has("netDeltaPct")) {
                    rule.netDeltaWarnPct = new BigDecimal(thresholds.get("netDeltaPct").asText("0"));
                }
            }
        } catch (Exception e) {
            rule.parseError = e.getMessage();
            log.warn("parseBasicRule failed, calculation is blocked: {}", e.getMessage());
        }
        return rule;
    }

    private boolean isCumulativeTaxMode(String mode) {
        return "cumulative_withholding".equalsIgnoreCase(mode)
                || "cumulative-withholding".equalsIgnoreCase(mode);
    }

    private java.util.Map<String, ItemRule> parseItemRules(SalaryTemplate tpl) {
        if (tpl == null || tpl.getItemsJson() == null || tpl.getItemsJson().isBlank()) return java.util.Collections.emptyMap();
        try {
            var node = readJsonTree(tpl.getItemsJson());
            java.util.Map<String, ItemRule> res = new java.util.HashMap<>();
            if (node.isArray()) {
                for (var it : node) {
                    String code = it.path("code").asText(null);
                    if (code == null || code.isBlank()) continue;
                    ItemRule r = new ItemRule();
                    if (it.has("required")) r.required = it.get("required").asBoolean(false);
                    if (it.has("min")) r.min = new BigDecimal(it.get("min").asText());
                    if (it.has("max")) r.max = new BigDecimal(it.get("max").asText());
                    res.put(code, r);
                }
            }
            return res;
        } catch (Exception e) {
            log.warn("parseItemRules failed: {}", e.getMessage());
            return java.util.Collections.emptyMap();
        }
    }

    private com.fasterxml.jackson.databind.JsonNode readJsonTree(String json) throws com.fasterxml.jackson.core.JsonProcessingException {
        var node = objectMapper.readTree(json);
        if (node != null && node.isTextual()) {
            return objectMapper.readTree(node.asText());
        }
        return node;
    }

    private PayrollPreviewDto.DiffSummaryDto buildDiff(PayrollBatch currentBatch,
                                                       Long employeeId,
                                                       BigDecimal currGross,
                                                       BigDecimal currNet,
                                                       BasicCalcRule rule) {
        try {
            PayrollBatch prev = findPreviousBatch(currentBatch);
            if (prev == null) return null;
            // fetch previous payroll line for this employee
            var prevLine = payrollLineService.getOne(new LambdaQueryWrapper<PayrollLine>()
                    .eq(PayrollLine::getBatchId, prev.getId())
                    .eq(PayrollLine::getBatchRevision, normalizeRevision(prev.getBatchRevision()))
                    .eq(PayrollLine::getEmployeeId, employeeId)
                    .last("limit 1"));
            if (prevLine == null) return null;

            BigDecimal lastGross = prevLine.getGrossAmount() == null ? BigDecimal.ZERO : prevLine.getGrossAmount();
            BigDecimal lastNet = prevLine.getNetAmount() == null ? BigDecimal.ZERO : prevLine.getNetAmount();
            BigDecimal delta = currNet.subtract(lastNet);
            BigDecimal pct = lastNet.signum() == 0 ? BigDecimal.ZERO : delta.divide(lastNet, Math.max(4, rule.scale + 2), rule.roundingMode);

            PayrollPreviewDto.DiffSummaryDto d = new PayrollPreviewDto.DiffSummaryDto();
            d.setLastGrossAmount(lastGross.setScale(rule.scale, rule.roundingMode));
            d.setLastNetAmount(lastNet.setScale(rule.scale, rule.roundingMode));
            d.setNetDeltaAmount(delta.setScale(rule.scale, rule.roundingMode));
            d.setNetDeltaPercent(pct);
            return d;
        } catch (Exception e) {
            log.warn("buildDiff failed: {}", e.getMessage());
            return null;
        }
    }

    private PayrollBatch findPreviousBatch(PayrollBatch current) {
        try {
            String type = current.getType();
            var wrapper = new LambdaQueryWrapper<PayrollBatch>()
                    .eq(PayrollBatch::getType, type)
                    .ne(PayrollBatch::getId, current.getId());
            if (current.getPayCycleId() != null) {
                wrapper.lt(PayrollBatch::getPayCycleId, current.getPayCycleId())
                        .orderByDesc(PayrollBatch::getPayCycleId)
                        .orderByDesc(PayrollBatch::getCreateTime);
            } else if (current.getCreateTime() != null) {
                wrapper.lt(PayrollBatch::getCreateTime, current.getCreateTime())
                        .orderByDesc(PayrollBatch::getCreateTime);
            } else {
                wrapper.lt(PayrollBatch::getId, current.getId())
                        .orderByDesc(PayrollBatch::getId);
            }
            wrapper.last("limit 1");
            var list = payrollBatchMapper.selectList(wrapper);
            if (list == null || list.isEmpty()) return null;
            return list.get(0);
        } catch (Exception e) {
            log.warn("findPreviousBatch failed: {}", e.getMessage());
            return null;
        }
    }

    private static class LinesSummary {
    final java.util.Map<Long, PayrollPreviewDto.PayrollPreviewLineDto> linesByEmployee = new java.util.LinkedHashMap<>();
    final java.util.List<PayrollPreviewDto.PayrollPreviewLineDto> orderedLines = new java.util.ArrayList<>();
    int linesWithWarnings;
    int linesWithBlockingIssues;
    int totalWarnings;
    int blockingIssueCount;
    int reviewIssueCount;
    BigDecimal earningsTotal = BigDecimal.ZERO;
    BigDecimal deductionsTotal = BigDecimal.ZERO;
    BigDecimal grossTotal = BigDecimal.ZERO;
    BigDecimal taxTotal = BigDecimal.ZERO;
    BigDecimal socialTotal = BigDecimal.ZERO;
    BigDecimal netTotal = BigDecimal.ZERO;
}

    private static class PreviewContext {
        PayrollBatch batch;
        SalaryTemplate template;
        BasicCalcRule rule;
        java.util.Map<String, ItemRule> expectedItemRules;
        java.util.Map<String, SalaryItem> defByCode;
        java.util.Map<Long, java.util.List<PayrollImportItem>> itemsByEmployee;
        java.util.Map<Long, Employee> employeeMap;
        java.util.Map<Long, Employee> managerMap;
        java.util.Map<Long, PayrollCumulativeTaxService.TaxComputation> taxComputations = new java.util.HashMap<>();
        java.util.Map<Long, PayrollContributionCalculationService.Result> contributionComputations = new java.util.HashMap<>();
        java.util.List<PayrollLine> persistedLines = java.util.Collections.emptyList();
        java.util.List<PayrollValidationIssueDto> globalIssues = new java.util.ArrayList<>();
        String inputSnapshotHash;
        String inputSnapshotJson;
        String ruleSnapshotHash;
        String ruleSnapshotJson;
        String calculationEngineVersion = PayrollCalculationSnapshotSupport.LEGACY_BASIC_ENGINE_VERSION;
        int calculationRevision = 1;
        boolean createNewRevision;
    }

    private record FailureGuard(LocalDateTime updateTime, Integer version) {
        static FailureGuard from(PayrollBatch batch) {
            if (batch == null) {
                return empty();
            }
            return new FailureGuard(batch.getUpdateTime(), batch.getVersion());
        }

        static FailureGuard empty() {
            return new FailureGuard(null, null);
        }
    }

    private static class BasicCalcRule {
        enum ApplyOn { TAXABLE_EARNINGS, GROSS, EARNINGS_MINUS_DEDUCTIONS, TAXABLE_EARNINGS_MINUS_DEDUCTIONS;
            static ApplyOn from(String s) {
                if (s == null) return TAXABLE_EARNINGS;
                return switch (s.trim().toLowerCase()) {
                    case "gross" -> GROSS;
                    case "earningsminusdeductions", "earnings_minus_deductions" -> EARNINGS_MINUS_DEDUCTIONS;
                    case "taxableearningsminusdeductions", "taxable_earnings_minus_deductions" -> TAXABLE_EARNINGS_MINUS_DEDUCTIONS;
                    case "taxableearnings", "taxable_earnings" -> TAXABLE_EARNINGS;
                    default -> TAXABLE_EARNINGS;
                };
            }
        }
        BigDecimal taxRate = BigDecimal.ZERO;
        BigDecimal socialRate = BigDecimal.ZERO;
        boolean cumulativeWithholding = true;
        ApplyOn taxApplyOn = ApplyOn.TAXABLE_EARNINGS;
        ApplyOn socialApplyOn = ApplyOn.GROSS;
        int scale = 2;
        RoundingMode roundingMode = RoundingMode.HALF_UP;
        BigDecimal netDeltaWarnPct; // e.g. 0.2 for 20%
        String parseError;

        static RoundingMode rounding(String s) {
            if (s == null) return RoundingMode.HALF_UP;
            try { return RoundingMode.valueOf(s.trim().toUpperCase()); }
            catch (Exception e) { return RoundingMode.HALF_UP; }
        }
    }

    private static class ItemRule {
        Boolean required;
        BigDecimal min;
        BigDecimal max;
    }
}
