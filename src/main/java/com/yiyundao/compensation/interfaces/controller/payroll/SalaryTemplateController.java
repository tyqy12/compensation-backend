package com.yiyundao.compensation.interfaces.controller.payroll;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.enums.PayrollType;
import com.yiyundao.compensation.interfaces.dto.payroll.SalaryTemplateResponseDto;
import com.yiyundao.compensation.interfaces.dto.payroll.SalaryTemplateUpsertRequest;
import com.yiyundao.compensation.modules.payroll.entity.SalaryTemplate;
import com.yiyundao.compensation.modules.payroll.entity.SalaryTemplateVersion;
import com.yiyundao.compensation.modules.payroll.service.SalaryTemplateService;
import com.yiyundao.compensation.modules.payroll.service.SalaryTemplateVersionService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/payroll/templates")
@SecurityAnnotations.IsFinanceOrAdmin
@RequiredArgsConstructor
public class SalaryTemplateController {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;
    private static final String STATUS_ENABLED = "enabled";
    private static final String STATUS_DISABLED = "disabled";

    private final SalaryTemplateService salaryTemplateService;
    private final SalaryTemplateVersionService salaryTemplateVersionService;
    private final ObjectMapper objectMapper;

    /**
     * JSON Schema验证itemsJson
     */
    private List<String> validateItemsJson(String itemsJson) {
        List<String> errors = new ArrayList<>();
        if (itemsJson == null || itemsJson.isBlank()) {
            errors.add("itemsJson不能为空");
            return errors;
        }

        try {
            JsonNode root = objectMapper.readTree(itemsJson);
            if (!root.isArray()) {
                errors.add("itemsJson必须是一个数组");
                return errors;
            }

            Set<String> itemCodes = new HashSet<>();
            for (int i = 0; i < root.size(); i++) {
                JsonNode item = root.get(i);
                if (!item.has("code") || item.get("code").asText().isBlank()) {
                    errors.add("itemsJson[" + i + "]: 缺少code字段或code为空");
                } else {
                    String code = item.get("code").asText().trim();
                    if (!itemCodes.add(code)) {
                        errors.add("itemsJson[" + i + "]: code重复: " + code);
                    }
                }
                if (!item.has("name") || item.get("name").asText().isBlank()) {
                    errors.add("itemsJson[" + i + "]: 缺少name字段或name为空");
                }
                if (!item.has("type") || item.get("type").asText().isBlank()) {
                    errors.add("itemsJson[" + i + "]: 缺少type字段或type为空");
                } else {
                    String type = item.get("type").asText();
                    if (!"earning".equalsIgnoreCase(type) && !"deduction".equalsIgnoreCase(type)) {
                        errors.add("itemsJson[" + i + "]: type必须为'earning'或'deduction'");
                    }
                }
                if (item.has("required") && !item.get("required").isBoolean()) {
                    errors.add("itemsJson[" + i + "]: required必须是布尔类型");
                }
                if (item.has("min") && !item.get("min").isNumber()) {
                    errors.add("itemsJson[" + i + "]: min必须是数字类型");
                }
                if (item.has("max") && !item.get("max").isNumber()) {
                    errors.add("itemsJson[" + i + "]: max必须是数字类型");
                }
                validateItemAmountBounds(item, i, errors);
            }
        } catch (Exception e) {
            errors.add("itemsJson解析失败: " + e.getMessage());
        }

        return errors;
    }

