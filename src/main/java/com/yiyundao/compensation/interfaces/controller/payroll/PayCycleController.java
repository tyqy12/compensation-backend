package com.yiyundao.compensation.interfaces.controller.payroll;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.interfaces.dto.payroll.PayCycleUpsertRequest;
import com.yiyundao.compensation.modules.payroll.entity.PayCycle;
import com.yiyundao.compensation.modules.payroll.service.PayCycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/payroll/cycles")
@RequiredArgsConstructor
public class PayCycleController {

    private final PayCycleService payCycleService;

    /**
     * 状态转换守护：允许的状态转换路径
     * open -> closed -> archived
     */
    private static final java.util.Map<String, java.util.Set<String>> ALLOWED_TRANSITIONS = java.util.Map.of(
        "open", java.util.Set.of("closed"),
        "closed", java.util.Set.of("archived"),
        "archived", java.util.Set.of() // 已归档不能转换
    );

    @PostMapping
    public ApiResponse<PayCycle> create(@Valid @RequestBody PayCycleUpsertRequest req) {
        // 检查唯一性约束：type + periodLabel 组合必须唯一
        LambdaQueryWrapper<PayCycle> uniqueCheck = new LambdaQueryWrapper<PayCycle>()
                .eq(PayCycle::getType, req.getType())
                .eq(PayCycle::getPeriodLabel, req.getPeriodLabel());
        long count = payCycleService.count(uniqueCheck);
        if (count > 0) {
            return ApiResponse.error(ErrorCode.RESOURCE_EXISTS,
                    "薪酬周期已存在: type=" + req.getType() + ", periodLabel=" + req.getPeriodLabel());
        }

        PayCycle c = new PayCycle();
        c.setType(req.getType());
        c.setPeriodLabel(req.getPeriodLabel());
        c.setStartDate(req.getStartDate());
        c.setEndDate(req.getEndDate());
        c.setCutoffDate(req.getCutoffDate());
        c.setStatus(req.getStatus() == null ? "open" : req.getStatus());

        try {
            payCycleService.save(c);
            log.info("创建薪酬周期成功: id={}, type={}, periodLabel={}", c.getId(), c.getType(), c.getPeriodLabel());
        } catch (Exception e) {
            log.error("创建薪酬周期失败: type={}, periodLabel={}", req.getType(), req.getPeriodLabel(), e);
            return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "创建薪酬周期失败");
        }

