package com.yiyundao.compensation.interfaces.dto.payroll;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PayrollManualImportItemRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void amountShouldRespectImportTablePrecisionAndScale() {
        PayrollManualImportItemRequest request = validRequest();
        request.setAmount(new BigDecimal("12345678901.00"));

        assertThat(validator.validate(request)).anyMatch(violation -> "amount".equals(violation.getPropertyPath().toString()));

        request.setAmount(new BigDecimal("10.123"));
        assertThat(validator.validate(request)).anyMatch(violation -> "amount".equals(violation.getPropertyPath().toString()));

        request.setAmount(new BigDecimal("9999999999.99"));
        assertThat(validator.validate(request)).isEmpty();
    }

    private PayrollManualImportItemRequest validRequest() {
        PayrollManualImportItemRequest request = new PayrollManualImportItemRequest();
        request.setEmployeeNo("E001");
        request.setItemCode("BASIC");
        request.setAmount(new BigDecimal("100.00"));
        return request;
    }
}
