package com.yiyundao.compensation.interfaces.controller.payroll;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollManualImportItemRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollPreviewDto;
import com.yiyundao.compensation.interfaces.vo.payroll.PayrollImportItemVO;
import com.yiyundao.compensation.interfaces.vo.payroll.PayrollImportSalaryItemVO;
import com.yiyundao.compensation.modules.payroll.service.PayrollCalculationService;
import com.yiyundao.compensation.modules.payroll.service.PayrollImportService;
import com.yiyundao.compensation.modules.system.service.SysConfigService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import com.yiyundao.compensation.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 薪酬导入控制器
 * 支持CSV文件预览、提交，以及批次工作台中的手动录入管理
 */
@Slf4j
@RestController
@RequestMapping("/payroll/import")
@RequiredArgsConstructor
public class PayrollImportController {

    private final PayrollImportService payrollImportService;
    private final PayrollCalculationService payrollCalculationService;
    private final NotificationService notificationService;
    private final SysConfigService sysConfigService;

    private static final List<String> EXPECTED_HEADERS = List.of(
            "employeeId", "itemCode", "amount", "note"
    );
    private static final CSVFormat IMPORT_CSV_FORMAT = CSVFormat.RFC4180.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreSurroundingSpaces(true)
            .setTrim(true)
            .build();

