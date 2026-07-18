package com.yiyundao.compensation.interfaces.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import lombok.Builder;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ResourceResponseDto {

    private Long id;
    private String type;
    private String code;
    private String name;
    private String path;
    private String component;
    private String icon;
    private Long parentId;
    private Integer orderNum;
    private Map<String, Object> meta;
    @JsonProperty("_children")
    private List<Long> childrenIds;
    private String status;
    private String accessMode;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static ResourceResponseDto from(SysResource resource, ObjectMapper objectMapper) {
        if (resource == null) {
            return null;
        }
        Map<String, Object> meta = parseMeta(resource.getPropsJson(), objectMapper);
        return ResourceResponseDto.builder()
                .id(resource.getId())
                .type(resource.getType())
                .code(resource.getCode())
                .name(resource.getName())
                .path(resource.getPath())
                .component(resource.getComponent())
                .icon(resource.getIcon())
                .parentId(resource.getParentId())
                .orderNum(resource.getOrderNum())
                .meta(meta)
                .childrenIds(extractChildrenIds(meta))
                .status(resource.getStatus())
                .accessMode(resource.getAccessMode())
                .createTime(resource.getCreateTime())
                .updateTime(resource.getUpdateTime())
                .build();
    }

    private static Map<String, Object> parseMeta(String propsJson, ObjectMapper objectMapper) {
        if (!StringUtils.hasText(propsJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(propsJson, new TypeReference<>() {});
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<Long> extractChildrenIds(Map<String, Object> meta) {
        if (meta == null) {
            return null;
        }
        Object rawChildren = meta.get("_children");
        if (!(rawChildren instanceof List<?> children)) {
            return null;
        }
        List<Long> ids = children.stream()
                .map(ResourceResponseDto::toLong)
                .filter(java.util.Objects::nonNull)
                .toList();
        return ids.isEmpty() ? null : ids;
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
