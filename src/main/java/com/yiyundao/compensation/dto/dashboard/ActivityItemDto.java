package com.yiyundao.compensation.dto.dashboard;

import lombok.Data;

@Data
public class ActivityItemDto {
    private String actor;       // 显示名（如 管理员/张三/系统/李四）
    private String initial;     // 首字（管/张/系/李）
    private String description; // 活动描述
    private String timeAgo;     // 相对时间（如 5分钟前）
}

