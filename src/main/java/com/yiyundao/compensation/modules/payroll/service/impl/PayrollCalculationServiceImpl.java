package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollLedgerDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollManagerReviewDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollPreviewDto;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollImportItem;
import com.yiyundao.compensation.modules.payroll.entity.SalaryItem;
import com.yiyundao.compensation.modules.payroll.entity.SalaryTemplate;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.infrastructure.dao.PayrollImportItemMapper;
import com.yiyundao.compensation.modules.payroll.service.SalaryItemService;
import com.yiyundao.compensation.modules.payroll.service.SalaryTemplateService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollCalculationServiceImpl implements PayrollCalculationService {

    private final PayrollBatchService payrollBatchService;
    private final PayrollImportItemMapper importItemMapper;
    private final SalaryItemService salaryItemService;
    private final SalaryTemplateService salaryTemplateService;
    private final PayrollLineService payrollLineService;
    private final EmployeeService employeeService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Override
    public boolean dryRun(Long batchId) {
        log.info("Dry-run payroll calculation for batch: {}", batchId);
        PayrollBatch batch = payrollBatchService.getById(batchId);
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

        LinesSummary summary = computeLines(ctx);
        java.util.List<PayrollLine> entities = new java.util.ArrayList<>();
        for (var entry : summary.linesByEmployee.entrySet()) {
            entities.add(toPayrollLine(ctx, entry.getKey(), entry.getValue(), null));
        }

        payrollLineService.remove(new LambdaQueryWrapper<PayrollLine>().eq(PayrollLine::getBatchId, batchId));
        if (!entities.isEmpty()) {
            payrollLineService.saveBatch(entities);
        }
        return true;
    }

    @Override
    @Transactional
    public boolean recomputeLine(Long batchId, Long employeeId) {
        log.info("Recompute payroll line: batch={}, employeeId= {}", batchId, employeeId);
        PreviewContext ctx = prepareContext(batchId, true);
        if (ctx == null) return false;
        if (!canPersist(ctx.batch)) {
            log.warn("Batch status not allowed for recompute: id={} status={}", ctx.batch.getId(), ctx.batch.getStatus());
            return false;
        }
        var records = ctx.itemsByEmployee.get(employeeId);
        if (records == null || records.isEmpty()) {
            payrollLineService.remove(new LambdaQueryWrapper<PayrollLine>()
                    .eq(PayrollLine::getBatchId, batchId)
                    .eq(PayrollLine::getEmployeeId, employeeId));
            return true;
        }

        var previewLine = buildPreviewLine(ctx, employeeId, records);
        var existing = payrollLineService.getOne(new LambdaQueryWrapper<PayrollLine>()
                .eq(PayrollLine::getBatchId, batchId)
                .eq(PayrollLine::getEmployeeId, employeeId)
                .last("limit 1"));
        PayrollLine entity = toPayrollLine(ctx, employeeId, previewLine, existing);
        if (existing != null) {
            entity.setId(existing.getId());
            entity.setVersion(existing.getVersion());
        }
        return payrollLineService.saveOrUpdate(entity);
    }

    @Override
    public boolean validateReady(PayrollBatch batch) {
        if (batch == null) return false;
        if (batch.getStatus() == null) return false;
        PayrollBatchStatus status = batch.getStatus();
        return status == PayrollBatchStatus.DRAFT ||
               status == PayrollBatchStatus.LOCKED ||
               status == PayrollBatchStatus.SUBMITTED ||
               status == PayrollBatchStatus.APPROVED ||
               status == PayrollBatchStatus.REJECTED;
    }

    @Override
    public PayrollPreviewDto dryRunPreview(Long batchId) {
        PreviewContext ctx = prepareContext(batchId, true);
        if (ctx == null) return null;

        LinesSummary summary = computeLines(ctx);

        PayrollPreviewDto preview = new PayrollPreviewDto();
        preview.setBatchId(batchId);
        preview.setCurrency(ctx.batch.getCurrency());
        preview.setLines(summary.orderedLines);
        preview.setTotalEmployees(summary.orderedLines.size());
        preview.setLinesWithWarnings(summary.linesWithWarnings);
        preview.setTotalWarnings(summary.totalWarnings);
        preview.setEarningsTotal(summary.earningsTotal);
        preview.setDeductionsTotal(summary.deductionsTotal);
        preview.setGrossTotal(summary.grossTotal);
        preview.setTaxTotal(summary.taxTotal);
        preview.setSocialTotal(summary.socialTotal);
        preview.setNetTotal(summary.netTotal);
        if (!ctx.globalWarnings.isEmpty()) {
            preview.setWarnings(ctx.globalWarnings);
        }
        return preview;
    }

    @Override
    public PayrollLedgerDto ledger(Long batchId) {
        PreviewContext ctx = prepareContext(batchId, false);
        PayrollBatch batch = ctx != null ? ctx.batch : payrollBatchService.getById(batchId);
        if (batch == null) {
            return null;
        }

        var persisted = payrollLineService.list(new LambdaQueryWrapper<PayrollLine>()
                .eq(PayrollLine::getBatchId, batchId)
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
        int totalWarnings = 0;
        java.util.Set<Long> seen = new java.util.HashSet<>();

        java.util.List<String> warnings = new java.util.ArrayList<>();
        if (ctx != null && ctx.globalWarnings != null && !ctx.globalWarnings.isEmpty()) {
            warnings.addAll(ctx.globalWarnings);
        }

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
                    view.setWarnings(base.getWarnings());
                    view.setMissingItems(base.getMissingItems());
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
                    view.setWarnings(java.util.Collections.emptyList());
                    view.setMissingItems(java.util.Collections.emptyList());
                }

                if (view.getWarnings() == null) {
                    view.setWarnings(java.util.Collections.emptyList());
                }
                if (view.getMissingItems() == null) {
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
                if ((items == null || items.isEmpty()) && base != null) {
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

                lines.add(view);
                earningsTotal = earningsTotal.add(earnings);
                deductionsTotal = deductionsTotal.add(deductions);
                grossTotal = grossTotal.add(gross);
                taxTotal = taxTotal.add(tax);
                socialTotal = socialTotal.add(social);
                netTotal = netTotal.add(net);

                int warningCount = view.getWarnings() == null ? 0 : view.getWarnings().size();
                if (warningCount > 0 || (view.getMissingItems() != null && !view.getMissingItems().isEmpty())) {
                    linesWithWarnings++;
                }
                totalWarnings += warningCount;
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
            if (warningCount > 0 || (line.getMissingItems() != null && !line.getMissingItems().isEmpty())) {
                linesWithWarnings++;
            }
            totalWarnings += warningCount;
        }

        if (persisted.isEmpty()) {
            warnings.add("no persisted payroll lines; run compute to finalize amounts");
        }

        PayrollLedgerDto dto = new PayrollLedgerDto();
        dto.setBatchId(batchId);
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
        dto.setTotalWarnings(totalWarnings);
        dto.setWarnings(warnings);
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

        java.util.List<PayrollPreviewDto.PayrollPreviewLineDto> filtered = new java.util.ArrayList<>();
        BigDecimal earningsTotal = BigDecimal.ZERO;
        BigDecimal deductionsTotal = BigDecimal.ZERO;
        BigDecimal grossTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal socialTotal = BigDecimal.ZERO;
        BigDecimal netTotal = BigDecimal.ZERO;
        int linesWithWarnings = 0;
        int totalWarnings = 0;

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
            if (warningCount > 0 || (line.getMissingItems() != null && !line.getMissingItems().isEmpty())) {
                linesWithWarnings++;
            }
            totalWarnings += warningCount;
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
        dto.setTotalWarnings(totalWarnings);
        dto.setWarnings(ctx.globalWarnings == null ? java.util.Collections.emptyList() : new java.util.ArrayList<>(ctx.globalWarnings));
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
            if (def == null) continue; // should not happen due to import validation
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

        // Compute tax/social
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

        BigDecimal tax = ctx.rule.taxRate.multiply(maxZero(taxBase));
        BigDecimal social = ctx.rule.socialRate.multiply(maxZero(socialBase));
        tax = tax.setScale(ctx.rule.scale, ctx.rule.roundingMode);
        social = social.setScale(ctx.rule.scale, ctx.rule.roundingMode);
        line.setTaxAmount(tax);
        line.setSocialAmount(social);
        line.setNetAmount(earnings.subtract(deductions).subtract(tax).subtract(social)
                .setScale(ctx.rule.scale, ctx.rule.roundingMode));

        // Warnings: missing/thresholds
        java.util.List<String> warnings = new java.util.ArrayList<>();
        java.util.List<String> missingItems = new java.util.ArrayList<>();
        java.util.Set<String> presentCodes = new java.util.HashSet<>();
        for (var it : pItems) presentCodes.add(it.getCode());
        if (ctx.expectedItemRules != null && !ctx.expectedItemRules.isEmpty()) {
            for (var entryRule : ctx.expectedItemRules.entrySet()) {
                String code = entryRule.getKey();
                ItemRule r = entryRule.getValue();
                boolean present = presentCodes.contains(code);
                if (Boolean.TRUE.equals(r.required) && !present) {
                    missingItems.add(code);
                    warnings.add("missing required item: " + code);
                }
                if (present && (r.min != null || r.max != null)) {
                    for (var it : pItems) {
                        if (code.equals(it.getCode()) && it.getAmount() != null) {
                            if (r.min != null && it.getAmount().compareTo(r.min) < 0) {
                                warnings.add("item " + code + " below min: " + it.getAmount());
                            }
                            if (r.max != null && it.getAmount().compareTo(r.max) > 0) {
                                warnings.add("item " + code + " above max: " + it.getAmount());
                            }
                        }
                    }
                }
            }
        }

        // Diff vs previous cycle
        var diff = buildDiff(ctx.batch, empId, line.getGrossAmount(), line.getNetAmount(), ctx.rule);
        if (diff != null && diff.getNetDeltaPercent() != null && ctx.rule.netDeltaWarnPct != null) {
            BigDecimal absPct = diff.getNetDeltaPercent().abs();
            if (absPct.compareTo(ctx.rule.netDeltaWarnPct) > 0) {
                warnings.add("net change exceeds threshold: " + diff.getNetDeltaPercent());
            }
        }
        if (ctx.template == null) {
            warnings.add("no active salary template for batch type " + ctx.batch.getType());
        }
        line.setWarnings(warnings);
        line.setMissingItems(missingItems);
        line.setDiff(diff);
        return line;
    }

    private LinesSummary computeLines(PreviewContext ctx) {
        LinesSummary summary = new LinesSummary();
        if (ctx == null || ctx.itemsByEmployee == null || ctx.itemsByEmployee.isEmpty()) {
            return summary;
        }
        int linesWithWarnings = 0;
        int totalWarnings = 0;
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
            if (warningCount > 0 || (line.getMissingItems() != null && !line.getMissingItems().isEmpty())) {
                linesWithWarnings++;
            }
            totalWarnings += warningCount;
        }
        summary.linesWithWarnings = linesWithWarnings;
        summary.totalWarnings = totalWarnings;
        return summary;
    }

    private PreviewContext prepareContext(Long batchId, boolean requireReady) {
        PayrollBatch batch = payrollBatchService.getById(batchId);
        if (batch == null) {
            return null;
        }
        boolean ready = validateReady(batch);
        if (requireReady && !ready) {
            return null;
        }

        PreviewContext ctx = new PreviewContext();
        ctx.batch = batch;
        if (!ready) {
            ctx.globalWarnings.add("batch status " + batch.getStatus() + " is read-only");
        }
        ctx.template = resolveTemplateForBatch(batch);
        ctx.rule = parseBasicRule(ctx.template);
        ctx.expectedItemRules = parseItemRules(ctx.template);
        if (ctx.template == null) {
            ctx.globalWarnings.add("no salary template configured for batch type " + batch.getType());
        }

        ctx.defByCode = new java.util.HashMap<>();
        for (SalaryItem it : salaryItemService.list(new LambdaQueryWrapper<SalaryItem>().eq(SalaryItem::getStatus, "enabled"))) {
            ctx.defByCode.put(it.getCode(), it);
        }

        ctx.itemsByEmployee = new java.util.LinkedHashMap<>();
        var items = importItemMapper.selectList(new LambdaQueryWrapper<PayrollImportItem>()
                .eq(PayrollImportItem::getBatchId, batchId)
                .eq(PayrollImportItem::getStatus, "valid")
                .orderByAsc(PayrollImportItem::getEmployeeId));
        for (var it : items) {
            ctx.itemsByEmployee.computeIfAbsent(it.getEmployeeId(), k -> new java.util.ArrayList<>()).add(it);
        }

        ctx.employeeMap = new java.util.HashMap<>();
        if (!ctx.itemsByEmployee.isEmpty()) {
            java.util.Set<Long> empIds = new java.util.HashSet<>(ctx.itemsByEmployee.keySet());
            for (Employee e : employeeService.listByIds(empIds)) {
                ctx.employeeMap.put(e.getId(), e);
            }
        }

        ctx.managerMap = new java.util.HashMap<>();
        if (!ctx.employeeMap.isEmpty()) {
            java.util.Set<Long> managerIds = new java.util.HashSet<>();
            for (Employee e : ctx.employeeMap.values()) {
                if (e.getManagerId() != null) {
                    managerIds.add(e.getManagerId());
                }
            }
            if (!managerIds.isEmpty()) {
                for (Employee m : employeeService.listByIds(managerIds)) {
                    ctx.managerMap.put(m.getId(), m);
                }
            }
        }
        return ctx;
    }

    private boolean canPersist(PayrollBatch batch) {
        if (batch == null || batch.getStatus() == null) {
            return false;
        }
        PayrollBatchStatus status = batch.getStatus();
        return status == PayrollBatchStatus.LOCKED || status == PayrollBatchStatus.APPROVED;
    }

    private PayrollLine toPayrollLine(PreviewContext ctx,
                                      Long empId,
                                      PayrollPreviewDto.PayrollPreviewLineDto lineDto,
                                      PayrollLine existing) {
        PayrollLine entity = new PayrollLine();
        entity.setBatchId(ctx.batch.getId());
        entity.setEmployeeId(empId);
        entity.setTemplateId(ctx.template != null ? ctx.template.getId() : null);
        Employee employee = ctx.employeeMap.get(empId);
        entity.setEmploymentType(employee != null ? employee.getEmploymentType() : ctx.batch.getType());
        entity.setCurrency(ctx.batch.getCurrency());
        entity.setGrossAmount(lineDto.getGrossAmount());
        entity.setTaxAmount(lineDto.getTaxAmount());
        entity.setSocialAmount(lineDto.getSocialAmount());
        entity.setNetAmount(lineDto.getNetAmount());
        if (existing != null && existing.getStatus() != null) {
            entity.setStatus(existing.getStatus());
        } else {
            entity.setStatus("calculated");
        }
        try {
            entity.setItemsSnapshotJson(objectMapper.writeValueAsString(lineDto.getItems()));
        } catch (Exception e) {
            log.warn("serialize items snapshot failed: {}", e.getMessage());
            entity.setItemsSnapshotJson("[]");
        }
        return entity;
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
            for (Employee e : employeeService.listByIds(missing)) {
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
            for (Employee manager : employeeService.listByIds(managerIds)) {
                ctx.managerMap.put(manager.getId(), manager);
            }
        }
    }

    private java.util.List<PayrollPreviewDto.PayrollPreviewItemDto> parseItemsSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return java.util.Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<java.util.List<PayrollPreviewDto.PayrollPreviewItemDto>>() {});
        } catch (Exception e) {
            log.warn("parse items snapshot failed: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    private SalaryTemplate resolveTemplateForBatch(PayrollBatch batch) {
        try {
            var list = salaryTemplateService.list(new LambdaQueryWrapper<SalaryTemplate>()
                    .eq(SalaryTemplate::getType, batch.getType())
                    .eq(SalaryTemplate::getStatus, "enabled"));
            if (list == null || list.isEmpty()) return null;
            // pick the latest by id
            list.sort(java.util.Comparator.comparingLong(SalaryTemplate::getId).reversed());
            return list.get(0);
        } catch (Exception e) {
            log.warn("resolveTemplateForBatch failed: {}", e.getMessage());
            return null;
        }
    }

    private BasicCalcRule parseBasicRule(SalaryTemplate tpl) {
        BasicCalcRule rule = new BasicCalcRule();
        if (tpl == null || tpl.getTaxRuleJson() == null || tpl.getTaxRuleJson().isBlank()) return rule;
        try {
            var root = objectMapper.readTree(tpl.getTaxRuleJson());
            // tax
            var tax = root.path("tax");
            if (!tax.isMissingNode()) {
                if (tax.has("rate")) rule.taxRate = new BigDecimal(tax.get("rate").asText("0"));
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
            log.warn("parseBasicRule failed, fallback to default zero rates: {}", e.getMessage());
        }
        return rule;
    }

    private java.util.Map<String, ItemRule> parseItemRules(SalaryTemplate tpl) {
        if (tpl == null || tpl.getItemsJson() == null || tpl.getItemsJson().isBlank()) return java.util.Collections.emptyMap();
        try {
            var node = objectMapper.readTree(tpl.getItemsJson());
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
            var list = payrollBatchService.list(wrapper);
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
        int totalWarnings;
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
        java.util.List<String> globalWarnings = new java.util.ArrayList<>();
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
        ApplyOn taxApplyOn = ApplyOn.TAXABLE_EARNINGS;
        ApplyOn socialApplyOn = ApplyOn.GROSS;
        int scale = 2;
        RoundingMode roundingMode = RoundingMode.HALF_UP;
        BigDecimal netDeltaWarnPct; // e.g. 0.2 for 20%

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
