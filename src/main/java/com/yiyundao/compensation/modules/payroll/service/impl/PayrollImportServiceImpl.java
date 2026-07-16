package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.infrastructure.dao.PayrollImportItemMapper;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollManualImportItemRequest;
import com.yiyundao.compensation.interfaces.vo.payroll.PayrollImportItemVO;
import com.yiyundao.compensation.interfaces.vo.payroll.PayrollImportSalaryItemVO;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollImportItem;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.entity.SalaryItem;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollImportService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.payroll.service.SalaryItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollImportServiceImpl implements PayrollImportService {

    private static final String STATUS_VALID = "valid";
    private static final String SOURCE_MANUAL = "manual_entry";
    private static final Set<PayrollBatchStatus> MUTABLE_BATCH_STATUSES = Set.of(
            PayrollBatchStatus.DRAFT,
            PayrollBatchStatus.LOCKED
    );
    private static final List<String> EXPECTED_HEADERS = List.of(
            "employeeId", "itemCode", "amount", "note"
    );
    // payment_record.amount is decimal(10,2), so keep import validation within its 8-digit integer capacity.
    private static final int AMOUNT_INTEGER_DIGITS = 8;
    private static final int AMOUNT_FRACTION_DIGITS = 2;
    private static final CSVFormat IMPORT_CSV_FORMAT = CSVFormat.RFC4180.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreSurroundingSpaces(true)
            .setTrim(true)
            .build();

    private final PayrollBatchService payrollBatchService;
    private final EmployeeService employeeService;
    private final SalaryItemService salaryItemService;
    private final PayrollLineService payrollLineService;
    private final PayrollImportItemMapper importItemMapper;

    @Override
    public String previewCsv(Long batchId, MultipartFile file) {
        Map<String, Object> summary = parseCsv(batchId, file, false);
        return toJson(summary);
    }

    @Override
    @Transactional
    public String commitCsv(Long batchId, MultipartFile file) {
        Map<String, Object> summary = parseCsv(batchId, file, true);
        Object error = summary.get("error");
        if (error != null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "CSV导入失败: " + error);
        }
        return toJson(summary);
    }

    @Override
    public List<PayrollImportItemVO> listItems(Long batchId) {
        requireBatch(batchId);
        List<PayrollImportItem> items = importItemMapper.selectList(new LambdaQueryWrapper<PayrollImportItem>()
                .eq(PayrollImportItem::getBatchId, batchId)
                .orderByDesc(PayrollImportItem::getCreateTime)
                .orderByDesc(PayrollImportItem::getId));
        return enrichItems(items);
    }

    @Override
    @Transactional
    public PayrollImportItemVO addManualItem(Long batchId, PayrollManualImportItemRequest request) {
        PayrollBatch batch = requireBatch(batchId);
        ensureBatchMutable(batch);

        Employee employee = resolveEmployee(request);
        SalaryItem salaryItem = resolveSalaryItem(request.getItemCode());

        PayrollImportItem entity = new PayrollImportItem();
        entity.setBatchId(batchId);
        entity.setEmployeeId(employee.getId());
        entity.setItemCode(salaryItem.getCode());
        validateAmount(request.getAmount());
        entity.setAmount(request.getAmount());
        entity.setNote(normalizeNullableText(request.getNote()));
        entity.setSourceName(SOURCE_MANUAL);
        entity.setRowNo(request.getRowNo() != null ? request.getRowNo() : nextRowNo(batchId));
        entity.setStatus(STATUS_VALID);
        entity.setErrorMsg(null);
        importItemMapper.insert(entity);

        return enrichItems(List.of(entity)).stream().findFirst().orElseThrow();
    }

    @Override
    @Transactional
    public PayrollImportItemVO updateItem(Long batchId, Long itemId, PayrollManualImportItemRequest request) {
        PayrollBatch batch = requireBatch(batchId);
        ensureBatchMutable(batch);

        PayrollImportItem item = importItemMapper.selectById(itemId);
        if (item == null || !batchId.equals(item.getBatchId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "导入项不存在");
        }

        Employee employee = resolveEmployee(request);
        SalaryItem salaryItem = resolveSalaryItem(request.getItemCode());

        item.setEmployeeId(employee.getId());
        item.setItemCode(salaryItem.getCode());
        validateAmount(request.getAmount());
        item.setAmount(request.getAmount());
        item.setNote(normalizeNullableText(request.getNote()));
        if (request.getRowNo() != null) {
            item.setRowNo(request.getRowNo());
        }
        item.setStatus(STATUS_VALID);
        item.setErrorMsg(null);
        importItemMapper.updateById(item);

        return enrichItems(List.of(item)).stream().findFirst().orElseThrow();
    }

    @Override
    @Transactional
    public boolean deleteItem(Long batchId, Long itemId) {
        PayrollBatch batch = requireBatch(batchId);
        ensureBatchMutable(batch);

        PayrollImportItem item = importItemMapper.selectById(itemId);
        if (item == null || !batchId.equals(item.getBatchId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "导入项不存在");
        }
        return importItemMapper.deleteById(itemId) > 0;
    }

    @Override
    public List<PayrollImportSalaryItemVO> listEnabledSalaryItems() {
        return salaryItemService.list(new LambdaQueryWrapper<SalaryItem>()
                        .eq(SalaryItem::getStatus, "enabled")
                        .orderByAsc(SalaryItem::getOrderNum)
                        .orderByAsc(SalaryItem::getCode))
                .stream()
                .map(this::toSalaryItemVO)
                .toList();
    }

    private Map<String, Object> parseCsv(Long batchId, MultipartFile file, boolean persist) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        int valid = 0, invalid = 0, total = 0;

        PayrollBatch batch = requireBatch(batchId);
        if (persist) {
            ensureBatchMutable(batch);
        }
        String sourceName = file == null ? null : normalizeNullableText(file.getOriginalFilename());
        List<PayrollImportItem> recordsToPersist = new ArrayList<>();

        Map<String, SalaryItem> itemByCode = loadEnabledSalaryItems();
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = IMPORT_CSV_FORMAT.parse(reader)) {
            Map<String, String> headerByLower = normalizeHeaders(parser.getHeaderMap().keySet());
            validateRequiredHeaders(headerByLower);
            for (CSVRecord record : parser) {
                int row = safeRowNumber(record);
                total++;
                String employeeNo = getCsvValue(record, headerByLower, "employeeId");
                String itemCode = getCsvValue(record, headerByLower, "itemCode");
                String amountStr = getCsvValue(record, headerByLower, "amount");
                String note = getCsvValue(record, headerByLower, "note");

                Employee emp = employeeService.getByEmployeeId(employeeNo);
                if (emp == null) {
                    invalid++;
                    errors.add(err(row, "employee not found:" + employeeNo));
                    continue;
                }
                SalaryItem def = itemByCode.get(itemCode);
                if (def == null) {
                    invalid++;
                    errors.add(err(row, "itemCode not found:" + itemCode));
                    continue;
                }
                BigDecimal amount;
                try {
                    amount = new BigDecimal(amountStr);
                    validateAmount(amount);
                } catch (Exception e) {
                    invalid++;
                    errors.add(err(row, "amount invalid:" + amountStr));
                    continue;
                }

                valid++;
                if (persist) {
                    PayrollImportItem rec = new PayrollImportItem();
                    rec.setBatchId(batchId);
                    rec.setEmployeeId(emp.getId());
                    rec.setItemCode(itemCode);
                    rec.setAmount(amount);
                    rec.setNote(note);
                    rec.setSourceName(sourceName);
                    rec.setRowNo(row);
                    rec.setStatus(STATUS_VALID);
                    recordsToPersist.add(rec);
                }
            }
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }

        if (persist && !result.containsKey("error")) {
            if (total == 0) {
                result.put("error", "CSV没有可导入的数据行");
            } else if (invalid > 0) {
                result.put("error", "存在 " + invalid + " 行无效数据，请先使用预览接口查看错误明细");
            }
        }

        if (persist && !result.containsKey("error") && !recordsToPersist.isEmpty()) {
            ensureBatchStillMutableForWrite(batchId);
            replacePreviousFileImport(batchId, sourceName);
            recordsToPersist.forEach(importItemMapper::insert);
        }
        result.put("total", total);
        result.put("valid", valid);
        result.put("invalid", invalid);
        result.put("errors", errors);
        return result;
    }

    private Map<String, String> normalizeHeaders(Set<String> headers) {
        Map<String, String> headerByLower = new HashMap<>();
        for (String header : headers) {
            String normalized = normalizeText(header);
            if (normalized != null) {
                headerByLower.put(normalized.toLowerCase(Locale.ROOT), header);
            }
        }
        return headerByLower;
    }

    private void validateRequiredHeaders(Map<String, String> headerByLower) {
        if (headerByLower == null || headerByLower.isEmpty()) {
            throw new IllegalArgumentException("CSV文件头部为空");
        }
        List<String> missingHeaders = new ArrayList<>();
        for (String expected : EXPECTED_HEADERS) {
            if (!headerByLower.containsKey(expected.toLowerCase(Locale.ROOT))) {
                missingHeaders.add(expected);
            }
        }
        if (!missingHeaders.isEmpty()) {
            throw new IllegalArgumentException("CSV头部缺少必需的列: " + String.join(", ", missingHeaders));
        }
    }

    private String getCsvValue(CSVRecord record, Map<String, String> headerByLower, String header) {
        String originalHeader = headerByLower.get(header.toLowerCase(Locale.ROOT));
        if (originalHeader == null || !record.isMapped(originalHeader) || !record.isSet(originalHeader)) {
            return null;
        }
        return normalizeNullableText(record.get(originalHeader));
    }

    private int safeRowNumber(CSVRecord record) {
        long rowNumber = record.getRecordNumber() + 1;
        return rowNumber > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rowNumber;
    }

    private void replacePreviousFileImport(Long batchId, String sourceName) {
        if (!StringUtils.hasText(sourceName) || SOURCE_MANUAL.equalsIgnoreCase(sourceName)) {
            return;
        }
        importItemMapper.delete(new LambdaQueryWrapper<PayrollImportItem>()
                .eq(PayrollImportItem::getBatchId, batchId)
                .eq(PayrollImportItem::getSourceName, sourceName));
    }

    private Map<String, Object> err(int row, String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("row", row);
        m.put("message", msg);
        return m;
    }

    private String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    private PayrollBatch requireBatch(Long batchId) {
        PayrollBatch batch = payrollBatchService.getById(batchId);
        if (batch == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "批次不存在");
        }
        return batch;
    }

    private void ensureBatchMutable(PayrollBatch batch) {
        if (batch.getStatus() == null || !MUTABLE_BATCH_STATUSES.contains(batch.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "当前批次状态不允许继续录入，请在草稿或已锁定状态下操作");
        }
        if (batch.getStatus() == PayrollBatchStatus.LOCKED && hasComputedLines(batch.getId())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "当前批次已生成工资结果，请先重新打开批次后再修改导入数据");
        }
    }

    private void ensureBatchStillMutableForWrite(Long batchId) {
        PayrollBatch latest = payrollBatchService.getOne(new LambdaQueryWrapper<PayrollBatch>()
                .eq(PayrollBatch::getId, batchId)
                .last("for update"));
        if (latest == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "批次不存在");
        }
        ensureBatchMutable(latest);
    }

    private boolean hasComputedLines(Long batchId) {
        if (batchId == null) {
            return false;
        }
        return payrollLineService.count(new LambdaQueryWrapper<PayrollLine>()
                .eq(PayrollLine::getBatchId, batchId)) > 0;
    }

    private Employee resolveEmployee(PayrollManualImportItemRequest request) {
        Employee employee;
        String employeeNo = normalizeText(request.getEmployeeNo());
        if (request.getEmployeeId() != null) {
            employee = employeeService.getById(request.getEmployeeId());
            if (employee == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "员工不存在: " + request.getEmployeeId());
            }
            if (StringUtils.hasText(employeeNo) && !employeeNo.equalsIgnoreCase(employee.getEmployeeId())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "employeeId 与 employeeNo 不匹配");
            }
            return employee;
        }

        employee = employeeService.getByEmployeeId(employeeNo);
        if (employee == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "员工工号不存在: " + employeeNo);
        }
        return employee;
    }

    private SalaryItem resolveSalaryItem(String itemCode) {
        String normalizedItemCode = normalizeText(itemCode);
        SalaryItem salaryItem = salaryItemService.getOne(new LambdaQueryWrapper<SalaryItem>()
                .eq(SalaryItem::getCode, normalizedItemCode)
                .eq(SalaryItem::getStatus, "enabled")
                .last("limit 1"));
        if (salaryItem == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "薪资项不存在或未启用: " + normalizedItemCode);
        }
        return salaryItem;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("金额必须大于0");
        }
        BigDecimal normalized = amount.stripTrailingZeros();
        int scale = Math.max(normalized.scale(), 0);
        int integerDigits = normalized.precision() - scale;
        if (integerDigits > AMOUNT_INTEGER_DIGITS || scale > AMOUNT_FRACTION_DIGITS) {
            throw new IllegalArgumentException("金额精度超出限制");
        }
    }

    private Map<String, SalaryItem> loadEnabledSalaryItems() {
        Map<String, SalaryItem> itemByCode = new HashMap<>();
        for (SalaryItem it : salaryItemService.list(new LambdaQueryWrapper<SalaryItem>().eq(SalaryItem::getStatus, "enabled"))) {
            itemByCode.put(it.getCode(), it);
        }
        return itemByCode;
    }

    private int nextRowNo(Long batchId) {
        PayrollImportItem latest = importItemMapper.selectOne(new LambdaQueryWrapper<PayrollImportItem>()
                .eq(PayrollImportItem::getBatchId, batchId)
                .orderByDesc(PayrollImportItem::getRowNo)
                .orderByDesc(PayrollImportItem::getId)
                .last("limit 1"));
        if (latest == null || latest.getRowNo() == null) {
            return 1;
        }
        return latest.getRowNo() + 1;
    }

    private List<PayrollImportItemVO> enrichItems(List<PayrollImportItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Employee> employeeMap = employeeService.listByIds(items.stream()
                        .map(PayrollImportItem::getEmployeeId)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Employee::getId, employee -> employee));
        Map<String, SalaryItem> salaryItemMap = salaryItemService.list(new LambdaQueryWrapper<SalaryItem>()
                        .in(SalaryItem::getCode, items.stream()
                                .map(PayrollImportItem::getItemCode)
                                .filter(StringUtils::hasText)
                                .collect(Collectors.toSet())))
                .stream()
                .collect(Collectors.toMap(SalaryItem::getCode, salaryItem -> salaryItem));

        return items.stream()
                .map(item -> toImportItemVO(item, employeeMap, salaryItemMap))
                .sorted(Comparator.comparing(PayrollImportItemVO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PayrollImportItemVO::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private PayrollImportItemVO toImportItemVO(PayrollImportItem item,
                                               Map<Long, Employee> employeeMap,
                                               Map<String, SalaryItem> salaryItemMap) {
        PayrollImportItemVO vo = new PayrollImportItemVO();
        vo.setId(item.getId());
        vo.setBatchId(item.getBatchId());
        vo.setEmployeeId(item.getEmployeeId());
        Employee employee = employeeMap.get(item.getEmployeeId());
        if (employee != null) {
            vo.setEmployeeNo(employee.getEmployeeId());
            vo.setEmployeeName(employee.getName());
        }
        vo.setItemCode(item.getItemCode());
        SalaryItem salaryItem = salaryItemMap.get(item.getItemCode());
        if (salaryItem != null) {
            vo.setItemName(salaryItem.getName());
            vo.setItemType(salaryItem.getType());
        }
        vo.setAmount(item.getAmount());
        vo.setNote(item.getNote());
        vo.setSourceName(item.getSourceName());
        vo.setRowNo(item.getRowNo());
        vo.setStatus(item.getStatus());
        vo.setErrorMsg(item.getErrorMsg());
        vo.setManual(SOURCE_MANUAL.equalsIgnoreCase(item.getSourceName()));
        vo.setCreateTime(item.getCreateTime());
        vo.setUpdateTime(item.getUpdateTime());
        return vo;
    }

    private PayrollImportSalaryItemVO toSalaryItemVO(SalaryItem item) {
        PayrollImportSalaryItemVO vo = new PayrollImportSalaryItemVO();
        vo.setCode(item.getCode());
        vo.setName(item.getName());
        vo.setType(item.getType());
        vo.setTaxable(item.getTaxable());
        vo.setShowOnPayslip(item.getShowOnPayslip());
        vo.setOrderNum(item.getOrderNum());
        return vo;
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeNullableText(String value) {
        String text = normalizeText(value);
        return StringUtils.hasText(text) ? text : null;
    }
}
