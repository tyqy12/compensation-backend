package com.yiyundao.compensation.interfaces.dto.admin;

import lombok.Data;

import java.util.List;

@Data
public class ResourceDetailDto {
    private Long id;
    private String type;       // MENU/VIEW/ACTION/API
    private String code;
    private String name;
    private String path;
    private String component;
    private String icon;
    private Long parentId;
    private Integer orderNum;
    private String status;     // enabled/disabled
    private List<String> actions; // 授权动作（可为空）
}

