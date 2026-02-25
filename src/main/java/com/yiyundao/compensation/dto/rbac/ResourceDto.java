package com.yiyundao.compensation.dto.rbac;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceDto {
    private Long id;
    private String type;       // MENU/VIEW/ACTION/API
    private String code;       // 唯一
    private String name;
    private String path;
    private String component;
    private String icon;
    private Long parentId;     // 父资源ID（数据库ID）
    private String parentCode; // 父资源Code（导入时使用，支持根据code引用父资源）
    private Integer orderNum;
    private Map<String, Object> meta; // propsJson 解析后
    private String status;     // enabled/disabled

    public static ResourceDto from(SysResource r, ObjectMapper om) {
        ResourceDto dto = new ResourceDto();
        dto.setId(r.getId());
        dto.setType(r.getType());
        dto.setCode(r.getCode());
        dto.setName(r.getName());
        dto.setPath(r.getPath());
        dto.setComponent(r.getComponent());
        dto.setIcon(r.getIcon());
        dto.setParentId(r.getParentId());
        dto.setOrderNum(r.getOrderNum());
        dto.setStatus(r.getStatus());
        if (StringUtils.hasText(r.getPropsJson())) {
            try {
                dto.setMeta(om.readValue(r.getPropsJson(), new TypeReference<>() {}));
            } catch (Exception ignored) {}
        }
        return dto;
    }

    public SysResource toEntity(ObjectMapper om) {
        SysResource r = new SysResource();
        r.setId(getId());
        r.setType(getType());
        r.setCode(getCode());
        r.setName(getName());
        r.setPath(getPath());
        r.setComponent(getComponent());
        r.setIcon(getIcon());
        r.setParentId(getParentId());
        r.setOrderNum(getOrderNum());
        r.setStatus(getStatus());
        if (meta != null && !meta.isEmpty()) {
            try { r.setPropsJson(om.writeValueAsString(meta)); } catch (Exception ignored) {}
        } else {
            r.setPropsJson(null);
        }
        return r;
    }

    /**
     * 获取实际的父ID（优先使用 parentId，如果为 null 则根据 parentCode 查询）
     * 此方法需要在导入时调用
     *
     * @param parentCodeMap code -> id 的映射
     * @return 父资源ID
     */
    public Long resolveParentId(java.util.Map<String, Long> parentCodeMap) {
        if (parentId != null) {
            return parentId;
        }
        if (StringUtils.hasText(parentCode) && parentCodeMap != null) {
            return parentCodeMap.get(parentCode);
        }
        return null;
    }
}

