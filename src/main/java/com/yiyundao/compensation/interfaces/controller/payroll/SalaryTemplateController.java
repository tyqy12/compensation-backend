package com.yiyundao.compensation.interfaces.controller.payroll;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.interfaces.dto.payroll.SalaryTemplateUpsertRequest;
import com.yiyundao.compensation.modules.payroll.entity.SalaryTemplate;
import com.yiyundao.compensation.modules.payroll.service.SalaryTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/payroll/templates")
@RequiredArgsConstructor
public class SalaryTemplateController {

    private final SalaryTemplateService salaryTemplateService;
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

            for (int i = 0; i < root.size(); i++) {
                JsonNode item = root.get(i);
                if (!item.has("code") || item.get("code").asText().isBlank()) {
                    errors.add("itemsJson[" + i + "]: 缺少code字段或code为空");
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
            // taxRuleJson可以为空，使用默认值
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
                if (tax.has("rate") && !tax.get("rate").isNumber()) {
                    errors.add("taxRuleJson.tax.rate必须是数字类型");
                }
                if (tax.has("applyOn")) {
                    String applyOn = tax.get("applyOn").asText();
                    if (!List.of("TAXABLE_EARNINGS", "GROSS", "EARNINGS_MINUS_DEDUCTIONS", "TAXABLE_EARNINGS_MINUS_DEDUCTIONS")
                            .contains(applyOn.toUpperCase())) {
                        errors.add("taxRuleJson.tax.applyOn必须是: TAXABLE_EARNINGS, GROSS, EARNINGS_MINUS_DEDUCTIONS, TAXABLE_EARNINGS_MINUS_DEDUCTIONS");
                    }
                }
            }

            // 验证social部分
            if (root.has("social")) {
                JsonNode social = root.get("social");
                if (social.has("rate") && !social.get("rate").isNumber()) {
                    errors.add("taxRuleJson.social.rate必须是数字类型");
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
    public ApiResponse<SalaryTemplate> create(@Valid @RequestBody SalaryTemplateUpsertRequest req) {
        // JSON语法验证
        List<String> itemsErrors = validateItemsJson(req.getItemsJson());
        if (!itemsErrors.isEmpty()) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, "itemsJson验证失败: " + String.join("; ", itemsErrors));
        }

        List<String> taxErrors = validateTaxRuleJson(req.getTaxRuleJson());
        if (!taxErrors.isEmpty()) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, "taxRuleJson验证失败: " + String.join("; ", taxErrors));
        }

        SalaryTemplate t = new SalaryTemplate();
        t.setName(req.getName());
        t.setType(req.getType());
        t.setItemsJson(req.getItemsJson());
        t.setTaxRuleJson(req.getTaxRuleJson());
        t.setStatus(req.getStatus() == null ? "enabled" : req.getStatus());
        t.setDataVersion(1L); // 初始版本号

        try {
            salaryTemplateService.save(t);
            log.info("创建薪资模板成功: id={}, name={}, dataVersion={}", t.getId(), t.getName(), t.getDataVersion());
        } catch (Exception e) {
            log.error("创建薪资模板失败: name={}", req.getName(), e);
            return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "创建薪资模板失败");
        }

        return ApiResponse.success(t);
    }

    @PutMapping("/{id}")
    public ApiResponse<SalaryTemplate> update(@PathVariable Long id, @Valid @RequestBody SalaryTemplateUpsertRequest req) {
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
        t.setType(req.getType() != null ? req.getType() : t.getType());
        t.setItemsJson(req.getItemsJson() != null ? req.getItemsJson() : t.getItemsJson());
        t.setTaxRuleJson(req.getTaxRuleJson() != null ? req.getTaxRuleJson() : t.getTaxRuleJson());
        if (req.getStatus() != null) t.setStatus(req.getStatus());

        try {
            salaryTemplateService.updateById(t);
            log.info("更新薪资模板成功: id={}, dataVersion={}", id, t.getDataVersion());
        } catch (Exception e) {
            log.error("更新薪资模板失败: id={}", id, e);
            return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "更新薪资模板失败");
        }

        return ApiResponse.success(t);
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

    @GetMapping
    public ApiResponse<Page<SalaryTemplate>> list(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam(required = false) String type,
                                                  @RequestParam(required = false) String status) {
        Page<SalaryTemplate> p = new Page<>(page, size);
        LambdaQueryWrapper<SalaryTemplate> qw = new LambdaQueryWrapper<>();
        if (type != null && !type.isBlank()) qw.eq(SalaryTemplate::getType, type);
        if (status != null && !status.isBlank()) qw.eq(SalaryTemplate::getStatus, status);
        qw.orderByDesc(SalaryTemplate::getVersion); // 按版本号降序排列
        return ApiResponse.success(salaryTemplateService.page(p, qw));
    }

    @GetMapping("/{id}")
    public ApiResponse<SalaryTemplate> get(@PathVariable Long id) {
        SalaryTemplate template = salaryTemplateService.getById(id);
        if (template == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "template not found");
        }
        return ApiResponse.success(template);
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
