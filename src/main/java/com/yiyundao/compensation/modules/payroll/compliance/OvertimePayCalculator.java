package com.yiyundao.compensation.modules.payroll.compliance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 标准工时加班费试算；综合工时和不定时工时必须先由政策/审批流程确认。 */
public final class OvertimePayCalculator {

    private OvertimePayCalculator() {
    }

    public enum OvertimeType {
        WORKDAY_EXTENSION,
        REST_DAY_WITHOUT_COMPENSATORY_LEAVE,
        STATUTORY_HOLIDAY
    }

    public static BigDecimal calculate(BigDecimal hourlyBase, BigDecimal hours, OvertimeType type) {
        if (hourlyBase == null || hours == null || type == null || hourlyBase.signum() < 0 || hours.signum() < 0) {
            throw new IllegalArgumentException("加班工资基数、工时和类型必须有效");
        }
        BigDecimal multiplier = switch (type) {
            case WORKDAY_EXTENSION -> new BigDecimal("1.5");
            case REST_DAY_WITHOUT_COMPENSATORY_LEAVE -> new BigDecimal("2");
            case STATUTORY_HOLIDAY -> new BigDecimal("3");
        };
        return hourlyBase.multiply(hours).multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }
}
