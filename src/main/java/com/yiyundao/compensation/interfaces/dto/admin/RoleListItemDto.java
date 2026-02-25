package com.yiyundao.compensation.interfaces.dto.admin;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RoleListItemDto {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String status;
    private Integer userCount;
    private Integer resourceCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

