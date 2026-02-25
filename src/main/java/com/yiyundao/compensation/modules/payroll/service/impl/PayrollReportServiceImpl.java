package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollBasicReportDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollPreviewDto;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.payroll.service.PayrollReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollReportServiceImpl implements PayrollReportService {

    private final PayrollLineService payrollLineService;
    private final PayrollBatchService payrollBatchService;
    private final EmployeeService employeeService;
    private final ObjectMapper objectMapper;

    @Override
    public PayrollBasicReportDto basicReport(Long batchId, String periodLabel, String department) {
        List<Long> batchIds = resolveBatchIds(batchId, periodLabel);
        if (batchIds.isEmpty()) {
            return emptyReport();
        }
        List<PayrollLine> lines = payrollLineService.list(new LambdaQueryWrapper<PayrollLine>()
                .in(PayrollLine::getBatchId, batchIds)
        );
        if (CollectionUtils.isEmpty(lines)) {
            PayrollBasicReportDto dto = emptyReport();
            dto.setBatchId(batchId);
            dto.setPeriodLabel(periodLabel);
            return dto;
        }

        Map<Long, PayrollBatch> batchMap = loadBatchMap(batchIds);
        Map<Long, Employee> employeeMap = loadEmployeeMap(lines);
        Map<String, PayrollBasicReportDto.DepartmentSummary> departmentMap = new LinkedHashMap<>();

        BigDecimal totalEarnings = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalSocial = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        Set<Long> countedEmployees = new HashSet<>();

        for (PayrollLine line : lines) {
            Employee employee = employeeMap.get(line.getEmployeeId());
            String dept = employee != null && StringUtils.hasText(employee.getDepartment())
                    ? employee.getDepartment()
                    : "未分配";
            if (StringUtils.hasText(department) && !department.equalsIgnoreCase(dept)) {
                continue;
            }

            PayrollBasicReportDto.DepartmentSummary summary = departmentMap.computeIfAbsent(dept, key -> {
                PayrollBasicReportDto.DepartmentSummary d = new PayrollBasicReportDto.DepartmentSummary();
                d.setDepartment(key);
                d.setEmployeeCount(0);
                d.setEarningsTotal(BigDecimal.ZERO);
                d.setDeductionsTotal(BigDecimal.ZERO);
                d.setGrossTotal(BigDecimal.ZERO);
                d.setTaxTotal(BigDecimal.ZERO);
                d.setSocialTotal(BigDecimal.ZERO);
                d.setNetTotal(BigDecimal.ZERO);
                return d;
            });

            List<PayrollPreviewDto.PayrollPreviewItemDto> items = parseItems(line.getItemsSnapshotJson());
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

            summary.setEmployeeCount(summary.getEmployeeCount() + 1);
            summary.setEarningsTotal(summary.getEarningsTotal().add(earnings));
            summary.setDeductionsTotal(summary.getDeductionsTotal().add(deductions));
            summary.setGrossTotal(summary.getGrossTotal().add(safe(line.getGrossAmount())));
            summary.setTaxTotal(summary.getTaxTotal().add(safe(line.getTaxAmount())));
            summary.setSocialTotal(summary.getSocialTotal().add(safe(line.getSocialAmount())));
            summary.setNetTotal(summary.getNetTotal().add(safe(line.getNetAmount())));

            totalEarnings = totalEarnings.add(earnings);
            totalDeductions = totalDeductions.add(deductions);
            totalGross = totalGross.add(safe(line.getGrossAmount()));
            totalTax = totalTax.add(safe(line.getTaxAmount()));
            totalSocial = totalSocial.add(safe(line.getSocialAmount()));
            totalNet = totalNet.add(safe(line.getNetAmount()));
            if (line.getEmployeeId() != null) {
                countedEmployees.add(line.getEmployeeId());
            }
        }

        PayrollBasicReportDto dto = new PayrollBasicReportDto();
        dto.setBatchId(batchId != null ? batchId : (!batchIds.isEmpty() ? batchIds.get(0) : null));
        if (dto.getBatchId() != null) {
            PayrollBatch batch = batchMap.get(dto.getBatchId());
            if (batch != null) {
                dto.setPeriodLabel(batch.getPeriodLabel());
                dto.setCurrency(batch.getCurrency());
            }
        } else if (!batchMap.isEmpty()) {
            PayrollBatch first = batchMap.values().iterator().next();
            dto.setPeriodLabel(first.getPeriodLabel());
            dto.setCurrency(first.getCurrency());
        } else {
            dto.setPeriodLabel(periodLabel);
        }
        dto.setEarningsTotal(totalEarnings);
        dto.setDeductionsTotal(totalDeductions);
        dto.setGrossTotal(totalGross);
        dto.setTaxTotal(totalTax);
        dto.setSocialTotal(totalSocial);
        dto.setNetTotal(totalNet);
        dto.setEmployeeCount(countedEmployees.size());
        dto.setDepartments(new ArrayList<>(departmentMap.values()));
        return dto;
    }

    @Override
    public byte[] exportBasicReport(Long batchId, String periodLabel, String department) {
        PayrollBasicReportDto report = basicReport(batchId, periodLabel, department);
        DecimalFormat format = new DecimalFormat("0.00");
        StringBuilder sb = new StringBuilder();
        sb.append("Department,Employees,Earnings,Deductions,Gross,Tax,Social,Net\n");
        if (report.getDepartments() != null) {
            for (PayrollBasicReportDto.DepartmentSummary summary : report.getDepartments()) {
                sb.append(summary.getDepartment()).append(',')
                        .append(summary.getEmployeeCount()).append(',')
                        .append(format.format(summary.getEarningsTotal())).append(',')
                        .append(format.format(summary.getDeductionsTotal())).append(',')
                        .append(format.format(summary.getGrossTotal())).append(',')
                        .append(format.format(summary.getTaxTotal())).append(',')
                        .append(format.format(summary.getSocialTotal())).append(',')
                        .append(format.format(summary.getNetTotal())).append('\n');
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<Long> resolveBatchIds(Long batchId, String periodLabel) {
        if (batchId != null) {
            return Collections.singletonList(batchId);
        }
        if (!StringUtils.hasText(periodLabel)) {
            return Collections.emptyList();
        }
        List<PayrollBatch> batches = payrollBatchService.list(new LambdaQueryWrapper<PayrollBatch>()
                .eq(PayrollBatch::getPeriodLabel, periodLabel)
        );
        if (CollectionUtils.isEmpty(batches)) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        for (PayrollBatch batch : batches) {
            if (batch != null && batch.getId() != null) {
                ids.add(batch.getId());
            }
        }
        return ids;
    }

    private Map<Long, PayrollBatch> loadBatchMap(List<Long> batchIds) {
        if (CollectionUtils.isEmpty(batchIds)) {
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

    private Map<Long, Employee> loadEmployeeMap(List<PayrollLine> lines) {
        Set<Long> employeeIds = new HashSet<>();
        for (PayrollLine line : lines) {
            if (line.getEmployeeId() != null) {
                employeeIds.add(line.getEmployeeId());
            }
        }
        if (employeeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Employee> employees = employeeService.listByIds(employeeIds);
        Map<Long, Employee> map = new HashMap<>();
        for (Employee employee : employees) {
            if (employee != null && employee.getId() != null) {
                map.put(employee.getId(), employee);
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
            log.warn("Failed to parse payroll line items: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private PayrollBasicReportDto emptyReport() {
        PayrollBasicReportDto dto = new PayrollBasicReportDto();
        dto.setDepartments(Collections.emptyList());
        dto.setEarningsTotal(BigDecimal.ZERO);
        dto.setDeductionsTotal(BigDecimal.ZERO);
        dto.setGrossTotal(BigDecimal.ZERO);
        dto.setTaxTotal(BigDecimal.ZERO);
        dto.setSocialTotal(BigDecimal.ZERO);
        dto.setNetTotal(BigDecimal.ZERO);
        dto.setEmployeeCount(0);
        return dto;
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
