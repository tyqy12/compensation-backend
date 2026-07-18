package com.yiyundao.compensation.modules.payroll.compliance;

import lombok.Getter;

import java.util.Arrays;

/** 国家综合所得工资薪金场景下的扣除类型编码。 */
@Getter
public enum PayrollDeductionType {
    INFANT_CARE("infant_care", "3岁以下婴幼儿照护"),
    CHILD_EDUCATION("child_education", "子女教育"),
    CONTINUING_EDUCATION("continuing_education", "继续教育"),
    MAJOR_MEDICAL("major_medical", "大病医疗"),
    HOUSING_LOAN_INTEREST("housing_loan_interest", "住房贷款利息"),
    RENT("rent", "住房租金"),
    ELDERLY_CARE("elderly_care", "赡养老人"),
    INDIVIDUAL_PENSION("individual_pension", "个人养老金"),
    OTHER_LAWFUL("other_lawful", "其他依法确定的扣除");

    private final String code;
    private final String label;

    PayrollDeductionType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static boolean supported(String code) {
        return Arrays.stream(values()).anyMatch(item -> item.code.equalsIgnoreCase(code));
    }
}
