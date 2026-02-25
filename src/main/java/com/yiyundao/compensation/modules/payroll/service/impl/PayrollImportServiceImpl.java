package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollImportItemMapper;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollImportItem;
import com.yiyundao.compensation.modules.payroll.entity.SalaryItem;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollImportService;
import com.yiyundao.compensation.modules.payroll.service.SalaryItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollImportServiceImpl implements PayrollImportService {

    private final PayrollBatchService payrollBatchService;
    private final EmployeeService employeeService;
    private final SalaryItemService salaryItemService;
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
        return toJson(summary);
    }

    private Map<String, Object> parseCsv(Long batchId, MultipartFile file, boolean persist) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        int valid = 0, invalid = 0, total = 0;

        PayrollBatch batch = payrollBatchService.getById(batchId);
        if (batch == null) {
            result.put("error", "batch not found");
            return result;
        }
        // load salary items map
        Map<String, SalaryItem> itemByCode = new HashMap<>();
        for (SalaryItem it : salaryItemService.list(new LambdaQueryWrapper<SalaryItem>().eq(SalaryItem::getStatus, "enabled"))) {
            itemByCode.put(it.getCode(), it);
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null) throw new IllegalArgumentException("empty file");
            // expected: employeeId,itemCode,amount,note
            int row = 1; String line;
            while ((line = br.readLine()) != null) {
                row++; total++;
                String[] parts = line.split(",");
                if (parts.length < 3) {
                    invalid++; errors.add(err(row, "col < 3")); continue;
                }
                String empIdStr = parts[0].trim();
                String itemCode = parts[1].trim();
                String amountStr = parts[2].trim();
                String note = parts.length >= 4 ? parts[3].trim() : null;

                Employee emp = employeeService.getByEmployeeId(empIdStr);
                if (emp == null) { invalid++; errors.add(err(row, "employee not found:"+empIdStr)); continue; }
                SalaryItem def = itemByCode.get(itemCode);
                if (def == null) { invalid++; errors.add(err(row, "itemCode not found:"+itemCode)); continue; }
                BigDecimal amount;
                try { amount = new BigDecimal(amountStr); } catch (Exception e) { invalid++; errors.add(err(row, "amount invalid:"+amountStr)); continue; }

                valid++;
                if (persist) {
                    PayrollImportItem rec = new PayrollImportItem();
                    rec.setBatchId(batchId);
                    rec.setEmployeeId(emp.getId());
                    rec.setItemCode(itemCode);
                    rec.setAmount(amount);
                    rec.setNote(note);
                    rec.setSourceName(file.getOriginalFilename());
                    rec.setRowNo(row);
                    rec.setStatus("valid");
                    importItemMapper.insert(rec);
                }
            }
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        result.put("total", total);
        result.put("valid", valid);
        result.put("invalid", invalid);
        result.put("errors", errors);
        return result;
    }

    private Map<String, Object> err(int row, String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("row", row); m.put("message", msg); return m;
    }

    private String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) { return null; }
    }
}

