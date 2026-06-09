package com.yiyundao.compensation.modules.payroll.support;

import com.yiyundao.compensation.enums.EmploymentType;
import com.yiyundao.compensation.enums.SettlementAccountType;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.service.EncryptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollDistributionRoutingSupportTest {

    @Mock
    private EncryptionService encryptionService;

    @Test
    void buildSnapshotShouldForceAlipayProviderForFullTimeBankCardAccount() {
        PayrollDistributionRoutingSupport support = new PayrollDistributionRoutingSupport(encryptionService);
        PayrollBatch batch = new PayrollBatch();
        batch.setType(EmploymentType.FULL_TIME.getCode());
        PayrollLine line = new PayrollLine();
        line.setEmploymentType(EmploymentType.FULL_TIME.getCode());
        line.setNetAmount(new BigDecimal("100.00"));
        Employee employee = new Employee();
        employee.setName("张三");
        employee.setSettlementAccountType(SettlementAccountType.BANK_CARD.getCode());
        employee.setSettlementAccount("ENC_BANK");
        employee.setSettlementProviderCode("yunzhanghu");

        when(encryptionService.decrypt("ENC_BANK")).thenReturn("6222020202020202");
        when(encryptionService.encrypt("6222020202020202")).thenReturn("ENC_SNAPSHOT");
        when(encryptionService.maskBankAccount("6222020202020202")).thenReturn("6222****0202");

        PayrollDistributionRoutingSupport.RouteSnapshot snapshot = support.buildSnapshot(batch, line, employee);

        assertThat(snapshot.supported()).isTrue();
        assertThat(snapshot.paymentMethod()).isEqualTo("BANK_CARD");
        assertThat(snapshot.providerCode()).isEqualTo("alipay");
    }

    @Test
    void buildSnapshotShouldFailWhenEncryptedAccountCannotBeDecrypted() {
        PayrollDistributionRoutingSupport support = new PayrollDistributionRoutingSupport(encryptionService);
        PayrollBatch batch = new PayrollBatch();
        batch.setType(EmploymentType.FULL_TIME.getCode());
        PayrollLine line = new PayrollLine();
        line.setEmploymentType(EmploymentType.FULL_TIME.getCode());
        Employee employee = new Employee();
        employee.setName("张三");
        employee.setSettlementAccountType(SettlementAccountType.BANK_CARD.getCode());
        employee.setSettlementAccount("ENC_BANK_VALUE");
        employee.setPhone("13800000000");

        when(encryptionService.decrypt("ENC_BANK_VALUE")).thenThrow(new RuntimeException("bad key"));

        PayrollDistributionRoutingSupport.RouteSnapshot snapshot = support.buildSnapshot(batch, line, employee);

        assertThat(snapshot.supported()).isFalse();
        assertThat(snapshot.failureReason()).contains("收款账号解密失败");
        verify(encryptionService, never()).encrypt("ENC_BANK_VALUE");
        verify(encryptionService, never()).encrypt("13800000000");
        verify(encryptionService, never()).maskPhone("13800000000");
    }

    @Test
    void buildSnapshotShouldKeepLegacyPlainAccountWhenDecryptFails() {
        PayrollDistributionRoutingSupport support = new PayrollDistributionRoutingSupport(encryptionService);
        PayrollBatch batch = new PayrollBatch();
        batch.setType(EmploymentType.FULL_TIME.getCode());
        PayrollLine line = new PayrollLine();
        line.setEmploymentType(EmploymentType.FULL_TIME.getCode());
        Employee employee = new Employee();
        employee.setName("张三");
        employee.setSettlementAccountType(SettlementAccountType.ALIPAY.getCode());
        employee.setSettlementAccount("13800000000");

        when(encryptionService.decrypt("13800000000")).thenThrow(new RuntimeException("legacy plaintext"));
        when(encryptionService.encrypt("13800000000")).thenReturn("ENC_SNAPSHOT");
        when(encryptionService.maskPhone("13800000000")).thenReturn("138****0000");

        PayrollDistributionRoutingSupport.RouteSnapshot snapshot = support.buildSnapshot(batch, line, employee);

        assertThat(snapshot.supported()).isTrue();
        assertThat(snapshot.accountNoEncrypted()).isEqualTo("ENC_SNAPSHOT");
        assertThat(snapshot.paymentMethod()).isEqualTo("ALIPAY");
    }
}
