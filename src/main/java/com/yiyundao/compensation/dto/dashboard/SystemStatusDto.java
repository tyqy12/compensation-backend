package com.yiyundao.compensation.dto.dashboard;

import lombok.Data;

import java.util.List;

@Data
public class SystemStatusDto {
    private String overallStatus; // 系统状态：在线/警告/离线
    private List<SystemComponentStatusDto> components;
}

