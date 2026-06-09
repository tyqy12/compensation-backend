package com.yiyundao.compensation.modules.payroll.support;

import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollValidationIssueDto;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayrollPaymentEligibilitySupportTest {

    @Test
    void collectBlockingIssueMessagesShouldReportMissingComputedLines() {
        List<String> messages = PayrollPaymentEligibilitySupport.collectBlockingIssueMessages(
                List.of(),
                ignored -> List.of()
        );

        assertThat(messages).containsExactly("未生成工资结果，请先执行计算薪酬");
    }

    @Test
    void collectBlockingIssueMessagesShouldDeduplicateBlockingMessagesOnly() {
        PayrollLine first = new PayrollLine();
        first.setWarning("first");
        PayrollLine second = new PayrollLine();
        second.setWarning("second");

        List<String> messages = PayrollPaymentEligibilitySupport.collectBlockingIssueMessages(
                List.of(first, second),
                warning -> {
                    if ("first".equals(warning)) {
                        return List.of(
                                issue("blocking", true, "缺少必填薪资项：BASIC"),
                                issue("review", false, "实发变动超过阈值")
                        );
                    }
                    return List.of(issue("blocking", true, "缺少必填薪资项：BASIC"));
                }
        );

        assertThat(messages).containsExactly("缺少必填薪资项：BASIC");
    }

    @Test
    void requireNoBlockingIssuesShouldThrowBusinessException() {
        PayrollLine line = new PayrollLine();
        line.setWarning("blocked");

        assertThatThrownBy(() -> PayrollPaymentEligibilitySupport.requireNoBlockingIssues(
                List.of(line),
                ignored -> List.of(issue("blocking", true, "缺少必填薪资项：BASIC"))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在阻塞问题")
                .hasMessageContaining("缺少必填薪资项：BASIC");
    }

    @Test
    void requireNoBlockingIssuesShouldFailClosedWhenWarningCannotBeParsed() {
        PayrollLine line = new PayrollLine();
        line.setWarning("broken-json");

        assertThatThrownBy(() -> PayrollPaymentEligibilitySupport.requireNoBlockingIssues(
                List.of(line),
                ignored -> {
                    throw new IllegalArgumentException("invalid warning payload");
                }
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在阻塞问题")
                .hasMessageContaining("工资校验结果解析失败");
    }

    private PayrollValidationIssueDto issue(String severity, boolean blocking, String message) {
        return PayrollValidationIssueDto.builder()
                .severity(severity)
                .blocking(blocking)
                .message(message)
                .build();
    }
}
