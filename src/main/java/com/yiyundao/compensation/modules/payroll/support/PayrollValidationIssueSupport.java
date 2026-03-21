package com.yiyundao.compensation.modules.payroll.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollValidationIssueDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollValidationIssueSupport {

    private final ObjectMapper objectMapper;

    public PayrollValidationIssueDto blocking(String code, String message) {
        return issue(code, PayrollValidationIssueDto.SEVERITY_BLOCKING, message, null, null, null);
    }

    public PayrollValidationIssueDto blocking(String code,
                                              String message,
                                              String itemCode,
                                              String currentValue,
                                              String expectedValue) {
        return issue(code, PayrollValidationIssueDto.SEVERITY_BLOCKING, message, itemCode, currentValue, expectedValue);
    }

    public PayrollValidationIssueDto review(String code, String message) {
        return issue(code, PayrollValidationIssueDto.SEVERITY_REVIEW, message, null, null, null);
    }

    public PayrollValidationIssueDto review(String code,
                                            String message,
                                            String itemCode,
                                            String currentValue,
                                            String expectedValue) {
        return issue(code, PayrollValidationIssueDto.SEVERITY_REVIEW, message, itemCode, currentValue, expectedValue);
    }

    public PayrollValidationIssueDto info(String code, String message) {
        return issue(code, PayrollValidationIssueDto.SEVERITY_INFO, message, null, null, null);
    }

    public PayrollValidationIssueDto issue(String code,
                                           String severity,
                                           String message,
                                           String itemCode,
                                           String currentValue,
                                           String expectedValue) {
        return PayrollValidationIssueDto.builder()
                .code(code)
                .severity(severity)
                .blocking(PayrollValidationIssueDto.SEVERITY_BLOCKING.equalsIgnoreCase(severity))
                .message(message)
                .itemCode(itemCode)
                .currentValue(currentValue)
                .expectedValue(expectedValue)
                .build();
    }

    public List<String> toMessages(List<PayrollValidationIssueDto> issues) {
        if (issues == null || issues.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> messages = new LinkedHashSet<>();
        for (PayrollValidationIssueDto issue : issues) {
            if (issue != null && StringUtils.hasText(issue.getMessage())) {
                messages.add(issue.getMessage().trim());
            }
        }
        return new ArrayList<>(messages);
    }

    public int countBlocking(List<PayrollValidationIssueDto> issues) {
        return countBySeverity(issues, PayrollValidationIssueDto.SEVERITY_BLOCKING);
    }

    public int countReview(List<PayrollValidationIssueDto> issues) {
        return countBySeverity(issues, PayrollValidationIssueDto.SEVERITY_REVIEW);
    }

    public boolean hasBlocking(List<PayrollValidationIssueDto> issues) {
        return countBlocking(issues) > 0;
    }

    public int countBySeverity(List<PayrollValidationIssueDto> issues, String severity) {
        if (issues == null || issues.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (PayrollValidationIssueDto issue : issues) {
            if (issue != null && severity.equalsIgnoreCase(issue.getSeverity())) {
                count++;
            }
        }
        return count;
    }

    public String serialize(List<PayrollValidationIssueDto> issues) {
        if (issues == null || issues.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(issues);
        } catch (Exception e) {
            log.warn("serialize payroll validation issues failed: {}", e.getMessage());
            return null;
        }
    }

    public List<PayrollValidationIssueDto> deserialize(String payload) {
    if (!StringUtils.hasText(payload)) {
        return Collections.emptyList();
    }
    try {
        return objectMapper.readValue(payload, new TypeReference<List<PayrollValidationIssueDto>>() {});
    } catch (Exception ex) {
        try {
            List<String> legacyMessages = objectMapper.readValue(payload, new TypeReference<List<String>>() {});
            return legacyMessagesToIssues(legacyMessages);
        } catch (Exception ignored) {
            String normalized = unwrapQuotedPayload(payload);
            if (!StringUtils.hasText(normalized)) {
                log.warn("deserialize payroll validation issues failed: {}", ex.getMessage());
                return Collections.emptyList();
            }
            log.warn("deserialize payroll validation issues failed, fallback to legacy warning text: {}", ex.getMessage());
            return legacyMessagesToIssues(List.of(normalized));
        }
    }
}

    private List<PayrollValidationIssueDto> legacyMessagesToIssues(List<String> legacyMessages) {
        if (legacyMessages == null || legacyMessages.isEmpty()) {
            return Collections.emptyList();
        }
        List<PayrollValidationIssueDto> issues = new ArrayList<>();
        for (String message : legacyMessages) {
            if (StringUtils.hasText(message)) {
                issues.add(review("LEGACY_WARNING", message.trim()));
            }
        }
        return issues;
    }

    private String unwrapQuotedPayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        String normalized = payload.trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"")) {
            try {
                return objectMapper.readValue(normalized, String.class);
            } catch (Exception ignored) {
                // ignore and fallback to raw content
            }
        }
        return normalized;
    }
}
