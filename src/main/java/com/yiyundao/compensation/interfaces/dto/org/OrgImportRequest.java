package com.yiyundao.compensation.interfaces.dto.org;

import lombok.Data;
import java.util.List;

@Data
public class OrgImportRequest {
    private String platformType;
    private List<OrgImportRequest.EmployeeItem> items;

    @Data
    public static class EmployeeItem {
        private String employeeId;
        private String name;
        private String phone;
        private String email;
        // 选中的多部门名称（按顺序保存）
        private java.util.List<String> departments;
        private String position;
        private String employmentType; // full_time/part_time
        private String platformUserId;
        private String platformType;
        private String status; // optional
        private Boolean offline; // optional
        private Long managerId; // optional
        private String bankAccount; // optional
        private String bankName; // optional
        private java.time.LocalDate hireDate; // optional
        private String username; // preferred username for system account
    }
}
