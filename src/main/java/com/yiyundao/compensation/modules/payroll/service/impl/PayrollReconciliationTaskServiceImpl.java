package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollReconciliationTaskMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistribution;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistributionItem;
import com.yiyundao.compensation.modules.payroll.entity.PayrollReconciliationTask;
import com.yiyundao.compensation.modules.payroll.service.PayrollReconciliationTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollReconciliationTaskServiceImpl
        extends ServiceImpl<PayrollReconciliationTaskMapper, PayrollReconciliationTask>
        implements PayrollReconciliationTaskService {

    private final com.yiyundao.compensation.infrastructure.dao.PayrollDistributionItemMapper distributionItemMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public PayrollReconciliationTask createOrRefresh(PayrollDistribution distribution) {
        if (distribution == null || distribution.getId() == null) {
            return null;
        }
        PayrollReconciliationTask task = getOne(new LambdaQueryWrapper<PayrollReconciliationTask>()
                .eq(PayrollReconciliationTask::getDistributionId, distribution.getId())
                .last("limit 1"));
        if (task == null) {
            task = new PayrollReconciliationTask();
            task.setDistributionId(distribution.getId());
        }

        BigDecimal expectedAmount = distribution.getTotalAmount() == null ? BigDecimal.ZERO : distribution.getTotalAmount();
        BigDecimal actualAmount = distribution.getActualAmount() == null ? BigDecimal.ZERO : distribution.getActualAmount();
        BigDecimal difference = expectedAmount.subtract(actualAmount);

        task.setTaskStatus("COMPLETED");
        task.setExpectedAmount(expectedAmount);
        task.setActualAmount(actualAmount);
        task.setDifference(difference);
        task.setResult(difference.compareTo(BigDecimal.ZERO) == 0 ? "MATCHED" : "MISMATCH");
        task.setDifferenceDetail(buildDifferenceDetail(distribution.getId()));

        if (task.getId() == null) {
            save(task);
        } else {
            updateById(task);
        }
        return task;
    }

    private String buildDifferenceDetail(Long distributionId) {
        List<PayrollDistributionItem> failedItems = distributionItemMapper.selectList(
                new LambdaQueryWrapper<PayrollDistributionItem>()
                        .eq(PayrollDistributionItem::getDistributionId, distributionId)
                        .in(PayrollDistributionItem::getItemStatus,
                                com.yiyundao.compensation.enums.PayrollDistributionItemStatus.FAILED)
                        .orderByAsc(PayrollDistributionItem::getId)
        );
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("failedCount", failedItems.size());
        detail.put("items", failedItems.stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("distributionItemId", item.getId());
            row.put("employeeId", item.getEmployeeId());
            row.put("amount", item.getAmount());
            row.put("failureReason", item.getFailureReason());
            return row;
        }).toList());
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception ex) {
            log.warn("序列化对账差异明细失败: {}", ex.getMessage());
            return "{}";
        }
    }
}