    /**
     * JSON Schema验证taxRuleJson
     */
    private List<String> validateTaxRuleJson(String taxRuleJson) {
        List<String> errors = new ArrayList<>();
        if (taxRuleJson == null || taxRuleJson.isBlank()) {
            errors.add("taxRuleJson必须声明tax.mode=cumulative_withholding，旧版固定税率已下线");
            return errors;
        }

        try {
            JsonNode root = objectMapper.readTree(taxRuleJson);
            if (!root.isObject()) {
                errors.add("taxRuleJson必须是一个对象");
                return errors;
            }

            // 验证tax部分
            if (root.has("tax")) {
                JsonNode tax = root.get("tax");
                if (!tax.has("mode") || !"cumulative_withholding".equalsIgnoreCase(tax.path("mode").asText())) {
                    errors.add("taxRuleJson.tax.mode必须为cumulative_withholding，旧版固定税率已下线");
                }
                if (tax.has("rate") && !tax.get("rate").isNumber()) {
                    errors.add("taxRuleJson.tax.rate必须是数字类型");
                } else if (tax.has("rate")) {
                    errors.add("taxRuleJson.tax.rate已下线，请使用政策版本和累计预扣税率表");
                }
                if (tax.has("applyOn")) {
                    String applyOn = tax.get("applyOn").asText();
                    if (!List.of("TAXABLE_EARNINGS", "GROSS", "EARNINGS_MINUS_DEDUCTIONS", "TAXABLE_EARNINGS_MINUS_DEDUCTIONS")
                            .contains(applyOn.toUpperCase())) {
                        errors.add("taxRuleJson.tax.applyOn必须是: TAXABLE_EARNINGS, GROSS, EARNINGS_MINUS_DEDUCTIONS, TAXABLE_EARNINGS_MINUS_DEDUCTIONS");
                    }
                }
            }
            if (!root.has("tax")) {
                errors.add("taxRuleJson必须声明tax.mode=cumulative_withholding");
            }

            // 验证social部分
            if (root.has("social")) {
                JsonNode social = root.get("social");
                if (social.has("rate") && !social.get("rate").isNumber()) {
                    errors.add("taxRuleJson.social.rate必须是数字类型");
                } else if (social.has("rate")) {
                    validateRate(social.get("rate"), "taxRuleJson.social.rate", errors);
                }
                if (social.has("applyOn")) {
                    String applyOn = social.get("applyOn").asText();
                    if (!List.of("TAXABLE_EARNINGS", "GROSS", "EARNINGS_MINUS_DEDUCTIONS", "TAXABLE_EARNINGS_MINUS_DEDUCTIONS")
                            .contains(applyOn.toUpperCase())) {
                        errors.add("taxRuleJson.social.applyOn必须是: TAXABLE_EARNINGS, GROSS, EARNINGS_MINUS_DEDUCTIONS, TAXABLE_EARNINGS_MINUS_DEDUCTIONS");
                    }
                }
            }

            // 验证rounding部分
            if (root.has("rounding")) {
                JsonNode rounding = root.get("rounding");
                if (rounding.has("scale") && !rounding.get("scale").isInt()) {
                    errors.add("taxRuleJson.rounding.scale必须是整数类型");
                }
                if (rounding.has("mode")) {
                    String mode = rounding.get("mode").asText();
                    if (!List.of("CEILING", "DOWN", "FLOOR", "HALF_UP", "HALF_DOWN", "HALF_EVEN", "UP")
                            .contains(mode.toUpperCase())) {
                        errors.add("taxRuleJson.rounding.mode必须是有效的RoundingMode");
                    }
                }
            }

        } catch (Exception e) {
            errors.add("taxRuleJson解析失败: " + e.getMessage());
        }

        return errors;
    }

