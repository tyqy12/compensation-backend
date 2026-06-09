package com.yiyundao.compensation.modules.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.dto.dashboard.*;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.notification.entity.NotificationRecord;
import com.yiyundao.compensation.modules.notification.service.NotificationRecordService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.modules.audit.entity.AuditLog;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import com.yiyundao.compensation.modules.system.service.OrgSyncTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final EmployeeService employeeService;
    private final PaymentRecordService paymentRecordService;
    private final NotificationRecordService notificationRecordService;
    private final PaymentBatchService paymentBatchService;
    private final SysUserService sysUserService;
    private final AuditLogService auditLogService;
    private final ApprovalEngine approvalEngine;
    private final OrgSyncTaskService orgSyncTaskService;
    private final JdbcTemplate jdbcTemplate;

    public DashboardMetricsDto collectMetrics() {
        LocalDateTime monthStart = firstDayOfThisMonth();
        LocalDateTime nextMonthStart = firstDayOfNextMonth();
        LocalDateTime prevMonthStart = firstDayOfPrevMonth();

        // 员工总数（按现存记录，逻辑删除自动过滤）
        long employeeTotal = employeeService.count();
        long employeePrevTotal = employeeService.count(new LambdaQueryWrapper<Employee>()
                .le(Employee::getCreateTime, endOfPrevMonth()));

        // 本月支付总额（成功）
        BigDecimal currentMonthPayment = sumPayments(monthStart, nextMonthStart);
        BigDecimal prevMonthPayment = sumPayments(prevMonthStart, monthStart);

        // 待处理批次数量（状态：submitted/approved）- 当前 + 当月新增对比上月新增
        long pendingNow = paymentBatchService.count(new LambdaQueryWrapper<PaymentBatch>()
                .in(PaymentBatch::getStatus, BatchStatus.SUBMITTED, BatchStatus.APPROVED));
        long pendingPrevMonth = paymentBatchService.count(new LambdaQueryWrapper<PaymentBatch>()
                .between(PaymentBatch::getSubmitTime, prevMonthStart, monthStart)
                .in(PaymentBatch::getStatus, BatchStatus.SUBMITTED, BatchStatus.APPROVED));
        long pendingCurrentMonth = paymentBatchService.count(new LambdaQueryWrapper<PaymentBatch>()
                .between(PaymentBatch::getSubmitTime, monthStart, nextMonthStart)
                .in(PaymentBatch::getStatus, BatchStatus.SUBMITTED, BatchStatus.APPROVED));

        // 用户绑定率（按系统用户绑定平台账号）
        long userTotal = sysUserService.count();
        long userBound = countBoundUsers(null);
        long userTotalPrev = sysUserService.count(new LambdaQueryWrapper<SysUser>()
                .le(SysUser::getCreateTime, endOfPrevMonth()));
        long userBoundPrev = countBoundUsers(endOfPrevMonth());

        DashboardMetricsDto dto = new DashboardMetricsDto();
        dto.setEmployeeTotal((int) employeeTotal);
        dto.setEmployeeGrowthRate(calcGrowthPercent(employeeTotal, employeePrevTotal));

        dto.setMonthlyPaymentAmount(currentMonthPayment.setScale(2, RoundingMode.HALF_UP));
        dto.setMonthlyPaymentGrowthRate(calcGrowthPercent(currentMonthPayment, prevMonthPayment));

        dto.setPendingBatchCount((int) pendingNow);
        dto.setPendingBatchChangeRate(calcGrowthPercent(pendingCurrentMonth, pendingPrevMonth));

        double bindingNow = percent(userBound, userTotal);
        double bindingPrev = percent(userBoundPrev, userTotalPrev);
        dto.setUserBindingRate(round(bindingNow));
        dto.setUserBindingGrowthRate(round(bindingNow - bindingPrev));
        return dto;
    }

    public SystemStatusDto collectStatus() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<SystemComponentStatusDto> components = new ArrayList<>();

        // 微信集成：近7日 ORG+wechat 成功率
        SystemComponentStatusDto wechat = new SystemComponentStatusDto();
        wechat.setName("微信集成");
        double wechatRate = successRateOfAudit("ORG", "wechat", since);
        wechat.setRunRate(round(wechatRate));
        boolean wechatRunning = isSyncRunningFor("wechat");
        wechat.setStatus(wechatRunning ? "同步中" : (wechatRate >= 97.0 ? "在线" : "警告"));
        components.add(wechat);

        // 数据同步：近7日 ORG 总成功率
        SystemComponentStatusDto sync = new SystemComponentStatusDto();
        sync.setName("数据同步");
        double orgRate = successRateOfAudit("ORG", null, since);
        sync.setRunRate(round(orgRate));
        sync.setStatus(orgRate >= 97.0 ? "在线" : "警告");
        components.add(sync);

        // 支付服务：近7日 PaymentRecord 成功率
        SystemComponentStatusDto payment = new SystemComponentStatusDto();
        payment.setName("支付服务");
        double payRate = successRateOfPayment(since);
        payment.setRunRate(round(payRate));
        payment.setStatus(payRate >= 98.5 ? "在线" : "警告");
        components.add(payment);

        // 通知服务：近7日 NotificationRecord 成功率
        SystemComponentStatusDto notify = new SystemComponentStatusDto();
        notify.setName("通知服务");
        double notifyRate = successRateOfNotification(since);
        notify.setRunRate(round(notifyRate));
        notify.setStatus(notifyRate >= 97.0 ? "在线" : "警告");
        components.add(notify);

        SystemStatusDto dto = new SystemStatusDto();
        dto.setComponents(components);
        dto.setOverallStatus(components.stream().allMatch(c -> !"警告".equals(c.getStatus())) ? "在线" : "警告");
        return dto;
    }

    public List<TodoItemDto> collectTodos() {
        // 待审批：取最近4条
        List<ApprovalWorkflow> pending = approvalEngine.list(new LambdaQueryWrapper<ApprovalWorkflow>()
                .eq(ApprovalWorkflow::getStatus, ApprovalStatus.PENDING)
                .orderByAsc(ApprovalWorkflow::getSubmitTime)
                .last("limit 4"));
        List<TodoItemDto> list = new ArrayList<>();
        for (ApprovalWorkflow w : pending) {
            TodoItemDto t = new TodoItemDto();
            t.setTitle("审核" + (w.getWorkflowName() != null ? w.getWorkflowName() : w.getBusinessKey()));
            t.setPriority("高");
            t.setDue("截止时间: " + (w.getSubmitTime() != null ? w.getSubmitTime().plusHours(24) : LocalDateTime.now().plusHours(24)));
            list.add(t);
        }
        return list;
    }

    public List<ActivityItemDto> collectActivities() {
        List<AuditLog> logs = auditLogService.list(new LambdaQueryWrapper<AuditLog>()
                .orderByDesc(AuditLog::getCreateTime)
                .last("limit 4"));
        List<ActivityItemDto> list = new ArrayList<>();
        for (AuditLog log : logs) {
            ActivityItemDto a = new ActivityItemDto();
            String actor = log.getUsername() != null ? log.getUsername() : "系统";
            a.setActor(actor);
            a.setInitial(actor.substring(0, 1));
            a.setDescription(buildActivityDesc(log));
            a.setTimeAgo(toTimeAgo(log.getCreateTime()));
            list.add(a);
        }
        return list;
    }

    private String buildActivityDesc(AuditLog log) {
        String op = log.getOperation();
        String key = log.getBusinessKey();
        if (op == null) return "活动";
        return switch (op) {
            case "ORG_SYNC", "ORG_SYNC_ALL" -> "同步了组织架构" + (key != null ? (" " + key) : "");
            case "ORG_CHECK" -> "检查了平台连接" + (key != null ? (" " + key) : "");
            default -> op + (key != null ? (" " + key) : "");
        };
    }

    private String toTimeAgo(LocalDateTime time) {
        if (time == null) return "刚刚";
        long minutes = java.time.Duration.between(time, LocalDateTime.now()).toMinutes();
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + "分钟前";
        long hours = minutes / 60;
        if (hours < 24) return hours + "小时前";
        long days = hours / 24;
        return days + "天前";
    }

    private boolean isSyncRunningFor(String platform) {
        return orgSyncTaskService.isRunningFor(platform);
    }

    private double successRateOfPayment(LocalDateTime since) {
        long success = paymentRecordService.count(new LambdaQueryWrapper<PaymentRecord>()
                .ge(PaymentRecord::getUpdateTime, since)
                .eq(PaymentRecord::getStatus, PaymentStatus.SUCCESS));
        long failed = paymentRecordService.count(new LambdaQueryWrapper<PaymentRecord>()
                .ge(PaymentRecord::getUpdateTime, since)
                .eq(PaymentRecord::getStatus, PaymentStatus.FAILED));
        return ratio(success, success + failed) * 100.0;
    }

    private double successRateOfNotification(LocalDateTime since) {
        long success = notificationRecordService.count(new LambdaQueryWrapper<NotificationRecord>()
                .ge(NotificationRecord::getCreateTime, since)
                .eq(NotificationRecord::getStatus, com.yiyundao.compensation.enums.NotificationStatus.SUCCESS));
        long failed = notificationRecordService.count(new LambdaQueryWrapper<NotificationRecord>()
                .ge(NotificationRecord::getCreateTime, since)
                .eq(NotificationRecord::getStatus, com.yiyundao.compensation.enums.NotificationStatus.FAILED));
        return ratio(success, success + failed) * 100.0;
    }

    private double successRateOfAudit(String businessType, String businessKey, LocalDateTime since) {
        LambdaQueryWrapper<AuditLog> w = new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getBusinessType, businessType)
                .ge(AuditLog::getCreateTime, since);
        if (businessKey != null) w.eq(AuditLog::getBusinessKey, businessKey);
        long total = auditLogService.count(w);
        if (total == 0) return 100.0;
        long ok = auditLogService.count(w.clone().eq(AuditLog::getResponseResult, "OK"));
        return ratio(ok, total) * 100.0;
    }

    @SuppressWarnings("unused")
    private long auditCount(String businessType, LocalDateTime since) {
        return auditLogService.count(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getBusinessType, businessType)
                .ge(AuditLog::getCreateTime, since));
    }

    @SuppressWarnings("unused")
    private long auditOkCount(String businessType, LocalDateTime since) {
        return auditLogService.count(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getBusinessType, businessType)
                .ge(AuditLog::getCreateTime, since)
                .eq(AuditLog::getResponseResult, "OK"));
    }

    private BigDecimal sumPayments(LocalDateTime start, LocalDateTime end) {
        List<PaymentRecord> list = paymentRecordService.list(new LambdaQueryWrapper<PaymentRecord>()
                .ge(PaymentRecord::getPaymentTime, start)
                .lt(PaymentRecord::getPaymentTime, end)
                .eq(PaymentRecord::getStatus, PaymentStatus.SUCCESS));
        BigDecimal sum = BigDecimal.ZERO;
        for (PaymentRecord r : list) {
            if (r.getAmount() != null) sum = sum.add(r.getAmount());
        }
        return sum;
    }

    private long countBoundUsers(LocalDateTime until) {
        String sql = """
                SELECT COUNT(DISTINCT user_id)
                FROM external_identity
                WHERE deleted = 0
                  AND status = 'active'
                  AND user_id IS NOT NULL
                """;
        Long count;
        if (until == null) {
            count = jdbcTemplate.queryForObject(sql, Long.class);
        } else {
            count = jdbcTemplate.queryForObject(sql + " AND create_time <= ?", Long.class, until);
        }
        return count != null ? count : 0L;
    }

    private static LocalDateTime firstDayOfThisMonth() {
        LocalDate now = LocalDate.now();
        return now.withDayOfMonth(1).atStartOfDay();
    }

    private static LocalDateTime firstDayOfNextMonth() {
        LocalDate now = LocalDate.now();
        return now.withDayOfMonth(1).plusMonths(1).atStartOfDay();
    }

    private static LocalDateTime firstDayOfPrevMonth() {
        LocalDate now = LocalDate.now();
        return now.withDayOfMonth(1).minusMonths(1).atStartOfDay();
    }

    private static LocalDateTime endOfPrevMonth() {
        LocalDate now = LocalDate.now();
        LocalDate lastDayPrev = now.withDayOfMonth(1).minusDays(1);
        return LocalDateTime.of(lastDayPrev, LocalTime.MAX);
    }

    private static double ratio(long numerator, long denominator) {
        if (denominator <= 0) return 0.0;
        return (double) numerator / (double) denominator;
    }

    private static double percent(long numerator, long denominator) {
        if (denominator <= 0) return 0.0;
        return (double) numerator * 100.0 / (double) denominator;
    }

    private static double round(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static double calcGrowthPercent(long current, long previous) {
        if (previous <= 0) return 0.0;
        return round(((double) (current - previous)) * 100.0 / previous);
    }

    private static double calcGrowthPercent(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        BigDecimal diff = current.subtract(previous);
        BigDecimal pct = diff.multiply(BigDecimal.valueOf(100)).divide(previous, 1, RoundingMode.HALF_UP);
        return pct.doubleValue();
    }
}
