package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.interfaces.dto.openapi.OpenApiPayrollBatchDto;
import com.yiyundao.compensation.interfaces.dto.openapi.OpenApiPayrollLineDto;
import com.yiyundao.compensation.interfaces.dto.openapi.OpenApiPayslipDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollPreviewDto;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.entity.SalaryItem;
import com.yiyundao.compensation.modules.payroll.service.ExternalPayrollQueryService;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.payroll.service.SalaryItemService;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.util.CollectionUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalPayrollQueryServiceImpl implements ExternalPayrollQueryService {

    private static final Set<PayrollBatchStatus> ALLOWED_BATCH_STATUSES = Set.of(
            PayrollBatchStatus.APPROVED,
            PayrollBatchStatus.PAID,
            PayrollBatchStatus.ARCHIVED
    );
    private static final Set<PayrollBatchStatus> DEFAULT_BATCH_STATUSES = Set.of(
            PayrollBatchStatus.APPROVED,
            PayrollBatchStatus.PAID
    );
    private static final String PT_TYPE = "part_time";

    private final PayrollBatchService payrollBatchService;
    private final PayrollLineService payrollLineService;
    private final EmployeeService employeeService;
    private final PaymentBatchService paymentBatchService;
    private final SalaryItemService salaryItemService;
    private final ObjectMapper objectMapper;
    private final EncryptionService encryptionService;

    @Override
    public Page<OpenApiPayrollBatchDto> pagePtBatches(String period, String status, long page, long size) {
        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), 100);

        LambdaQueryWrapper<PayrollBatch> wrapper = new LambdaQueryWrapper<PayrollBatch>()
                .eq(PayrollBatch::getType, PT_TYPE)
                .in(PayrollBatch::getStatus, resolveStatuses(status));

        if (StringUtils.hasText(period)) {
            wrapper.eq(PayrollBatch::getPeriodLabel, period.trim());
        }
        wrapper.orderByDesc(PayrollBatch::getUpdateTime);

        Page<PayrollBatch> entityPage = payrollBatchService.page(new Page<>(current, pageSize), wrapper);
        Page<OpenApiPayrollBatchDto> result = new Page<>(current, pageSize, entityPage.getTotal());
        if (CollectionUtils.isEmpty(entityPage.getRecords())) {
            result.setRecords(Collections.emptyList());
            return result;
        }

        List<PayrollBatch> batches = entityPage.getRecords();
        List<Long> batchIds = batches.stream().map(PayrollBatch::getId).toList();
        Map<Long, Long> lineCountMap = loadLineCounts(batchIds);
        Map<String, PaymentBatch> paymentBatchMap = loadPaymentBatches(batches);

        List<OpenApiPayrollBatchDto> dtos = batches.stream()
                .map(batch -> toBatchDto(batch, lineCountMap, paymentBatchMap))
                .toList();
        result.setRecords(dtos);
        return result;
    }

    @Override
    public OpenApiPayrollBatchDto findBatch(Long batchId) {
        if (batchId == null) {
            return null;
        }
        PayrollBatch batch = payrollBatchService.getById(batchId);
        if (batch == null || !PT_TYPE.equalsIgnoreCase(batch.getType())) {
            return null;
        }
        Map<Long, Long> lineCountMap = loadLineCounts(List.of(batch.getId()));
        Map<String, PaymentBatch> paymentBatchMap = loadPaymentBatches(List.of(batch));
        return toBatchDto(batch, lineCountMap, paymentBatchMap);
    }

    @Override
    public Page<OpenApiPayrollLineDto> pageBatchLines(Long batchId, String employeeRef, long page, long size) {
        if (batchId == null) {
            return emptyPage(page, size);
        }
        PayrollBatch batch = payrollBatchService.getById(batchId);
        if (batch == null || !PT_TYPE.equalsIgnoreCase(batch.getType())) {
            return emptyPage(page, size);
        }

        Long employeeInternalId = resolveEmployeeId(employeeRef);
        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), 100);

        LambdaQueryWrapper<PayrollLine> wrapper = new LambdaQueryWrapper<PayrollLine>()
                .eq(PayrollLine::getBatchId, batchId)
                .eq(PayrollLine::getEmploymentType, PT_TYPE)
                .orderByDesc(PayrollLine::getId);
        if (employeeInternalId != null) {
            wrapper.eq(PayrollLine::getEmployeeId, employeeInternalId);
        }

        Page<PayrollLine> entityPage = payrollLineService.page(new Page<>(current, pageSize), wrapper);
        Page<OpenApiPayrollLineDto> result = new Page<>(current, pageSize, entityPage.getTotal());
        if (CollectionUtils.isEmpty(entityPage.getRecords())) {
            result.setRecords(Collections.emptyList());
            return result;
        }

        Map<Long, Employee> employeeMap = loadEmployees(entityPage.getRecords());

        List<OpenApiPayrollLineDto> dtos = entityPage.getRecords().stream()
                .map(line -> toLineDto(line, employeeMap.get(line.getEmployeeId())))
                .toList();
        result.setRecords(dtos);
        return result;
    }

    @Override
    public List<OpenApiPayslipDto> findPayslips(String employeeRef, String period) {
        Long employeeId = resolveEmployeeId(employeeRef);
        if (employeeId == null || !StringUtils.hasText(period)) {
            return Collections.emptyList();
        }

        List<PayrollBatch> batches = payrollBatchService.list(new LambdaQueryWrapper<PayrollBatch>()
                .eq(PayrollBatch::getType, PT_TYPE)
                .eq(PayrollBatch::getPeriodLabel, period.trim())
                .in(PayrollBatch::getStatus, ALLOWED_BATCH_STATUSES));
        if (CollectionUtils.isEmpty(batches)) {
            return Collections.emptyList();
        }
        List<Long> batchIds = batches.stream().map(PayrollBatch::getId).toList();

        List<PayrollLine> lines = payrollLineService.list(new LambdaQueryWrapper<PayrollLine>()
                .in(PayrollLine::getBatchId, batchIds)
                .eq(PayrollLine::getEmployeeId, employeeId)
                .eq(PayrollLine::getEmploymentType, PT_TYPE));
        if (CollectionUtils.isEmpty(lines)) {
            return Collections.emptyList();
        }

        Employee employee = employeeService.getById(employeeId);
        Map<Long, PayrollBatch> batchMap = batches.stream().collect(Collectors.toMap(PayrollBatch::getId, b -> b));
        Map<String, SalaryItem> salaryItemMap = loadSalaryItems(lines);

        return lines.stream()
                .map(line -> toPayslipDto(line, batchMap.get(line.getBatchId()), employee, salaryItemMap))
                .toList();
    }

    @Override
    public OpenApiPayslipDto findPayslip(Long payslipId) {
        if (payslipId == null) {
            return null;
        }
        PayrollLine line = payrollLineService.getById(payslipId);
        if (line == null || !PT_TYPE.equalsIgnoreCase(line.getEmploymentType())) {
            return null;
        }
        PayrollBatch batch = payrollBatchService.getById(line.getBatchId());
        if (batch == null || !PT_TYPE.equalsIgnoreCase(batch.getType())) {
            return null;
        }
        Employee employee = employeeService.getById(line.getEmployeeId());
        Map<String, SalaryItem> salaryItemMap = loadSalaryItems(List.of(line));
        return toPayslipDto(line, batch, employee, salaryItemMap);
    }

    private Page<OpenApiPayrollLineDto> emptyPage(long page, long size) {
        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), 100);
        Page<OpenApiPayrollLineDto> result = new Page<>(current, pageSize, 0);
        result.setRecords(Collections.emptyList());
        return result;
    }

    private List<PayrollBatchStatus> resolveStatuses(String status) {
        if (!StringUtils.hasText(status)) {
            return new ArrayList<>(DEFAULT_BATCH_STATUSES);
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        // 尝试通过 code 匹配枚举
        PayrollBatchStatus matchedStatus = ALLOWED_BATCH_STATUSES.stream()
                .filter(s -> s.getCode().equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(null);
        if (matchedStatus == null) {
            return new ArrayList<>(DEFAULT_BATCH_STATUSES);
        }
        return List.of(matchedStatus);
    }

    private Map<Long, Long> loadLineCounts(List<Long> batchIds) {
        if (CollectionUtils.isEmpty(batchIds)) {
            return Collections.emptyMap();
        }
        QueryWrapper<PayrollLine> wrapper = new QueryWrapper<>();
        wrapper.select("batch_id", "COUNT(*) AS cnt")
                .in("batch_id", batchIds)
                .eq("employment_type", PT_TYPE)
                .groupBy("batch_id");
        List<Map<String, Object>> maps = payrollLineService.listMaps(wrapper);
        Map<Long, Long> result = new HashMap<>();
        if (maps != null) {
            for (Map<String, Object> map : maps) {
                Object batchIdObj = map.get("batch_id");
                Object countObj = map.get("cnt");
                if (batchIdObj != null && countObj != null) {
                    result.put(Long.parseLong(batchIdObj.toString()), Long.parseLong(countObj.toString()));
                }
            }
        }
        return result;
    }

    private Map<String, PaymentBatch> loadPaymentBatches(List<PayrollBatch> batches) {
        if (CollectionUtils.isEmpty(batches)) {
            return Collections.emptyMap();
        }
        Set<String> batchNos = batches.stream()
                .map(PayrollBatch::getPaymentBatchNo)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (batchNos.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, PaymentBatch> map = new HashMap<>();
        for (String batchNo : batchNos) {
            PaymentBatch paymentBatch = paymentBatchService.getByBatchNo(batchNo);
            if (paymentBatch != null) {
                map.put(batchNo, paymentBatch);
            }
        }
        return map;
    }

    private Map<Long, Employee> loadEmployees(List<PayrollLine> lines) {
        if (CollectionUtils.isEmpty(lines)) {
            return Collections.emptyMap();
        }
        Set<Long> employeeIds = lines.stream()
                .map(PayrollLine::getEmployeeId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        if (employeeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Employee> employees = employeeService.listByIds(employeeIds);
        if (CollectionUtils.isEmpty(employees)) {
            return Collections.emptyMap();
        }
        return employees.stream().collect(Collectors.toMap(Employee::getId, e -> e));
    }

    private Map<String, SalaryItem> loadSalaryItems(List<PayrollLine> lines) {
        if (CollectionUtils.isEmpty(lines)) {
            return Collections.emptyMap();
        }
        Set<String> codes = new HashSet<>();
        for (PayrollLine line : lines) {
            for (PayrollPreviewDto.PayrollPreviewItemDto item : parseItems(line.getItemsSnapshotJson())) {
                if (StringUtils.hasText(item.getCode())) {
                    codes.add(item.getCode().trim());
                }
            }
        }
        if (codes.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SalaryItem> salaryItems = salaryItemService.list(new LambdaQueryWrapper<SalaryItem>()
                .in(SalaryItem::getCode, codes));
        if (CollectionUtils.isEmpty(salaryItems)) {
            return Collections.emptyMap();
        }
        return salaryItems.stream().collect(Collectors.toMap(SalaryItem::getCode, item -> item, (a, b) -> a));
    }

    private OpenApiPayrollBatchDto toBatchDto(PayrollBatch batch,
                                              Map<Long, Long> lineCountMap,
                                              Map<String, PaymentBatch> paymentBatchMap) {
        Long lineCount = lineCountMap.getOrDefault(batch.getId(), 0L);
        PaymentBatch paymentBatch = StringUtils.hasText(batch.getPaymentBatchNo())
                ? paymentBatchMap.get(batch.getPaymentBatchNo())
                : null;
        LocalDateTime paidAt = resolvePaidAt(paymentBatch);

        return OpenApiPayrollBatchDto.builder()
                .id(batch.getId())
                .periodLabel(batch.getPeriodLabel())
                .type(batch.getType())
                .status(batch.getStatus().getCode())
                .currency(batch.getCurrency())
                .lineCount(lineCount)
                .paidAt(paidAt)
                .createdAt(batch.getCreateTime())
                .updatedAt(batch.getUpdateTime())
                .build();
    }

    private LocalDateTime resolvePaidAt(PaymentBatch paymentBatch) {
        if (paymentBatch == null) {
            return null;
        }
        if (paymentBatch.getProcessEndTime() != null) {
            return paymentBatch.getProcessEndTime();
        }
        if (paymentBatch.getApproveTime() != null) {
            return paymentBatch.getApproveTime();
        }
        return paymentBatch.getUpdateTime();
    }

    private OpenApiPayrollLineDto toLineDto(PayrollLine line, Employee employee) {
        return OpenApiPayrollLineDto.builder()
                .id(line.getId())
                .batchId(line.getBatchId())
                .employeeRef(buildEmployeeRef(employee))
                .employmentType(line.getEmploymentType())
                .grossAmount(safe(line.getGrossAmount()))
                .taxAmount(safe(line.getTaxAmount()))
                .socialAmount(safe(line.getSocialAmount()))
                .netAmount(safe(line.getNetAmount()))
                .currency(line.getCurrency())
                .departments(resolveDepartments(employee))
                .employeeNameMasked(maskName(employee != null ? employee.getName() : null))
                .phoneMasked(encryptionService.maskPhone(employee != null ? employee.getPhone() : null))
                .generatedAt(line.getUpdateTime())
                .build();
    }

    private OpenApiPayslipDto toPayslipDto(PayrollLine line,
                                           PayrollBatch batch,
                                           Employee employee,
                                           Map<String, SalaryItem> salaryItemMap) {
        List<OpenApiPayslipDto.PayslipItemDto> items = parseItems(line.getItemsSnapshotJson()).stream()
                .map(item -> toPayslipItemDto(item, salaryItemMap.get(item.getCode())))
                .toList();

        return OpenApiPayslipDto.builder()
                .id(line.getId())
                .employeeRef(buildEmployeeRef(employee))
                .period(batch != null ? batch.getPeriodLabel() : null)
                .employmentType(line.getEmploymentType())
                .grossAmount(safe(line.getGrossAmount()))
                .taxAmount(safe(line.getTaxAmount()))
                .socialAmount(safe(line.getSocialAmount()))
                .netAmount(safe(line.getNetAmount()))
                .currency(line.getCurrency())
                .departments(resolveDepartments(employee))
                .employeeNameMasked(maskName(employee != null ? employee.getName() : null))
                .phoneMasked(encryptionService.maskPhone(employee != null ? employee.getPhone() : null))
                .generatedAt(line.getUpdateTime())
                .items(items)
                .build();
    }

    private OpenApiPayslipDto.PayslipItemDto toPayslipItemDto(PayrollPreviewDto.PayrollPreviewItemDto item,
                                                              SalaryItem salaryItem) {
        Boolean showOnPayslip = salaryItem != null ? salaryItem.getShowOnPayslip() : Boolean.TRUE;
        Integer order = salaryItem != null ? salaryItem.getOrderNum() : null;
        return OpenApiPayslipDto.PayslipItemDto.builder()
                .code(item.getCode())
                .name(item.getName())
                .type(item.getType())
                .taxable(item.getTaxable())
                .amount(item.getAmount())
                .showOnPayslip(showOnPayslip != null ? showOnPayslip : Boolean.TRUE)
                .order(order)
                .build();
    }

    private Long resolveEmployeeId(String employeeRef) {
        if (!StringUtils.hasText(employeeRef)) {
            return null;
        }
        String ref = employeeRef.trim();
        if (ref.contains(":")) {
            String[] parts = ref.split(":", 2);
            String platformType = parts[0];
            String platformUserId = parts.length > 1 ? parts[1] : null;
            if (StringUtils.hasText(platformType) && platformUserId != null && StringUtils.hasText(platformUserId)) {
                Employee employee = employeeService.getByPlatformUserId(platformUserId.trim(), platformType.trim());
                return employee != null ? employee.getId() : null;
            }
        }
        String normalized = ref.startsWith("emp:") ? ref.substring(4) : ref;
        Employee employee = employeeService.getByEmployeeId(normalized.trim());
        return employee != null ? employee.getId() : null;
    }

    private String buildEmployeeRef(Employee employee) {
        if (employee == null) {
            return null;
        }
        if (StringUtils.hasText(employee.getPlatformType()) && StringUtils.hasText(employee.getPlatformUserId())) {
            return employee.getPlatformType().trim() + ":" + employee.getPlatformUserId().trim();
        }
        if (StringUtils.hasText(employee.getEmployeeId())) {
            return "emp:" + employee.getEmployeeId().trim();
        }
        return "emp:" + employee.getId();
    }

    private List<String> resolveDepartments(Employee employee) {
        if (employee == null || !StringUtils.hasText(employee.getDepartment())) {
            return Collections.emptyList();
        }
        String dept = employee.getDepartment().trim();
        if (dept.contains("/")) {
            String[] parts = dept.split("/");
            List<String> list = new ArrayList<>();
            for (String part : parts) {
                if (StringUtils.hasText(part)) {
                    list.add(part.trim());
                }
            }
            return list;
        }
        return List.of(dept);
    }

    private String maskName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.length() <= 1) {
            return "*";
        }
        if (trimmed.length() == 2) {
            return trimmed.charAt(0) + "*";
        }
        return trimmed.charAt(0) + "**";
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private List<PayrollPreviewDto.PayrollPreviewItemDto> parseItems(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("parse items snapshot failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
