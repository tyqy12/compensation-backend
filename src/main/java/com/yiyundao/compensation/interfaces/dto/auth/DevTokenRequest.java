package com.yiyundao.compensation.interfaces.dto.auth;

import lombok.Data;

import java.util.List;

@Data
public class DevTokenRequest {
    private String username;
    /** @deprecated 开发令牌只签发用户身份，权限始终由数据库动态加载。 */
    @Deprecated
    private List<String> roles;
    /** @deprecated 开发令牌只签发用户身份，权限始终由数据库动态加载。 */
    @Deprecated
    private List<String> authorities;
}
