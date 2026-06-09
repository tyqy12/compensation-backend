package com.yiyundao.compensation.interfaces.controller.payroll;

import com.yiyundao.compensation.enums.NotificationType;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollPreviewDto;
import com.yiyundao.compensation.modules.payroll.service.PayrollCalculationService;
import com.yiyundao.compensation.modules.payroll.service.PayrollImportService;
import com.yiyundao.compensation.modules.system.service.SysConfigService;
import com.yiyundao.compensation.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollImportControllerTest {

    @Mock
    private PayrollImportService payrollImportService;
    @Mock
    private PayrollCalculationService payrollCalculationService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private SysConfigService sysConfigService;

    @Test
    void commitShouldSendApprovalNotificationToConfiguredAdmin() {
        PayrollImportController controller = new PayrollImportController(
                payrollImportService,
                payrollCalculationService,
                notificationService,
                sysConfigService
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "payroll.csv",
                "text/csv",
                "employeeId,itemCode,amount,note\nE001,BASIC,1000,ok\n".getBytes()
        );
        PayrollPreviewDto preview = new PayrollPreviewDto();
        preview.setTotalEmployees(1);
        preview.setEarningsTotal(new BigDecimal("1000"));
        preview.setNetTotal(new BigDecimal("900"));
        preview.setTotalWarnings(0);

        when(payrollImportService.commitCsv(12L, file)).thenReturn("ok");
        when(payrollCalculationService.dryRunPreview(12L)).thenReturn(preview);
        when(sysConfigService.getLong("system.admin_user_id", 1L)).thenReturn(66L);

        controller.commit(12L, file);

        verify(notificationService).sendApprovalNotification(
                eq(66L),
                eq("PAYROLL_IMPORT"),
                eq("薪酬导入审核"),
                eq(NotificationType.APPROVAL_PENDING),
                org.mockito.ArgumentMatchers.contains("批次ID: 12")
        );
    }
}
