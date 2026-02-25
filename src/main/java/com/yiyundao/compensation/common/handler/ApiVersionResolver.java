package com.yiyundao.compensation.common.handler;

import com.yiyundao.compensation.common.annotation.ApiVersion;
import com.yiyundao.compensation.common.annotation.IgnoreVersion;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Optional;

/**
 * API 版本解析器
 * <p>
 * 从请求路径或注解中解析 API 版本号。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Slf4j
@Component
public class ApiVersionResolver {

    /**
     * 默认版本号
     */
    private static final int DEFAULT_VERSION = 1;

    /**
     * 版本路径前缀正则
     */
    private static final String VERSION_PATTERN = "/v(\\d+)";

    @SuppressWarnings("unused")
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    public ApiVersionResolver(RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
    }

    /**
     * 解析请求的 API 版本号
     *
     * @param request HTTP 请求
     * @param handlerMethod 处理器方法
     * @return 版本号
     */
    public int resolveVersion(HttpServletRequest request, HandlerMethod handlerMethod) {
        // 1. 首先从路径解析（/api/v1/xxx 或 /api/v2/xxx）
        String requestUri = request.getRequestURI();
        Optional<Integer> pathVersion = parseVersionFromPath(requestUri);
        if (pathVersion.isPresent()) {
            return pathVersion.get();
        }

        // 2. 从方法注解解析
        ApiVersion methodVersion = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), ApiVersion.class);
        if (methodVersion != null) {
            return methodVersion.value();
        }

        // 3. 从类注解解析
        ApiVersion classVersion = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), ApiVersion.class);
        if (classVersion != null) {
            return classVersion.value();
        }

        // 4. 从 RequestMapping 注解的 path 解析
        RequestMapping requestMapping = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), RequestMapping.class);
        if (requestMapping != null && requestMapping.value().length > 0) {
            String basePath = requestMapping.value()[0];
            Optional<Integer> basePathVersion = parseVersionFromPath(basePath);
            if (basePathVersion.isPresent()) {
                return basePathVersion.get();
            }
        }

        // 5. 从 HandlerMethod 的自定义属性解析（如果有）
        Object versionAttr = request.getAttribute("apiVersion");
        if (versionAttr instanceof Integer) {
            return (Integer) versionAttr;
        }

        log.debug("未找到版本注解，使用默认版本: {}", DEFAULT_VERSION);
        return DEFAULT_VERSION;
    }

    /**
     * 判断是否忽略版本控制
     *
     * @param handlerMethod 处理器方法
     * @return 是否忽略版本控制
     */
    public boolean isIgnoreVersion(HandlerMethod handlerMethod) {
        // 检查方法级别
        if (AnnotationUtils.findAnnotation(handlerMethod.getMethod(), IgnoreVersion.class) != null) {
            return true;
        }
        // 检查类级别
        return AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), IgnoreVersion.class) != null;
    }

    /**
     * 从路径中解析版本号
     *
     * @param path 路径
     * @return 版本号（如果存在）
     */
    private Optional<Integer> parseVersionFromPath(String path) {
        if (!StringUtils.hasText(path)) {
            return Optional.empty();
        }
        // 匹配 /v1/ 或 /v1 开头的版本号
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(VERSION_PATTERN);
        java.util.regex.Matcher matcher = pattern.matcher(path);
        if (matcher.find()) {
            try {
                return Optional.of(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException e) {
                log.warn("版本号解析失败: {}", matcher.group(1));
            }
        }
        return Optional.empty();
    }

    /**
     * 获取请求的版本路径前缀
     *
     * @param version 版本号
     * @return 版本路径前缀
     */
    public String getVersionPrefix(int version) {
        return "/v" + version;
    }

    /**
     * 检查请求路径是否包含版本号
     *
     * @param requestUri 请求路径
     * @return 是否包含版本号
     */
    public boolean hasVersionInPath(String requestUri) {
        return parseVersionFromPath(requestUri).isPresent();
    }

    /**
     * 将路径转换为指定版本的路径
     *
     * @param originalPath 原始路径
     * @param targetVersion 目标版本
     * @return 转换后的路径
     */
    public String convertToVersion(String originalPath, int targetVersion) {
        Optional<Integer> currentVersion = parseVersionFromPath(originalPath);
        if (currentVersion.isPresent()) {
            // 替换现有版本号
            return originalPath.replaceFirst(VERSION_PATTERN, "/v" + targetVersion);
        }
        // 插入版本号
        String versionPrefix = getVersionPrefix(targetVersion);
        if (originalPath.startsWith("/api")) {
            // /api/xxx -> /api/v1/xxx
            return originalPath.replaceFirst("(/api)", "$1" + versionPrefix);
        }
        // /xxx -> /v1/xxx
        return versionPrefix + originalPath;
    }
}
