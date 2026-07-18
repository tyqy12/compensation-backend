package com.yiyundao.compensation.interfaces.dto.payroll;

import com.yiyundao.compensation.modules.payroll.entity.PayrollEnrollment;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PayrollEnrollmentResponse {
    private Long id;
    private Long employeeId;
    private String contributionType;
    private String regionCode;
    private String collectionEntityCode;
    private String accountNoMasked;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String status;
    private Boolean primary;
    private String eventType;
    private Long policyId;

    public static PayrollEnrollmentResponse from(PayrollEnrollment entity) {
        PayrollEnrollmentResponse response = new PayrollEnrollmentResponse();
        response.setId(entity.getId());
        response.setEmployeeId(entity.getEmployeeId());
        response.setContributionType(entity.getContributionType());
        response.setRegionCode(entity.getRegionCode());
        response.setCollectionEntityCode(entity.getCollectionEntityCode());
        response.setAccountNoMasked(mask(entity.getAccountNoEncrypted()));
        response.setEffectiveFrom(entity.getEffectiveFrom());
        response.setEffectiveTo(entity.getEffectiveTo());
        response.setStatus(entity.getStatus());
        response.setPrimary(entity.getPrimary());
        response.setEventType(entity.getEventType());
        response.setPolicyId(entity.getPolicyId());
        return response;
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }
}
