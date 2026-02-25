package com.yiyundao.compensation.interfaces.controller.payroll;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollPreviewDto;
import com.yiyundao.compensation.modules.payroll.service.PayrollCalculationService;
import com.yiyundao.compensation.modules.payroll.service.PayrollImportService;
import com.yiyundao.compensation.service.NotificationService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

/**
 * 薪酬导入控制器
 * 支持CSV文件预览和提交，支持财务权限控制和头部验证
 */
@Slf4j
@RestController
@RequestMapping("/payroll/import")
@RequiredArgsConstructor
public class PayrollImportController {

    private final PayrollImportService payrollImportService;
    private final PayrollCalculationService payrollCalculationService;
    private final NotificationService notificationService;

    /**
     * CSV文件头部验证：期望的列名
     */
    private static final List<String> EXPECTED_HEADERS = Arrays.asList(
            "employeeId", "itemCode", "amount", "note"
    );

    /**
     * 预览导入（CSV），不落库
     * 权限：仅ADMIN和FINANCE角色可访问
     * 验证：CSV头部格式
     */
    @PostMapping("/preview")
    @SecurityAnnotations.IsFinanceOrAdmin
    public ApiResponse<String> preview(@RequestParam Long batchId, @RequestParam("file") MultipartFile file) {
        log.info("预览导入CSV: batchId={}, fileName={}", batchId, file.getOriginalFilename());

        // 验证CSV头部
        String headerValidation = validateCsvHeader(file);
        if (headerValidation != null) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, headerValidation);
        }

        return ApiResponse.success(payrollImportService.previewCsv(batchId, file));
    }

    /**
     * 提交导入（CSV），写入暂存表
     * 权限：仅ADMIN和FINANCE角色可访问
     * 完成后：触发计算预览或通知管理员审核
     */
    @PostMapping("/commit")
    @SecurityAnnotations.IsFinanceOrAdmin
    public ApiResponse<ImportCommitResult> commit(@RequestParam Long batchId, @RequestParam("file") MultipartFile file) {
        log.info("提交导入CSV: batchId={}, fileName={}", batchId, file.getOriginalFilename());

        // 验证CSV头部
        String headerValidation = validateCsvHeader(file);
        if (headerValidation != null) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, headerValidation);
        }

        // 执行导入
        String importResult = payrollImportService.commitCsv(batchId, file);

        ImportCommitResult result = new ImportCommitResult();
        result.setImportSummary(importResult);

        // 触发计算预览
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

        // 通知管理员审核（异步）
        try {
            notificationService.sendApprovalNotification(
                    1L, // 默认通知管理员（实际应从配置读取）
                    "PAYROLL_IMPORT", // workflowId
                    "薪酬导入审核", // workflowName
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

    /**
     * 验证CSV文件头部
     * @return null表示验证通过，错误消息表示验证失败
     */
    private String validateCsvHeader(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "文件为空";
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            return "仅支持CSV格式文件";
        }

        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(file.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {

            String header = br.readLine();
            if (header == null || header.trim().isEmpty()) {
                return "CSV文件头部为空";
            }

            // 解析并标准化头部
            List<String> headers = Arrays.stream(header.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .collect(java.util.stream.Collectors.toList());

            // 验证必需的头部列
            List<String> missingHeaders = new java.util.ArrayList<>();
            for (String expected : EXPECTED_HEADERS) {
                if (!headers.contains(expected)) {
                    missingHeaders.add(expected);
                }
            }

            if (!missingHeaders.isEmpty()) {
                return "CSV头部缺少必需的列: " + String.join(", ", missingHeaders) +
                       ". 期望的头部: " + String.join(", ", EXPECTED_HEADERS);
            }

            // 检查是否有未知列（允许额外的列，但会警告）
            List<String> unknownHeaders = new java.util.ArrayList<>();
            for (String headerCol : headers) {
                if (!EXPECTED_HEADERS.contains(headerCol)) {
                    unknownHeaders.add(headerCol);
                }
            }

            if (!unknownHeaders.isEmpty()) {
                log.warn("CSV文件包含未知列: {}", String.join(", ", unknownHeaders));
            }

            return null; // 验证通过

        } catch (Exception e) {
            log.error("CSV头部验证失败", e);
            return "读取CSV文件失败: " + e.getMessage();
        }
    }

    /**
     * 导入提交结果DTO
     */
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
