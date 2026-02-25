package com.yiyundao.compensation.dto.dashboard;

import lombok.Data;

@Data
public class TodoItemDto {
    private String title;
    private String priority; // 高/中/低
    private String due;      // 截止时间（展示文案）
}

