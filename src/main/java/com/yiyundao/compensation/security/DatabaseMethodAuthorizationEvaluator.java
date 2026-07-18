package com.yiyundao.compensation.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Spring method-security 与同一数据库权限决策服务的桥接器。
 */
@Component("databaseMethodAuthorizationEvaluator")
@RequiredArgsConstructor
public class DatabaseMethodAuthorizationEvaluator {

    private final DatabasePermissionService permissionService;

    public boolean check(Authentication authentication) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return false;
        }
        return permissionService.decide(attributes.getRequest(), authentication).allowed();
    }
}
