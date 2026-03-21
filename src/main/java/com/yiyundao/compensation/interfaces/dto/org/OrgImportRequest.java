package com.yiyundao.compensation.interfaces.dto.org;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class OrgImportRequest {
    private String provider;
    @JsonIgnore
    private String legacyPlatformType;
    // new_only | upsert，默认由服务端按 new_only 处理
    private String importMode;
    // 前端透传的附加信息（如同步范围、部门ID等）
    private Map<String, Object> metadata;
    private List<OrgImportRequest.EmployeeItem> items;

    @JsonAnySetter
    public void captureLegacyPlatformFields(String key, Object value) {
        if (value == null || key == null) {
            return;
        }
        if ("platformType".equals(key)) {
            this.legacyPlatformType = String.valueOf(value);
        }
    }

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
        private String subjectId;
        private String provider;
        @JsonIgnore
        private String legacyPlatformType;
        @JsonIgnore
        private String legacyPlatformUserId;
        private String status; // optional
        private Boolean offline; // optional
        private Long managerId; // optional
        private String bankAccount; // optional
        private String bankName; // optional
        private java.time.LocalDate hireDate; // optional
        private String username; // preferred username for system account

        @JsonAnySetter
        public void captureLegacyPlatformFields(String key, Object value) {
            if (value == null || key == null) {
                return;
            }
            if ("platformType".equals(key)) {
                this.legacyPlatformType = String.valueOf(value);
                return;
            }
            if ("platformUserId".equals(key)) {
                this.legacyPlatformUserId = String.valueOf(value);
            }
        }
    }
}