    @PostMapping
    public ApiResponse<SalaryTemplateResponseDto> create(@Valid @RequestBody SalaryTemplateUpsertRequest req) {
        // JSON语法验证
        List<String> itemsErrors = validateItemsJson(req.getItemsJson());
        if (!itemsErrors.isEmpty()) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, "itemsJson验证失败: " + String.join("; ", itemsErrors));
        }

        List<String> taxErrors = validateTaxRuleJson(req.getTaxRuleJson());
        if (!taxErrors.isEmpty()) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, "taxRuleJson验证失败: " + String.join("; ", taxErrors));
        }
        String normalizedType = normalizeTemplateType(req.getType());
        String normalizedStatus = normalizeTemplateStatus(req.getStatus(), STATUS_ENABLED);
        if (STATUS_ENABLED.equals(normalizedStatus) && hasAnotherEnabledTemplate(normalizedType, null)) {
            return ApiResponse.error(ErrorCode.REQUEST_CONFLICT,
                    "同一用工类型只能有一个启用中的规则包，请先停用现有规则包");
        }

        SalaryTemplate t = new SalaryTemplate();
        t.setName(req.getName());
        t.setType(normalizedType);
        t.setItemsJson(req.getItemsJson());
        t.setTaxRuleJson(req.getTaxRuleJson());
        t.setStatus(normalizedStatus);
        t.setDataVersion(1L); // 初始版本号

        try {
            salaryTemplateService.save(t);
            saveVersionSnapshot(t);
            log.info("创建薪资模板成功: id={}, name={}, dataVersion={}", t.getId(), t.getName(), t.getDataVersion());
        } catch (Exception e) {
            log.error("创建薪资模板失败: name={}", req.getName(), e);
            return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "创建薪资模板失败");
        }

        return ApiResponse.success(SalaryTemplateResponseDto.from(t));
    }

    @PutMapping("/{id}")
    public ApiResponse<SalaryTemplateResponseDto> update(@PathVariable Long id, @Valid @RequestBody SalaryTemplateUpsertRequest req) {
        SalaryTemplate t = salaryTemplateService.getById(id);
        if (t == null) return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "template not found");

        // JSON语法验证
        if (req.getItemsJson() != null) {
            List<String> itemsErrors = validateItemsJson(req.getItemsJson());
            if (!itemsErrors.isEmpty()) {
                return ApiResponse.error(ErrorCode.PARAM_INVALID, "itemsJson验证失败: " + String.join("; ", itemsErrors));
            }
        }

        if (req.getTaxRuleJson() != null) {
            List<String> taxErrors = validateTaxRuleJson(req.getTaxRuleJson());
            if (!taxErrors.isEmpty()) {
                return ApiResponse.error(ErrorCode.PARAM_INVALID, "taxRuleJson验证失败: " + String.join("; ", taxErrors));
            }
        }
        String normalizedType = req.getType() != null ? normalizeTemplateType(req.getType()) : null;
        String normalizedStatus = req.getStatus() != null ? normalizeTemplateStatus(req.getStatus(), null) : null;
        String effectiveType = normalizedType != null ? normalizedType : t.getType();
        if (STATUS_ENABLED.equals(normalizedStatus) && req.getTaxRuleJson() == null) {
            List<String> taxErrors = validateTaxRuleJson(t.getTaxRuleJson());
            if (!taxErrors.isEmpty()) {
                return ApiResponse.error(ErrorCode.PARAM_INVALID, "taxRuleJson验证失败: " + String.join("; ", taxErrors));
            }
        }
        if (STATUS_ENABLED.equals(normalizedStatus) && hasAnotherEnabledTemplate(effectiveType, id)) {
            return ApiResponse.error(ErrorCode.REQUEST_CONFLICT,
                    "同一用工类型只能有一个启用中的规则包，请先停用现有规则包");
        }

        // 版本控制：只有当内容真正变化时才递增版本
        boolean contentChanged = false;
        if (req.getItemsJson() != null && !req.getItemsJson().equals(t.getItemsJson())) {
            contentChanged = true;
        }
        if (req.getTaxRuleJson() != null && !req.getTaxRuleJson().equals(t.getTaxRuleJson())) {
            contentChanged = true;
        }
        if (req.getName() != null && !req.getName().equals(t.getName())) {
            contentChanged = true;
        }

        if (contentChanged) {
            Long newVersion = (t.getDataVersion() == null ? 0L : t.getDataVersion()) + 1;
            t.setDataVersion(newVersion);
            log.info("薪资模板版本递增: id={}, oldVersion={}, newVersion={}", id, t.getDataVersion() - 1, newVersion);
        }

        t.setName(req.getName() != null ? req.getName() : t.getName());
        t.setType(normalizedType != null ? normalizedType : t.getType());
        t.setItemsJson(req.getItemsJson() != null ? req.getItemsJson() : t.getItemsJson());
        t.setTaxRuleJson(req.getTaxRuleJson() != null ? req.getTaxRuleJson() : t.getTaxRuleJson());
        if (normalizedStatus != null) t.setStatus(normalizedStatus);

        try {
            salaryTemplateService.updateById(t);
            if (contentChanged) {
                saveVersionSnapshot(t);
            }
            log.info("更新薪资模板成功: id={}, dataVersion={}", id, t.getDataVersion());
        } catch (Exception e) {
            log.error("更新薪资模板失败: id={}", id, e);
            return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "更新薪资模板失败");
        }

        return ApiResponse.success(SalaryTemplateResponseDto.from(t));
    }

    /**
     * 获取模板版本信息
     */
    @GetMapping("/{id}/version")
    public ApiResponse<SalaryTemplateVersionInfo> getVersionInfo(@PathVariable Long id) {
        SalaryTemplate t = salaryTemplateService.getById(id);
        if (t == null) return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "template not found");

        SalaryTemplateVersionInfo info = new SalaryTemplateVersionInfo();
        info.setId(t.getId());
        info.setName(t.getName());
        info.setType(t.getType());
        info.setDataVersion(t.getDataVersion());
        info.setStatus(t.getStatus());
        info.setCreateTime(t.getCreateTime());
        info.setUpdateTime(t.getUpdateTime());

        return ApiResponse.success(info);
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<SalaryTemplateResponseDto> publish(@PathVariable Long id) {
        SalaryTemplate template = salaryTemplateService.getById(id);
        if (template == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "template not found");
        }
        List<String> itemsErrors = validateItemsJson(template.getItemsJson());
        if (!itemsErrors.isEmpty()) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID,
                    "itemsJson验证失败: " + String.join("; ", itemsErrors));
        }
        List<String> taxErrors = validateTaxRuleJson(template.getTaxRuleJson());
        if (!taxErrors.isEmpty()) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID,
                    "taxRuleJson验证失败: " + String.join("; ", taxErrors));
        }
        if (STATUS_ENABLED.equals(template.getStatus())) {
            return ApiResponse.success(SalaryTemplateResponseDto.from(template));
        }
        if (hasAnotherEnabledTemplate(template.getType(), id)) {
            return ApiResponse.error(ErrorCode.REQUEST_CONFLICT,
                    "同一用工类型只能有一个启用中的规则包，请先停用现有规则包");
        }
        template.setStatus(STATUS_ENABLED);
        try {
            salaryTemplateService.updateById(template);
            saveVersionSnapshot(template);
            log.info("发布薪资规则包成功: id={}, type={}, dataVersion={}",
                    template.getId(), template.getType(), template.getDataVersion());
        } catch (Exception e) {
            log.error("发布薪资规则包失败: id={}", id, e);
            return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "发布薪资规则包失败");
        }
        return ApiResponse.success(SalaryTemplateResponseDto.from(template));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<SalaryTemplateResponseDto> disable(@PathVariable Long id) {
        SalaryTemplate template = salaryTemplateService.getById(id);
        if (template == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "template not found");
        }
        template.setStatus(STATUS_DISABLED);
        try {
            salaryTemplateService.updateById(template);
            saveVersionSnapshot(template);
            log.info("停用薪资规则包成功: id={}, type={}, dataVersion={}",
                    template.getId(), template.getType(), template.getDataVersion());
        } catch (Exception e) {
            log.error("停用薪资规则包失败: id={}", id, e);
            return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "停用薪资规则包失败");
        }
        return ApiResponse.success(SalaryTemplateResponseDto.from(template));
    }

    @GetMapping
    public ApiResponse<Page<SalaryTemplateResponseDto>> list(@RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "10") int size,
                                                             @RequestParam(required = false) String type,
                                                             @RequestParam(required = false) String status) {
        Page<SalaryTemplate> p = new Page<>(safePage(page), safeSize(size));
        LambdaQueryWrapper<SalaryTemplate> qw = new LambdaQueryWrapper<>();
        if (type != null && !type.isBlank()) qw.eq(SalaryTemplate::getType, normalizeTemplateTypeFilter(type));
        if (status != null && !status.isBlank()) qw.eq(SalaryTemplate::getStatus, normalizeTemplateStatusFilter(status));
        qw.orderByDesc(SalaryTemplate::getDataVersion)
                .orderByDesc(SalaryTemplate::getUpdateTime);
        return ApiResponse.success(toResponsePage(salaryTemplateService.page(p, qw)));
    }

    @GetMapping("/{id}")
    public ApiResponse<SalaryTemplateResponseDto> get(@PathVariable Long id) {
        SalaryTemplate template = salaryTemplateService.getById(id);
        if (template == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "template not found");
        }
        return ApiResponse.success(SalaryTemplateResponseDto.from(template));
    }

    private static Page<SalaryTemplateResponseDto> toResponsePage(Page<SalaryTemplate> source) {
        Page<SalaryTemplateResponseDto> target = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        target.setPages(source.getPages());
        target.setRecords(source.getRecords().stream()
            .map(SalaryTemplateResponseDto::from)
            .toList());
        return target;
    }

    private int safePage(int page) {
        return page < 1 ? 1 : page;
    }

    private int safeSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String normalizeTemplateTypeFilter(String type) {
        return normalizeTemplateType(type);
    }

    private String normalizeTemplateType(String type) {
        PayrollType payrollType = PayrollType.fromCode(type.trim());
        if (payrollType == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "无效的模板类型: " + type);
        }
        return payrollType.getCode();
    }

    private String normalizeTemplateStatusFilter(String status) {
        return normalizeTemplateStatus(status, null);
    }

    private String normalizeTemplateStatus(String status, String defaultStatus) {
        if (status == null) {
            return defaultStatus;
        }
        String normalizedStatus = status.trim().toLowerCase();
        if (!STATUS_ENABLED.equals(normalizedStatus) && !STATUS_DISABLED.equals(normalizedStatus)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "无效的模板状态: " + status);
        }
        return normalizedStatus;
    }

    private boolean hasAnotherEnabledTemplate(String type, Long excludeId) {
        LambdaQueryWrapper<SalaryTemplate> query = new LambdaQueryWrapper<SalaryTemplate>()
                .eq(SalaryTemplate::getType, type)
                .eq(SalaryTemplate::getStatus, STATUS_ENABLED);
        if (excludeId != null) {
            query.ne(SalaryTemplate::getId, excludeId);
        }
        return salaryTemplateService.count(query) > 0;
    }

    private void saveVersionSnapshot(SalaryTemplate template) {
        if (template == null || template.getId() == null) {
            return;
        }
        long versionNo = template.getDataVersion() == null ? 1L : template.getDataVersion();
        LambdaQueryWrapper<SalaryTemplateVersion> existingQuery = new LambdaQueryWrapper<SalaryTemplateVersion>()
                .eq(SalaryTemplateVersion::getTemplateId, template.getId())
                .eq(SalaryTemplateVersion::getVersionNo, versionNo)
                .eq(SalaryTemplateVersion::getDeleted, 0);
        if (salaryTemplateVersionService.count(existingQuery) > 0) {
            return;
        }

        SalaryTemplateVersion snapshot = new SalaryTemplateVersion();
        snapshot.setTemplateId(template.getId());
        snapshot.setVersionNo(versionNo);
        snapshot.setName(template.getName());
        snapshot.setType(template.getType());
        snapshot.setItemsJson(template.getItemsJson());
        snapshot.setTaxRuleJson(template.getTaxRuleJson());
        snapshot.setStatus(template.getStatus());
        if (!salaryTemplateVersionService.save(snapshot)) {
            throw new IllegalStateException("薪资规则包版本快照写入失败");
        }
    }

    private void validateItemAmountBounds(JsonNode item, int index, List<String> errors) {
        BigDecimal min = item.has("min") && item.get("min").isNumber() ? item.get("min").decimalValue() : null;
        BigDecimal max = item.has("max") && item.get("max").isNumber() ? item.get("max").decimalValue() : null;
        if (min != null && min.compareTo(BigDecimal.ZERO) < 0) {
            errors.add("itemsJson[" + index + "]: min不能小于0");
        }
        if (max != null && max.compareTo(BigDecimal.ZERO) < 0) {
            errors.add("itemsJson[" + index + "]: max不能小于0");
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            errors.add("itemsJson[" + index + "]: min不能大于max");
        }
    }

    private void validateRate(JsonNode rateNode, String fieldName, List<String> errors) {
        BigDecimal rate = rateNode.decimalValue();
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            errors.add(fieldName + "必须在0到1之间");
        }
    }

    /**
     * 薪资模板版本信息DTO
     */
    public static class SalaryTemplateVersionInfo {
        private Long id;
        private String name;
        private String type;
        private Long dataVersion;
        private String status;
        private java.time.LocalDateTime createTime;
        private java.time.LocalDateTime updateTime;

        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Long getDataVersion() { return dataVersion; }
        public void setDataVersion(Long dataVersion) { this.dataVersion = dataVersion; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public java.time.LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(java.time.LocalDateTime createTime) { this.createTime = createTime; }
        public java.time.LocalDateTime getUpdateTime() { return updateTime; }
        public void setUpdateTime(java.time.LocalDateTime updateTime) { this.updateTime = updateTime; }
    }
}
