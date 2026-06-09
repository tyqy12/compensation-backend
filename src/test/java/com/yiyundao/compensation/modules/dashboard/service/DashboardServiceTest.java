package com.yiyundao.compensation.modules.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yiyundao.compensation.dto.dashboard.SystemComponentStatusDto;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.audit.entity.AuditLog;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.notification.entity.NotificationRecord;
import com.yiyundao.compensation.modules.notification.service.NotificationRecordService;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.system.service.OrgSyncTaskService;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private EmployeeService employeeService;
    @Mock
    private PaymentRecordService paymentRecordService;
    @Mock
    private NotificationRecordService notificationRecordService;
    @Mock
    private PaymentBatchService paymentBatchService;
    @Mock
    private SysUserService sysUserService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private ApprovalEngine approvalEngine;
    @Mock
    private OrgSyncTaskService orgSyncTaskService;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, PaymentRecord.class.getName());
        assistant.setCurrentNamespace(PaymentRecord.class.getName());
        TableInfoHelper.initTableInfo(assistant, PaymentRecord.class);
    }

    @Test
    void collectStatusShouldMeasurePaymentHealthByTerminalUpdateTime() {
        DashboardService service = new DashboardService(
                employeeService,
                paymentRecordService,
                notificationRecordService,
                paymentBatchService,
                sysUserService,
                auditLogService,
                approvalEngine,
                orgSyncTaskService,
                jdbcTemplate);
        AtomicInteger paymentCountCall = new AtomicInteger();
        Mockito.when(auditLogService.count(ArgumentMatchers.<LambdaQueryWrapper<AuditLog>>any()))
                .thenReturn(0L);
        Mockito.when(notificationRecordService.count(ArgumentMatchers.<LambdaQueryWrapper<NotificationRecord>>any()))
                .thenReturn(0L);
        Mockito.when(paymentRecordService.count(ArgumentMatchers.<LambdaQueryWrapper<PaymentRecord>>any()))
                .thenAnswer(invocation -> {
                    LambdaQueryWrapper<PaymentRecord> wrapper = invocation.getArgument(0);
                    String sql = wrapper.getSqlSegment();
                    assertThat(sql).contains("update_time");
                    assertThat(sql).doesNotContain("payment_time");
                    int call = paymentCountCall.incrementAndGet();
                    assertThat(wrapper.getParamNameValuePairs().values()).contains(call == 1
                            ? PaymentStatus.SUCCESS
                            : PaymentStatus.FAILED);
                    return 1L;
                });

        var status = service.collectStatus();

        assertThat(paymentCountCall).hasValue(2);
        assertThat(status.getComponents())
                .extracting(SystemComponentStatusDto::getName)
                .contains("支付服务");
        assertThat(status.getComponents().stream()
                .filter(component -> "支付服务".equals(component.getName()))
                .findFirst()
                .orElseThrow()
                .getRunRate()).isEqualTo(50.0);
    }
}
