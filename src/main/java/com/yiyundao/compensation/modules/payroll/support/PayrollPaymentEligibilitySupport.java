package com.yiyundao.compensation.modules.payroll.support;

import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.enums.PayrollConfirmationStatus;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollValidationIssueDto;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class PayrollPaymentEligibilitySupport {

    private static final int MAX_REPORTED_LINE_IDS = 5;
    private static final String WARNING_PARSE_FAILED_MESSAGE =
            "工资校验结果解析失败，请重新计算薪酬或修复校验数据";

    private PayrollPaymentEligibilitySupport() {
    }

    public static void requireAllLinesFinalForPayment(PayrollBatch batch, List<PayrollLine> lines) {
        if (batch == null || Boolean.FALSE.equals(batch.getConfirmationRequired())
                || CollectionUtils.isEmpty(lines)) {
            return;
        }

        List<String> unresolvedLineIds = lines.stream()
                .filter(line -> line == null
                        || !PayrollConfirmationStatus.fromCode(line.getConfirmationStatus()).isFinalForPayment())
                .map(line -> line != null && line.getId() != null ? String.valueOf(line.getId()) : "unknown")
                .limit(MAX_REPORTED_LINE_IDS)
                .toList();
        if (!unresolvedLineIds.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS,
                    "还有员工待确认或异议未处理，暂不可创建发放或支付批次: lineIds="
                            + unresolvedLineIds.stream().collect(Collectors.joining(","))
            );
        }
    }

    public static void requireNoBlockingIssues(List<PayrollLine> lines,
                                               Function<String, List<PayrollValidationIssueDto>> warningDeserializer) {
        List<String> messages = collectBlockingIssueMessages(lines, warningDeserializer);
        if (!messages.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "存在阻塞问题，暂不可创建发放或支付批次：" + String.join("；", messages)
            );
        }
    }

    public static List<String> collectBlockingIssueMessages(
            List<PayrollLine> lines,
            Function<String, List<PayrollValidationIssueDto>> warningDeserializer
    ) {
        if (CollectionUtils.isEmpty(lines)) {
            return List.of("未生成工资结果，请先执行计算薪酬");
        }
        Set<String> messages = new LinkedHashSet<>();
        for (PayrollLine line : lines) {
            if (line == null || warningDeserializer == null) {
                continue;
            }
            List<PayrollValidationIssueDto> issues;
            try {
                issues = warningDeserializer.apply(line.getWarning());
            } catch (Exception ignored) {
                messages.add(WARNING_PARSE_FAILED_MESSAGE);
                continue;
            }
            if (issues == null || issues.isEmpty()) {
                continue;
            }
            for (PayrollValidationIssueDto issue : issues) {
                if (isBlockingIssue(issue) && hasText(issue.getMessage())) {
                    messages.add(issue.getMessage().trim());
                }
            }
        }
        return new ArrayList<>(messages);
    }

    private static boolean isBlockingIssue(PayrollValidationIssueDto issue) {
        return issue != null && (Boolean.TRUE.equals(issue.getBlocking())
                || PayrollValidationIssueDto.SEVERITY_BLOCKING.equalsIgnoreCase(issue.getSeverity()));
    }

    private static boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }
}
