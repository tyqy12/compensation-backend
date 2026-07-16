package com.yiyundao.compensation.modules.payroll.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollImportItem;
import com.yiyundao.compensation.modules.payroll.entity.SalaryTemplate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PayrollCalculationSnapshotSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void inputHashShouldBeIndependentOfImportOrderAndAmountScale() {
        PayrollImportItem first = item(2L, "BONUS", "1.00");
        PayrollImportItem second = item(1L, "BASIC", "1000.0");

        String left = PayrollCalculationSnapshotSupport.inputHash(objectMapper, List.of(first, second));
        String right = PayrollCalculationSnapshotSupport.inputHash(objectMapper, List.of(
                item(1L, "BASIC", "1000.00"),
                item(2L, "BONUS", "1.0")
        ));

        assertThat(left).isEqualTo(right).hasSize(64);
        assertThat(PayrollCalculationSnapshotSupport.inputSnapshotJson(objectMapper, List.of(first, second)))
                .contains("\"itemCode\":\"BASIC\"")
                .contains("\"amount\":\"1000\"");
    }

    @Test
    void ruleHashShouldChangeWhenTemplateDataVersionChanges() {
        SalaryTemplate first = template(7L);
        SalaryTemplate second = template(8L);

        assertThat(PayrollCalculationSnapshotSupport.ruleHash(objectMapper, first))
                .isNotEqualTo(PayrollCalculationSnapshotSupport.ruleHash(objectMapper, second));
        assertThat(PayrollCalculationSnapshotSupport.ruleSnapshotJson(objectMapper, first))
                .contains("\"dataVersion\":7")
                .contains("\"taxRuleJson\":\"{}\"");
    }

    private PayrollImportItem item(Long employeeId, String itemCode, String amount) {
        PayrollImportItem item = new PayrollImportItem();
        item.setEmployeeId(employeeId);
        item.setItemCode(itemCode);
        item.setAmount(new BigDecimal(amount));
        item.setStatus("valid");
        return item;
    }

    private SalaryTemplate template(Long version) {
        SalaryTemplate template = new SalaryTemplate();
        template.setId(1L);
        template.setType("full_time");
        template.setDataVersion(version);
        template.setItemsJson("[]");
        template.setTaxRuleJson("{}");
        template.setStatus("enabled");
        return template;
    }
}
