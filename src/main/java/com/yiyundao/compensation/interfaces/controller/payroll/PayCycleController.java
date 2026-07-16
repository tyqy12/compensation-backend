package com.yiyundao.compensation.interfaces.controller.payroll;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.interfaces.dto.payroll.PayCycleResponseDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayCycleUpsertRequest;
import com.yiyundao.compensation.modules.payroll.entity.PayCycle;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.SalaryTemplate;
import com.yiyundao.compensation.modules.payroll.service.PayCycleService;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.SalaryTemplateService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/payroll/cycles")
@SecurityAnnotations.IsFinanceOrAdmin
@RequiredArgsConstructor
public class PayCycleController {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;

    private final PayCycleService payCycleService;
    private final PayrollBatchService payrollBatchService;
    private final SalaryTemplateService salaryTemplateService;

    /**
     * 状态转换守护：允许的状态转换路径
     * draft -> open -> closed -> archived
     * closed 支持重新启用: closed -> open
     */
    private static final Set<String> ALLOWED_STATUSES = Set.of("draft", "open", "closed", "archived");

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
        "draft", Set.of("open", "archived"),
        "open", Set.of("closed"),
        "closed", Set.of("open", "archived"),
        "archived", Set.of()
    );

    @PostMapping
    public ApiResponse<PayCycleResponseDto> create(@Valid @RequestBody PayCycleUpsertRequest req) {
        String normalizedType = normalizeLower(req.getType());
        String normalizedPeriodLabel = normalizeText(req.getPeriodLabel());
        if (normalizedType == null || normalizedPeriodLabel == null) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, "type 与 periodLabel 不能为空");
        }
        if (!isDateRangeValid(req.getStartDate(), req.getEndDate())) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, "期间范围无效：开始日期不能晚于结束日期");
        }
        if (!isCutoffDateValid(req.getCutoffDate(), req.getStartDate(), req.getEndDate())) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, "截数日需位于期间范围内");
        }

        String requestedStatus = normalizeStatus(req.getStatus());
        if (requestedStatus == null) {
            requestedStatus = "draft";
        }
        if (!ALLOWED_STATUSES.contains(requestedStatus)) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, "无效状态: " + req.getStatus()
                + "，允许值: " + String.join(",", ALLOWED_STATUSES));
        }
        if ("open".equals(requestedStatus)) {
            validateRunnableCycle(req.getStartDate(), req.getEndDate(), req.getCutoffDate(),
                req.getPayDay(), req.getCycleType());
        }
        RuleBinding ruleBinding = resolveRuleBinding(
            req.getRuleTemplateId(), req.getRuleTemplateVersion(), normalizedType, requestedStatus, null);

        // 检查唯一性约束：type + periodLabel 组合必须唯一
        LambdaQueryWrapper<PayCycle> uniqueCheck = new LambdaQueryWrapper<PayCycle>()
            .eq(PayCycle::getType, normalizedType)
            .eq(PayCycle::getPeriodLabel, normalizedPeriodLabel);
        long count = payCycleService.count(uniqueCheck);
        if (count > 0) {
            return ApiResponse.error(ErrorCode.RESOURCE_EXISTS,
                "薪酬周期已存在: type=" + normalizedType + ", periodLabel=" + normalizedPeriodLabel);
        }

        String cycleCode = resolveCycleCode(req.getCycleCode(), null, normalizedType, normalizedPeriodLabel);
        if (isCycleCodeExists(cycleCode, null)) {
            return ApiResponse.error(ErrorCode.RESOURCE_EXISTS, "周期编码已存在: " + cycleCode);
        }

        PayCycle c = new PayCycle();
        c.setType(normalizedType);
        c.setRuleTemplateId(ruleBinding.templateId());
        c.setRuleTemplateVersion(ruleBinding.version());
        c.setPeriodLabel(normalizedPeriodLabel);
        c.setCycleCode(cycleCode);
        c.setCycleName(defaultIfBlank(req.getCycleName(), normalizedPeriodLabel));
        c.setCycleType(defaultIfBlank(normalizeLower(req.getCycleType()), normalizedType));
        c.setStartDate(req.getStartDate());
        c.setEndDate(req.getEndDate());
        c.setCutoffDate(req.getCutoffDate());
        c.setPayDay(req.getPayDay());
        c.setLeadDays(req.getLeadDays());
        c.setGraceDays(req.getGraceDays());
        c.setTimezone(defaultIfBlank(req.getTimezone(), "UTC+8"));
        c.setDescription(normalizeText(req.getDescription()));
        c.setStatus(requestedStatus);

        try {
            payCycleService.save(c);
            log.info("创建薪酬周期成功: id={}, type={}, periodLabel={}, cycleCode={}",
                c.getId(), c.getType(), c.getPeriodLabel(), c.getCycleCode());
        } catch (Exception e) {
            log.error("创建薪酬周期失败: type={}, periodLabel={}", normalizedType, normalizedPeriodLabel, e);
            return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "创建薪酬周期失败");
        }

        return ApiResponse.success(PayCycleResponseDto.from(c));
    }

    @PutMapping("/{id}")
    public ApiResponse<PayCycleResponseDto> update(@PathVariable Long id, @Valid @RequestBody PayCycleUpsertRequest req) {
        PayCycle c = payCycleService.getById(id);
        if (c == null) return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "pay cycle not found");

        String normalizedType = normalizeLower(req.getType());
        String normalizedPeriodLabel = normalizeText(req.getPeriodLabel());
        if (normalizedType == null || normalizedPeriodLabel == null) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, "type 与 periodLabel 不能为空");
        }
        if (!isDateRangeValid(req.getStartDate(), req.getEndDate())) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, "期间范围无效：开始日期不能晚于结束日期");
        }
        if (!isCutoffDateValid(req.getCutoffDate(), req.getStartDate(), req.getEndDate())) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, "截数日需位于期间范围内");
        }

        String requestedStatus = normalizeStatus(req.getStatus());
        if (requestedStatus != null && !ALLOWED_STATUSES.contains(requestedStatus)) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, "无效状态: " + req.getStatus()
                + "，允许值: " + String.join(",", ALLOWED_STATUSES));
        }

        // 如果修改了type或periodLabel，检查唯一性
        if (!normalizedType.equals(c.getType()) || !normalizedPeriodLabel.equals(c.getPeriodLabel())) {
            LambdaQueryWrapper<PayCycle> uniqueCheck = new LambdaQueryWrapper<PayCycle>()
                .eq(PayCycle::getType, normalizedType)
                .eq(PayCycle::getPeriodLabel, normalizedPeriodLabel)
                .ne(PayCycle::getId, id);
            long count = payCycleService.count(uniqueCheck);
            if (count > 0) {
                return ApiResponse.error(ErrorCode.RESOURCE_EXISTS,
                    "薪酬周期已存在: type=" + normalizedType + ", periodLabel=" + normalizedPeriodLabel);
            }
        }

        String cycleCode = resolveCycleCode(req.getCycleCode(), c.getCycleCode(), normalizedType, normalizedPeriodLabel);
        if (isCycleCodeExists(cycleCode, id)) {
            return ApiResponse.error(ErrorCode.RESOURCE_EXISTS, "周期编码已存在: " + cycleCode);
        }

        // 状态转换守护
        String originalStatus = c.getStatus();
        String currentStatus = normalizeStatus(c.getStatus());
        if (currentStatus == null) {
            currentStatus = "draft";
        }
        if (c.getStatus() == null || !currentStatus.equals(c.getStatus())) {
            c.setStatus(currentStatus);
        }
        if (requestedStatus != null && !requestedStatus.equals(currentStatus)) {
            Set<String> allowedNext = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
            if (!allowedNext.contains(requestedStatus)) {
                return ApiResponse.error(ErrorCode.INVALID_STATUS,
                    "无效的状态转换: current=" + currentStatus + ", requested=" + requestedStatus +
                        ", 允许的转换: " + (allowedNext.isEmpty() ? "无（当前状态不允许转换）" : String.join(",", allowedNext)));
            }
            log.info("薪酬周期状态转换: id={}, {} -> {}", id, currentStatus, requestedStatus);
        }

        if ("open".equals(currentStatus)
            && (requestedStatus == null || "open".equals(requestedStatus))
            && hasSchedulingFieldsChanged(c, normalizedType, normalizedPeriodLabel, req)) {
            return ApiResponse.error(ErrorCode.INVALID_STATUS,
                "开放中的发薪日历不能直接修改，请先停用后再调整运行安排");
        }

        if ("open".equals(requestedStatus != null ? requestedStatus : currentStatus)) {
            validateRunnableCycle(req.getStartDate(), req.getEndDate(), req.getCutoffDate(),
                req.getPayDay(), req.getCycleType());
        }

        RuleBinding ruleBinding = resolveRuleBinding(
            req.getRuleTemplateId(), req.getRuleTemplateVersion(), normalizedType,
            requestedStatus != null ? requestedStatus : currentStatus, c);
        if ("open".equals(currentStatus)
            && (requestedStatus == null || "open".equals(requestedStatus))
            && hasRuleBindingChanged(c, ruleBinding)) {
            return ApiResponse.error(ErrorCode.INVALID_STATUS,
                "开放中的发薪日历不能直接更换规则包，请先关闭日历后再调整规则版本");
        }

        c.setType(normalizedType);
        c.setRuleTemplateId(ruleBinding.templateId());
        c.setRuleTemplateVersion(ruleBinding.version());
        c.setPeriodLabel(normalizedPeriodLabel);
        c.setCycleCode(cycleCode);
        c.setCycleName(defaultIfBlank(req.getCycleName(), normalizedPeriodLabel));
        c.setCycleType(defaultIfBlank(normalizeLower(req.getCycleType()), normalizedType));
        c.setStartDate(req.getStartDate());
        c.setEndDate(req.getEndDate());
        c.setCutoffDate(req.getCutoffDate());
        c.setPayDay(req.getPayDay());
        c.setLeadDays(req.getLeadDays());
        c.setGraceDays(req.getGraceDays());
        c.setTimezone(defaultIfBlank(req.getTimezone(), "UTC+8"));
        c.setDescription(normalizeText(req.getDescription()));
        if (requestedStatus != null) {
            c.setStatus(requestedStatus);
        }

        try {
            if (!payCycleService.update(buildConditionalUpdateWrapper(c, originalStatus))) {
                return ApiResponse.error(ErrorCode.REQUEST_CONFLICT, "薪酬周期状态已变更，请刷新后重试");
            }
            log.info("更新薪酬周期成功: id={}", id);
        } catch (Exception e) {
            log.error("更新薪酬周期失败: id={}", id, e);
            return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "更新薪酬周期失败");
        }

        return ApiResponse.success(PayCycleResponseDto.from(c));
    }

    /**
     * 推进薪酬周期状态（专用接口，更清晰的语义）
     * draft -> open -> closed -> archived
     */
    @PostMapping("/{id}/advance")
    public ApiResponse<PayCycleResponseDto> advanceStatus(@PathVariable Long id) {
        PayCycle c = payCycleService.getById(id);
        if (c == null) return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "pay cycle not found");

        String currentStatus = normalizeStatus(c.getStatus());
        if (currentStatus == null) {
            currentStatus = "draft";
        }
        String nextStatus = switch (currentStatus) {
            case "draft" -> "open";
            case "open" -> "closed";
            case "closed" -> "archived";
            default -> null;
        };

        if (nextStatus == null) {
            return ApiResponse.error(ErrorCode.INVALID_STATUS, "当前状态不允许转换: current=" + currentStatus);
        }

        if ("open".equals(nextStatus)) {
            validateRunnableCycle(c.getStartDate(), c.getEndDate(), c.getCutoffDate(),
                c.getPayDay(), c.getCycleType());
        }

        RuleBinding ruleBinding = resolveRuleBinding(
            c.getRuleTemplateId(), c.getRuleTemplateVersion(), c.getType(), nextStatus, c);

        try {
            UpdateWrapper<PayCycle> updateWrapper = new UpdateWrapper<PayCycle>();
            addCurrentStatusGuard(updateWrapper, id, c.getStatus());
            updateWrapper.set("status", nextStatus);
            updateWrapper.set("rule_template_id", ruleBinding.templateId());
            updateWrapper.set("rule_template_version", ruleBinding.version());
            if (!payCycleService.update(updateWrapper)) {
                return ApiResponse.error(ErrorCode.REQUEST_CONFLICT, "薪酬周期状态已变更，请刷新后重试");
            }

            c.setStatus(nextStatus);
            c.setRuleTemplateId(ruleBinding.templateId());
            c.setRuleTemplateVersion(ruleBinding.version());
            log.info("薪酬周期状态推进成功: id={}, {} -> {}", id, currentStatus, nextStatus);
            return ApiResponse.success(PayCycleResponseDto.from(c));
        } catch (Exception e) {
            log.error("推进薪酬周期状态失败: id={}", id, e);
            return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "推进状态失败");
        }
    }

    @GetMapping
    public ApiResponse<Page<PayCycleResponseDto>> list(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "10") int size,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String periodLabel,
                                                       @RequestParam(required = false) String keyword) {
        Page<PayCycle> p = new Page<>(safePage(page), safeSize(size));
        LambdaQueryWrapper<PayCycle> qw = new LambdaQueryWrapper<>();

        if (status != null && !status.isBlank()) {
            String normalizedStatus = normalizeStatus(status);
            if (!ALLOWED_STATUSES.contains(normalizedStatus)) {
                return ApiResponse.error(ErrorCode.PARAM_INVALID, "无效状态: " + status
                    + "，允许值: " + String.join(",", ALLOWED_STATUSES));
            }
            qw.eq(PayCycle::getStatus, normalizedStatus);
        }
        if (periodLabel != null && !periodLabel.isBlank()) {
            qw.like(PayCycle::getPeriodLabel, periodLabel.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String keywordValue = keyword.trim();
            qw.and(wrapper -> wrapper
                .like(PayCycle::getCycleName, keywordValue)
                .or()
                .like(PayCycle::getCycleCode, keywordValue)
                .or()
                .like(PayCycle::getPeriodLabel, keywordValue));
        }

        qw.orderByAsc(PayCycle::getNextExecutionTime)
            .orderByDesc(PayCycle::getCreateTime);
        return ApiResponse.success(toResponsePage(payCycleService.page(p, qw)));
    }

    @GetMapping("/{id}")
    public ApiResponse<PayCycleResponseDto> get(@PathVariable Long id) {
        PayCycle cycle = payCycleService.getById(id);
        if (cycle == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "pay cycle not found");
        }
        return ApiResponse.success(PayCycleResponseDto.from(cycle));
    }

    /**
     * 获取指定类型的开放周期
     */
    @GetMapping("/open")
    public ApiResponse<List<PayCycleResponseDto>> getOpenCycles(@RequestParam String type) {
        String normalizedType = normalizeLower(type);
        LambdaQueryWrapper<PayCycle> qw = new LambdaQueryWrapper<PayCycle>()
            .eq(PayCycle::getType, normalizedType)
            .eq(PayCycle::getStatus, "open")
            .orderByDesc(PayCycle::getCreateTime);
        return ApiResponse.success(payCycleService.list(qw).stream()
            .map(PayCycleResponseDto::from)
            .toList());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        PayCycle c = payCycleService.getById(id);
        if (c == null) return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "pay cycle not found");

        // 状态检查：只能删除草稿状态
        if (!"draft".equals(normalizeStatus(c.getStatus()))) {
            return ApiResponse.error(ErrorCode.INVALID_STATUS, "只能删除草稿状态的周期");
        }

        long linkedBatchCount = payrollBatchService.count(new LambdaQueryWrapper<PayrollBatch>()
            .eq(PayrollBatch::getPayCycleId, id));
        if (linkedBatchCount > 0) {
            return ApiResponse.error(ErrorCode.REQUEST_CONFLICT, "薪酬周期已关联薪资批次，不能删除");
        }

        try {
            payCycleService.removeById(id);
            log.info("删除薪酬周期成功: id={}, type={}, periodLabel={}", id, c.getType(), c.getPeriodLabel());
        } catch (Exception e) {
            log.error("删除薪酬周期失败: id={}", id, e);
            return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "删除薪酬周期失败");
        }

        return ApiResponse.success(null);
    }

    private UpdateWrapper<PayCycle> buildConditionalUpdateWrapper(PayCycle cycle, String originalStatus) {
        UpdateWrapper<PayCycle> wrapper = new UpdateWrapper<>();
        addCurrentStatusGuard(wrapper, cycle.getId(), originalStatus);
        wrapper.set("type", cycle.getType())
                .set("rule_template_id", cycle.getRuleTemplateId())
                .set("rule_template_version", cycle.getRuleTemplateVersion())
                .set("period_label", cycle.getPeriodLabel())
                .set("cycle_code", cycle.getCycleCode())
                .set("cycle_name", cycle.getCycleName())
                .set("cycle_type", cycle.getCycleType())
                .set("start_date", cycle.getStartDate())
                .set("end_date", cycle.getEndDate())
                .set("cutoff_date", cycle.getCutoffDate())
                .set("pay_day", cycle.getPayDay())
                .set("lead_days", cycle.getLeadDays())
                .set("grace_days", cycle.getGraceDays())
                .set("timezone", cycle.getTimezone())
                .set("description", cycle.getDescription())
                .set("status", cycle.getStatus());
        return wrapper;
    }

    private void addCurrentStatusGuard(UpdateWrapper<PayCycle> wrapper, Long id, String currentStatus) {
        wrapper.eq("id", id);
        if (normalizeText(currentStatus) == null) {
            wrapper.isNull("status");
        } else {
            wrapper.eq("status", currentStatus);
        }
    }

    private boolean isCycleCodeExists(String cycleCode, Long excludeId) {
        if (cycleCode == null) {
            return false;
        }
        LambdaQueryWrapper<PayCycle> query = new LambdaQueryWrapper<PayCycle>()
            .eq(PayCycle::getCycleCode, cycleCode);
        if (excludeId != null) {
            query.ne(PayCycle::getId, excludeId);
        }
        return payCycleService.count(query) > 0;
    }

    private static Page<PayCycleResponseDto> toResponsePage(Page<PayCycle> source) {
        Page<PayCycleResponseDto> target = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        target.setPages(source.getPages());
        target.setRecords(source.getRecords().stream()
            .map(PayCycleResponseDto::from)
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

    private static boolean isDateRangeValid(LocalDate startDate, LocalDate endDate) {
        return startDate == null || endDate == null || !startDate.isAfter(endDate);
    }

    private static boolean isCutoffDateValid(LocalDate cutoffDate, LocalDate startDate, LocalDate endDate) {
        if (cutoffDate == null) {
            return true;
        }
        if (startDate != null && cutoffDate.isBefore(startDate)) {
            return false;
        }
        return endDate == null || !cutoffDate.isAfter(endDate);
    }

    private static void validateRunnableCycle(LocalDate startDate,
                                              LocalDate endDate,
                                              LocalDate cutoffDate,
                                              Integer payDay,
                                              String cycleType) {
        if (startDate == null || endDate == null) {
            throw new BusinessException(
                ErrorCode.PARAM_INVALID, "开放发薪日历必须配置完整的期间范围");
        }
        if (cutoffDate == null) {
            throw new BusinessException(
                ErrorCode.PARAM_INVALID, "开放发薪日历必须配置截数日");
        }
        if (payDay == null || payDay < 1 || payDay > 31) {
            throw new BusinessException(
                ErrorCode.PARAM_INVALID, "开放发薪日历必须配置有效发薪日");
        }
        if (normalizeText(cycleType) == null) {
            throw new BusinessException(
                ErrorCode.PARAM_INVALID, "开放发薪日历必须配置运行频率");
        }
    }

    private RuleBinding resolveRuleBinding(Long requestedTemplateId,
                                           Long requestedVersion,
                                           String payrollType,
                                           String targetStatus,
                                           PayCycle existing) {
        Long templateId = requestedTemplateId != null
            ? requestedTemplateId
            : existing != null ? existing.getRuleTemplateId() : null;
        Long version = requestedVersion != null
            ? requestedVersion
            : existing != null ? existing.getRuleTemplateVersion() : null;
        boolean preservingExistingBinding = requestedTemplateId == null
            && requestedVersion == null
            && existing != null;

        if (requestedVersion != null && templateId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "规则包版本必须随规则包ID一起传入");
        }

        // 日历表单只维护运行安排；开放时自动绑定该用工类型唯一的启用规则包。
        if (templateId == null && "open".equals(targetStatus)) {
            List<SalaryTemplate> enabledTemplates = salaryTemplateService.list(
                new LambdaQueryWrapper<SalaryTemplate>()
                    .eq(SalaryTemplate::getType, payrollType)
                    .eq(SalaryTemplate::getStatus, "enabled")
                    .eq(SalaryTemplate::getDeleted, 0)
            );
            if (enabledTemplates == null || enabledTemplates.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "开放发薪日历前，必须先配置一个启用中的" + payrollType + "规则包");
            }
            if (enabledTemplates.size() > 1) {
                throw new BusinessException(ErrorCode.REQUEST_CONFLICT,
                    payrollType + "存在多个启用规则包，无法自动确定日历绑定关系");
            }
            SalaryTemplate enabledTemplate = enabledTemplates.get(0);
            templateId = enabledTemplate.getId();
            version = enabledTemplate.getDataVersion();
        }

        if (templateId == null) {
            if ("open".equals(targetStatus)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "开放发薪日历必须绑定规则包版本");
            }
            return new RuleBinding(null, null);
        }

        SalaryTemplate template = salaryTemplateService.getById(templateId);
        if (template == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "规则包不存在");
        }
        if (!Objects.equals(payrollType, normalizeLower(template.getType()))) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                "发薪日历用工类型必须与规则包一致: cycle=" + payrollType + ", template=" + template.getType());
        }

        Long currentVersion = template.getDataVersion() == null ? 1L : template.getDataVersion();
        Long effectiveVersion = version == null ? currentVersion : version;
        if ("open".equals(targetStatus)
            && preservingExistingBinding
            && !"open".equalsIgnoreCase(existing.getStatus())) {
            effectiveVersion = currentVersion;
        }
        if ("open".equals(targetStatus) && !Objects.equals(currentVersion, effectiveVersion)) {
            throw new BusinessException(ErrorCode.REQUEST_CONFLICT,
                "规则包版本已变化，请重新选择当前版本后再开放发薪日历");
        }
        if ("open".equals(targetStatus) && !"enabled".equalsIgnoreCase(template.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "开放发薪日历只能绑定启用中的规则包");
        }
        return new RuleBinding(templateId, effectiveVersion);
    }

    private static boolean hasRuleBindingChanged(PayCycle current, RuleBinding next) {
        return !Objects.equals(current.getRuleTemplateId(), next.templateId())
            || !Objects.equals(current.getRuleTemplateVersion(), next.version());
    }

    private static boolean hasSchedulingFieldsChanged(PayCycle current,
                                                       String type,
                                                       String periodLabel,
                                                       PayCycleUpsertRequest request) {
        String cycleType = defaultIfBlank(normalizeLower(request.getCycleType()), type);
        return !Objects.equals(current.getType(), type)
            || !Objects.equals(current.getPeriodLabel(), periodLabel)
            || !Objects.equals(current.getCycleType(), cycleType)
            || !Objects.equals(current.getStartDate(), request.getStartDate())
            || !Objects.equals(current.getEndDate(), request.getEndDate())
            || !Objects.equals(current.getCutoffDate(), request.getCutoffDate())
            || !Objects.equals(current.getPayDay(), request.getPayDay())
            || !Objects.equals(current.getLeadDays(), request.getLeadDays())
            || !Objects.equals(current.getGraceDays(), request.getGraceDays())
            || !Objects.equals(current.getTimezone(), defaultIfBlank(request.getTimezone(), "UTC+8"));
    }

    private record RuleBinding(Long templateId, Long version) {
    }

    private static String resolveCycleCode(String code, String existingCode, String type, String periodLabel) {
        String normalized = normalizeCycleCode(code);
        if (normalized != null) {
            return normalized;
        }
        String existing = normalizeCycleCode(existingCode);
        if (existing != null) {
            return existing;
        }
        return buildDefaultCycleCode(type, periodLabel);
    }

    private static String buildDefaultCycleCode(String type, String periodLabel) {
        String base = "CYCLE_" + defaultIfBlank(type, "monthly") + "_" + defaultIfBlank(periodLabel, "unknown");
        return normalizeCycleCode(base);
    }

    private static String normalizeCycleCode(String code) {
        String normalized = normalizeText(code);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9_\\-]", "_")
            .replaceAll("_+", "_");
        if (normalized.length() > 64) {
            normalized = normalized.substring(0, 64);
        }
        return normalized;
    }

    private static String normalizeLower(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        String normalized = normalizeText(value);
        return normalized == null ? defaultValue : normalized;
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "active" -> "open";
            case "inactive" -> "closed";
            default -> normalized;
        };
    }
}
