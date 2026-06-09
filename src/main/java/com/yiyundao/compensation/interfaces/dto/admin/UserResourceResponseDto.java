package com.yiyundao.compensation.interfaces.dto.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.modules.rbac.entity.SysUserResource;
import lombok.Builder;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Data
@Builder
public class UserResourceResponseDto {

    private Long id;
    private Long userId;
    private Long resourceId;
    private List<String> actions;
    private String actionsJson;
    private Boolean inheritFromRole;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static UserResourceResponseDto from(SysUserResource resource, ObjectMapper objectMapper) {
        if (resource == null) {
            return null;
        }
        return UserResourceResponseDto.builder()
                .id(resource.getId())
                .userId(resource.getUserId())
                .resourceId(resource.getResourceId())
                .actions(parseActions(resource.getActionsJson(), objectMapper))
                .actionsJson(resource.getActionsJson())
                .inheritFromRole(false)
                .createdAt(resource.getCreateTime())
                .updatedAt(resource.getUpdateTime())
                .createTime(resource.getCreateTime())
                .updateTime(resource.getUpdateTime())
                .build();
    }

    private static List<String> parseActions(String actionsJson, ObjectMapper objectMapper) {
        if (!StringUtils.hasText(actionsJson)) {
            return List.of();
        }
        try {
            return Arrays.asList(objectMapper.readValue(actionsJson, String[].class));
        } catch (Exception ignored) {
            String trimmed = actionsJson.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
            if (!StringUtils.hasText(trimmed)) {
                return List.of();
            }
            return Arrays.stream(trimmed.split("[,\\s]+"))
                    .map(item -> item.replaceAll("^[\"']|[\"']$", ""))
                    .filter(StringUtils::hasText)
                    .toList();
        }
    }
}