    @PostMapping("/preview")
    @SecurityAnnotations.IsFinanceOrAdmin
    public ApiResponse<String> preview(@RequestParam Long batchId, @RequestParam("file") MultipartFile file) {
        log.info("预览导入CSV: batchId={}, fileName={}", batchId, file.getOriginalFilename());

        String headerValidation = validateCsvHeader(file);
        if (headerValidation != null) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, headerValidation);
        }

        return ApiResponse.success(payrollImportService.previewCsv(batchId, file));
    }

    @PostMapping("/commit")
    @SecurityAnnotations.IsFinanceOrAdmin
    public ApiResponse<ImportCommitResult> commit(@RequestParam Long batchId, @RequestParam("file") MultipartFile file) {
        log.info("提交导入CSV: batchId={}, fileName={}", batchId, file.getOriginalFilename());

        String headerValidation = validateCsvHeader(file);
        if (headerValidation != null) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, headerValidation);
        }

        String importResult = payrollImportService.commitCsv(batchId, file);

        ImportCommitResult result = new ImportCommitResult();
        result.setImportSummary(importResult);

        try {
            PayrollPreviewDto preview = payrollCalculationService.dryRunPreview(batchId);
            if (preview != null) {
                result.setPreviewGenerated(true);
                result.setTotalEmployees(preview.getTotalEmployees());
                result.setEarningsTotal(preview.getEarningsTotal());
                result.setNetTotal(preview.getNetTotal());
                result.setWarningsCount(preview.getTotalWarnings());
                log.info("计算预览生成成功: batchId={}, employees={}", batchId, preview.getTotalEmployees());
            }
        } catch (Exception e) {
            log.warn("计算预览生成失败: batchId={}, error={}", batchId, e.getMessage());
            result.setPreviewGenerated(false);
            result.setPreviewError(e.getMessage());
        }

        try {
            Long adminUserId = sysConfigService.getLong("system.admin_user_id", 1L);
            notificationService.sendApprovalNotification(
                    adminUserId,
                    "PAYROLL_IMPORT",
                    "薪酬导入审核",
                    com.yiyundao.compensation.enums.NotificationType.APPROVAL_PENDING,
                    "薪酬数据已导入，待审核。批次ID: " + batchId + ", 员工数: " + (result.getTotalEmployees() != null ? result.getTotalEmployees() : 0)
            );
            result.setNotificationSent(true);
        } catch (Exception e) {
            log.warn("管理员通知发送失败: batchId={}, error={}", batchId, e.getMessage());
            result.setNotificationSent(false);
        }

        return ApiResponse.success(result);
    }

    @GetMapping("/batches/{batchId}/items")
    @SecurityAnnotations.IsFinanceOrAdmin
    public ApiResponse<List<PayrollImportItemVO>> listItems(@PathVariable Long batchId) {
        return ApiResponse.success(payrollImportService.listItems(batchId));
    }

    @PostMapping("/batches/{batchId}/items/manual")
    @SecurityAnnotations.IsFinanceOrAdmin
    public ApiResponse<PayrollImportItemVO> addManualItem(@PathVariable Long batchId,
                                                          @Valid @RequestBody PayrollManualImportItemRequest request) {
        return ApiResponse.success(payrollImportService.addManualItem(batchId, request));
    }

    @PutMapping("/batches/{batchId}/items/{itemId}")
    @SecurityAnnotations.IsFinanceOrAdmin
    public ApiResponse<PayrollImportItemVO> updateItem(@PathVariable Long batchId,
                                                       @PathVariable Long itemId,
                                                       @Valid @RequestBody PayrollManualImportItemRequest request) {
        return ApiResponse.success(payrollImportService.updateItem(batchId, itemId, request));
    }

    @DeleteMapping("/batches/{batchId}/items/{itemId}")
    @SecurityAnnotations.IsFinanceOrAdmin
    public ApiResponse<Boolean> deleteItem(@PathVariable Long batchId, @PathVariable Long itemId) {
        return ApiResponse.success(payrollImportService.deleteItem(batchId, itemId));
    }

    @GetMapping("/salary-items")
    @SecurityAnnotations.IsFinanceOrAdmin
    public ApiResponse<List<PayrollImportSalaryItemVO>> salaryItems() {
        return ApiResponse.success(payrollImportService.listEnabledSalaryItems());
    }

    private String validateCsvHeader(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "文件为空";
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            return "仅支持CSV格式文件";
        }

        try (java.io.Reader reader = new java.io.InputStreamReader(
                file.getInputStream(), java.nio.charset.StandardCharsets.UTF_8);
             CSVParser parser = IMPORT_CSV_FORMAT.parse(reader)) {

            Map<String, String> headerByLower = parser.getHeaderMap().keySet().stream()
                    .filter(header -> header != null && !header.trim().isEmpty())
                    .collect(Collectors.toMap(
                            header -> header.trim().toLowerCase(Locale.ROOT),
                            String::trim,
                            (left, right) -> left
                    ));
            if (headerByLower.isEmpty()) {
                return "CSV文件头部为空";
            }

            List<String> missingHeaders = new java.util.ArrayList<>();
            for (String expected : EXPECTED_HEADERS) {
                if (!headerByLower.containsKey(expected.toLowerCase(Locale.ROOT))) {
                    missingHeaders.add(expected);
                }
            }

            if (!missingHeaders.isEmpty()) {
                return "CSV头部缺少必需的列: " + String.join(", ", missingHeaders) +
                        ". 期望的头部: " + String.join(", ", EXPECTED_HEADERS);
            }

            List<String> unknownHeaders = new java.util.ArrayList<>();
            List<String> expectedLowerHeaders = EXPECTED_HEADERS.stream()
                    .map(header -> header.toLowerCase(Locale.ROOT))
                    .toList();
            for (String headerCol : headerByLower.keySet()) {
                if (!expectedLowerHeaders.contains(headerCol)) {
                    unknownHeaders.add(headerCol);
                }
            }

            if (!unknownHeaders.isEmpty()) {
                log.warn("CSV文件包含未知列: {}", String.join(", ", unknownHeaders));
            }

            return null;

        } catch (Exception e) {
            log.error("CSV头部验证失败", e);
            return "读取CSV文件失败: " + e.getMessage();
        }
    }

    @lombok.Data
    public static class ImportCommitResult {
        private String importSummary;
        private boolean previewGenerated;
        private Integer totalEmployees;
        private java.math.BigDecimal earningsTotal;
        private java.math.BigDecimal netTotal;
        private Integer warningsCount;
        private String previewError;
        private boolean notificationSent;
    }
}