        return ApiResponse.success(c);
    }

    @PutMapping("/{id}")
    public ApiResponse<PayCycle> update(@PathVariable Long id, @Valid @RequestBody PayCycleUpsertRequest req) {
        PayCycle c = payCycleService.getById(id);
        if (c == null) return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "pay cycle not found");

        // 如果修改了type或periodLabel，检查唯一性
        if (!c.getType().equals(req.getType()) || !c.getPeriodLabel().equals(req.getPeriodLabel())) {
            LambdaQueryWrapper<PayCycle> uniqueCheck = new LambdaQueryWrapper<PayCycle>()
                    .eq(PayCycle::getType, req.getType())
                    .eq(PayCycle::getPeriodLabel, req.getPeriodLabel())
                    .ne(PayCycle::getId, id);
            long count = payCycleService.count(uniqueCheck);
            if (count > 0) {
                return ApiResponse.error(ErrorCode.RESOURCE_EXISTS,
                        "薪酬周期已存在: type=" + req.getType() + ", periodLabel=" + req.getPeriodLabel());
            }
        }

        // 状态转换守护
        if (req.getStatus() != null && !req.getStatus().equals(c.getStatus())) {
            String currentStatus = c.getStatus();
            String newStatus = req.getStatus();
            java.util.Set<String> allowedNext = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, java.util.Set.of());
            if (!allowedNext.contains(newStatus)) {
                return ApiResponse.error(ErrorCode.INVALID_STATUS,
                    "无效的状态转换: current=" + currentStatus + ", requested=" + newStatus +
                    ", 允许的转换: " + (allowedNext.isEmpty() ? "无（当前状态不允许转换）" : String.join(",", allowedNext)));
            }
            log.info("薪酬周期状态转换: id={}, {} -> {}", id, currentStatus, newStatus);
        }

        c.setType(req.getType());
        c.setPeriodLabel(req.getPeriodLabel());
        c.setStartDate(req.getStartDate());
        c.setEndDate(req.getEndDate());
        c.setCutoffDate(req.getCutoffDate());
        if (req.getStatus() != null) c.setStatus(req.getStatus());

        try {
            payCycleService.updateById(c);
            log.info("更新薪酬周期成功: id={}", id);
        } catch (Exception e) {
            log.error("更新薪酬周期失败: id={}", id, e);
            return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "更新薪酬周期失败");
        }

        return ApiResponse.success(c);
    }

    /**
     * 推进薪酬周期状态（专用接口，更清晰的语义）
     * open -> closed, closed -> archived
     */
    @PostMapping("/{id}/advance")
    public ApiResponse<PayCycle> advanceStatus(@PathVariable Long id) {
        PayCycle c = payCycleService.getById(id);
        if (c == null) return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "pay cycle not found");

        String currentStatus = c.getStatus();
        java.util.Set<String> allowedNext = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, java.util.Set.of());

        if (allowedNext.isEmpty()) {
            return ApiResponse.error(ErrorCode.INVALID_STATUS, "当前状态不允许转换: current=" + currentStatus);
        }

        // 取第一个允许的下一个状态
        String nextStatus = allowedNext.iterator().next();

        try {
            LambdaUpdateWrapper<PayCycle> updateWrapper = new LambdaUpdateWrapper<PayCycle>()
                    .eq(PayCycle::getId, id)
                    .set(PayCycle::getStatus, nextStatus);
            payCycleService.update(updateWrapper);

            c.setStatus(nextStatus);
            log.info("薪酬周期状态推进成功: id={}, {} -> {}", id, currentStatus, nextStatus);
            return ApiResponse.success(c);
        } catch (Exception e) {
            log.error("推进薪酬周期状态失败: id={}", id, e);
            return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "推进状态失败");
        }
    }

    @GetMapping
    public ApiResponse<Page<PayCycle>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String periodLabel) {
        Page<PayCycle> p = new Page<>(page, size);
        LambdaQueryWrapper<PayCycle> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) qw.eq(PayCycle::getStatus, status);
        if (periodLabel != null && !periodLabel.isBlank()) qw.eq(PayCycle::getPeriodLabel, periodLabel);
        qw.orderByDesc(PayCycle::getCreateTime);
        return ApiResponse.success(payCycleService.page(p, qw));
    }

    @GetMapping("/{id}")
    public ApiResponse<PayCycle> get(@PathVariable Long id) {
        PayCycle cycle = payCycleService.getById(id);
        if (cycle == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "pay cycle not found");
        }
        return ApiResponse.success(cycle);
    }

    /**
     * 获取指定类型的开放周期
     */
    @GetMapping("/open")
    public ApiResponse<List<PayCycle>> getOpenCycles(@RequestParam String type) {
        LambdaQueryWrapper<PayCycle> qw = new LambdaQueryWrapper<PayCycle>()
                .eq(PayCycle::getType, type)
                .eq(PayCycle::getStatus, "open")
                .orderByDesc(PayCycle::getCreateTime);
        return ApiResponse.success(payCycleService.list(qw));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        PayCycle c = payCycleService.getById(id);
        if (c == null) return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "pay cycle not found");

        // 状态检查：只能删除草稿状态
        if (!"draft".equals(c.getStatus())) {
            return ApiResponse.error(ErrorCode.INVALID_STATUS, "只能删除草稿状态的周期");
        }

        // 检查是否有关联的数据（如果需要）
        payCycleService.lambdaQuery()
                .eq(PayCycle::getId, id)
                .exists();
        // 如果有批次关联，不允许删除（此处可根据业务需求调整）

        try {
            payCycleService.removeById(id);
            log.info("删除薪酬周期成功: id={}, type={}, periodLabel={}", id, c.getType(), c.getPeriodLabel());
        } catch (Exception e) {
            log.error("删除薪酬周期失败: id={}", id, e);
            return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "删除薪酬周期失败");
        }

        return ApiResponse.success(null);
    }
}
