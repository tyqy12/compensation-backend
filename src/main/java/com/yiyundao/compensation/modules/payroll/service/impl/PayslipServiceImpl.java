package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.interfaces.dto.payroll.EmployeePayslipDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollPreviewDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollValidationIssueDto;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payroll.entity.PayCycle;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.PayCycleService;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.payroll.service.PayslipService;
import com.yiyundao.compensation.modules.payroll.support.PayrollValidationIssueSupport;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.security.SecurityConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayslipServiceImpl implements PayslipService {

    private final PayrollLineService payrollLineService;
    private final PayrollBatchService payrollBatchService;
    private final PayCycleService payCycleService;
    private final EmployeeService employeeService;
    private final ObjectMapper objectMapper;
    private final PayrollValidationIssueSupport validationIssueSupport;
    private final UserRoleService userRoleService;

    @Override
    public Page<EmployeePayslipDto.PayslipSummary> pagePayslips(SysUser currentUser, Long employeeId, int page, int size) {
        enforceAuthenticated(currentUser);
        Long targetEmployeeId = resolveTargetEmployee(currentUser, employeeId);
        if (targetEmployeeId == null) {
            long current = Math.max(page, 1);
            long sizeVal = Math.max(size, 1);
            return new Page<>(current, sizeVal, 0L);
        }

        long pageIndex = Math.max(page, 1);
        long pageSize = Math.max(size, 1);
        Page<PayrollLine> entityPage = payrollLineService.page(
                new Page<>(pageIndex, pageSize),
                new LambdaQueryWrapper<PayrollLine>()
                        .eq(PayrollLine::getEmployeeId, targetEmployeeId)
                        .orderByDesc(PayrollLine::getId)
        );

        Page<EmployeePayslipDto.PayslipSummary> result = new Page<>(pageIndex, pageSize, entityPage.getTotal());
        if (entityPage.getRecords() == null || entityPage.getRecords().isEmpty()) {
            result.setRecords(Collections.emptyList());
            return result;
        }

        Map<Long, PayrollBatch> batchMap = loadBatches(entityPage.getRecords());
        Map<Long, PayCycle> cycleMap = loadCycles(batchMap.values());

        List<EmployeePayslipDto.PayslipSummary> summaries = entityPage.getRecords().stream().map(line -> {
            EmployeePayslipDto.PayslipSummary summary = new EmployeePayslipDto.PayslipSummary();
            summary.setLineId(line.getId());
            summary.setBatchId(line.getBatchId());
            summary.setCurrency(line.getCurrency());
            summary.setGrossAmount(safe(line.getGrossAmount()));
            summary.setTaxAmount(safe(line.getTaxAmount()));
            summary.setSocialAmount(safe(line.getSocialAmount()));
            summary.setNetAmount(safe(line.getNetAmount()));
            summary.setStatus(line.getStatus());
            summary.setConfirmationStatus(line.getConfirmationStatus());
            summary.setConfirmationAssigneeEmployeeId(line.getConfirmationAssigneeEmployeeId());
            summary.setConfirmedAt(line.getConfirmedAt());
            summary.setObjectionReason(line.getObjectionReason());

            PayrollBatch batch = batchMap.get(line.getBatchId());
            if (batch != null) {
                summary.setPeriodLabel(batch.getPeriodLabel());
                summary.setPayCycleId(batch.getPayCycleId());
            }
            if (summary.getPayCycleId() != null) {
                PayCycle cycle = cycleMap.get(summary.getPayCycleId());
                if (cycle != null) {
                    summary.setPeriodStart(cycle.getStartDate());
                    summary.setPeriodEnd(cycle.getEndDate());
                }
            }
            return summary;
        }).toList();

        result.setRecords(summaries);
        return result;
    }

    @Override
    public EmployeePayslipDto.PayslipDetail getPayslipDetail(SysUser currentUser, Long lineId) {
        enforceAuthenticated(currentUser);
        PayrollLine line = payrollLineService.getById(lineId);
        if (line == null) {
            return null;
        }
        ensureAccess(currentUser, line);

        PayrollBatch batch = line.getBatchId() != null ? payrollBatchService.getById(line.getBatchId()) : null;
        PayCycle cycle = (batch != null && batch.getPayCycleId() != null) ? payCycleService.getById(batch.getPayCycleId()) : null;
        Employee employee = line.getEmployeeId() != null ? employeeService.getById(line.getEmployeeId()) : null;

        EmployeePayslipDto.PayslipDetail detail = new EmployeePayslipDto.PayslipDetail();
        detail.setLineId(line.getId());
        detail.setBatchId(line.getBatchId());
        detail.setCurrency(line.getCurrency());
        detail.setGrossAmount(safe(line.getGrossAmount()));
        detail.setTaxAmount(safe(line.getTaxAmount()));
        detail.setSocialAmount(safe(line.getSocialAmount()));
        detail.setNetAmount(safe(line.getNetAmount()));
        detail.setConfirmationStatus(line.getConfirmationStatus());
        detail.setConfirmationAssigneeEmployeeId(line.getConfirmationAssigneeEmployeeId());
        detail.setConfirmedAt(line.getConfirmedAt());
        detail.setConfirmationComment(line.getConfirmationComment());
        detail.setObjectionReason(line.getObjectionReason());
        detail.setDisputeWorkflowId(line.getDisputeWorkflowId());
        List<PayrollValidationIssueDto> issues = validationIssueSupport.deserialize(line.getWarning());
        detail.setIssues(issues);
        detail.setWarnings(validationIssueSupport.toMessages(issues));
        detail.setBlockingIssueCount(validationIssueSupport.countBlocking(issues));
        detail.setReviewIssueCount(validationIssueSupport.countReview(issues));
        detail.setHasBlockingIssues(validationIssueSupport.hasBlocking(issues));

        if (batch != null) {
            detail.setPeriodLabel(batch.getPeriodLabel());
            detail.setPayCycleId(batch.getPayCycleId());
        }
        if (cycle != null) {
            detail.setPeriodStart(cycle.getStartDate());
            detail.setPeriodEnd(cycle.getEndDate());
        }
        if (employee != null) {
            detail.setEmployeeNo(employee.getEmployeeId());
            detail.setEmployeeName(employee.getName());
            detail.setDepartment(employee.getDepartment());
            detail.setEmploymentType(employee.getEmploymentType());
            detail.setBankName(employee.getBankName());
            detail.setBankAccountMasked(maskBankAccount(employee.getBankAccount()));
        }

        List<PayrollPreviewDto.PayrollPreviewItemDto> items = parseItems(line.getItemsSnapshotJson());
        detail.setItems(items);

        BigDecimal earnings = BigDecimal.ZERO;
        BigDecimal deductions = BigDecimal.ZERO;
        for (PayrollPreviewDto.PayrollPreviewItemDto item : items) {
            if (item == null || item.getAmount() == null) {
                continue;
            }
            if ("earning".equalsIgnoreCase(item.getType())) {
                earnings = earnings.add(item.getAmount());
            } else {
                deductions = deductions.add(item.getAmount());
            }
        }
        detail.setEarningsTotal(earnings);
        detail.setDeductionsTotal(deductions);

        return detail;
    }

    @Override
    public byte[] exportPayslip(SysUser currentUser, Long lineId) {
        EmployeePayslipDto.PayslipDetail detail = getPayslipDetail(currentUser, lineId);
        if (detail == null) {
            return null;
        }
        DecimalFormat decimalFormat = new DecimalFormat("0.00");
        StringBuilder sb = new StringBuilder();
        sb.append("Payslip for ").append(nullSafe(detail.getEmployeeName())).append('\n');
        sb.append("Period,").append(nullSafe(detail.getPeriodLabel())).append('\n');
        sb.append("Currency,").append(nullSafe(detail.getCurrency())).append('\n');
        sb.append("Gross, ").append(decimalFormat.format(detail.getGrossAmount())).append('\n');
        sb.append("Tax, ").append(decimalFormat.format(detail.getTaxAmount())).append('\n');
        sb.append("Social, ").append(decimalFormat.format(detail.getSocialAmount())).append('\n');
        sb.append("Net, ").append(decimalFormat.format(detail.getNetAmount())).append('\n');
        sb.append("Bank, ").append(nullSafe(detail.getBankName())).append('\n');
        sb.append("Account, ").append(nullSafe(detail.getBankAccountMasked())).append('\n');
        sb.append('\n');
        sb.append("Item Code,Item Name,Type,Taxable,Amount\n");
        for (PayrollPreviewDto.PayrollPreviewItemDto item : detail.getItems()) {
            if (item == null) {
                continue;
            }
            sb.append(nullSafe(item.getCode())).append(',')
                    .append(nullSafe(item.getName())).append(',')
                    .append(nullSafe(item.getType())).append(',')
                    .append(item.getTaxable() != null && item.getTaxable() ? "Y" : "N").append(',')
                    .append(decimalFormat.format(item.getAmount() == null ? BigDecimal.ZERO : item.getAmount()))
                    .append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void enforceAuthenticated(SysUser currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("未登录");
        }
    }

    private Long resolveTargetEmployee(SysUser currentUser, Long requestedEmployee) {
        if (requestedEmployee != null) {
            if (hasAnyRole(currentUser, SecurityConstants.ROLE_ADMIN, SecurityConstants.ROLE_FINANCE)) {
                return requestedEmployee;
            }
            Long owned = currentUser.getEmployeeId();
            if (owned != null && owned.equals(requestedEmployee)) {
                return owned;
            }
            throw new AccessDeniedException("无权访问其他员工工资条");
        }
        if (currentUser.getEmployeeId() != null) {
            return currentUser.getEmployeeId();
        }
        if (hasAnyRole(currentUser, SecurityConstants.ROLE_ADMIN, SecurityConstants.ROLE_FINANCE)) {
            return null;
        }
        throw new AccessDeniedException("当前账号未绑定员工信息");
    }

    private void ensureAccess(SysUser currentUser, PayrollLine line) {
        if (line == null) {
            throw new AccessDeniedException("工资条不存在");
        }
        Long employeeId = currentUser.getEmployeeId();
        if (employeeId != null && employeeId.equals(line.getEmployeeId())) {
            return;
        }
        if (employeeId != null && line.getConfirmationAssigneeEmployeeId() != null
                && employeeId.equals(line.getConfirmationAssigneeEmployeeId())) {
            return;
        }
        if (hasAnyRole(currentUser, SecurityConstants.ROLE_ADMIN, SecurityConstants.ROLE_FINANCE)) {
            return;
        }
        throw new AccessDeniedException("无权查看该工资条");
    }

    private Map<Long, PayrollBatch> loadBatches(List<PayrollLine> lines) {
        Set<Long> batchIds = new HashSet<>();
        for (PayrollLine l : lines) {
            if (l.getBatchId() != null) {
                batchIds.add(l.getBatchId());
            }
        }
        if (batchIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PayrollBatch> batches = payrollBatchService.listByIds(batchIds);
        Map<Long, PayrollBatch> map = new HashMap<>();
        for (PayrollBatch batch : batches) {
            if (batch != null && batch.getId() != null) {
                map.put(batch.getId(), batch);
            }
        }
        return map;
    }

    private Map<Long, PayCycle> loadCycles(Iterable<PayrollBatch> batches) {
        if (batches == null) {
            return Collections.emptyMap();
        }
        Set<Long> cycleIds = new HashSet<>();
        for (PayrollBatch batch : batches) {
            if (batch != null && batch.getPayCycleId() != null) {
                cycleIds.add(batch.getPayCycleId());
            }
        }
        if (cycleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PayCycle> cycles = payCycleService.listByIds(cycleIds);
        Map<Long, PayCycle> map = new HashMap<>();
        for (PayCycle cycle : cycles) {
            if (cycle != null && cycle.getId() != null) {
                map.put(cycle.getId(), cycle);
            }
        }
        return map;
    }

    private List<PayrollPreviewDto.PayrollPreviewItemDto> parseItems(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<PayrollPreviewDto.PayrollPreviewItemDto>>() {});
        } catch (Exception e) {
            try {
                var node = objectMapper.readTree(json);
                if (node != null && node.isTextual()) {
                    return objectMapper.readValue(node.asText(), new TypeReference<List<PayrollPreviewDto.PayrollPreviewItemDto>>() {});
                }
            } catch (Exception nested) {
                log.warn("Failed to parse payslip items snapshot: {}", nested.getMessage());
            }
            log.warn("Failed to parse payslip items snapshot: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private boolean hasAnyRole(SysUser user, String... roles) {
        if (user == null || roles == null) {
            return false;
        }
        // 使用 UserRoleService 检查角色（带缓存）
        Set<String> userRoleCodes = userRoleService.getUserRoleCodes(user.getId());
        for (String role : roles) {
            if (role != null && userRoleCodes.contains(role)) {
                return true;
            }
        }
        return false;
    }

    private String maskBankAccount(String bankAccount) {
        if (!StringUtils.hasText(bankAccount)) {
            return null;
        }
        String raw = bankAccount.replaceAll("\\s", "");
        if (raw.length() <= 8) {
            return "****";
        }
        String prefix = raw.substring(0, 4);
        String suffix = raw.substring(raw.length() - 4);
        return prefix + " **** **** " + suffix;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
