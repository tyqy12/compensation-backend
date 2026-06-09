package com.yiyundao.compensation.interceptor;

import com.yiyundao.compensation.common.annotation.Idempotent;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.security.ClientIpResolver;
import com.yiyundao.compensation.service.IdempotentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnBean(IdempotentService.class)
public class IdempotentInterceptor implements HandlerInterceptor {

    private final IdempotentService idempotentService;
    private final ClientIpResolver clientIpResolver;

    @Autowired
    public IdempotentInterceptor(@Autowired(required = false) IdempotentService idempotentService,
                                 ClientIpResolver clientIpResolver) {
        this.idempotentService = idempotentService;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        Method method = handlerMethod.getMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);

        if (idempotent == null) {
            return true;
        }

        // 如果幂等性服务不可用，跳过检查
        if (idempotentService == null) {
            return true;
        }

        String idempotentKey = generateKey(request, idempotent, handlerMethod);

        boolean acquired = idempotentService.tryLock(
                idempotentKey,
                idempotent.expireSeconds(),
                idempotent.tryLock() ? idempotent.waitTime() : 0
        );

        if (!acquired) {
            log.warn("幂等性检查未通过: key={}", idempotentKey);
            writeResponse(response, ErrorCode.REQUEST_CONFLICT, idempotent.message());
            return false;
        }

        request.setAttribute("idempotentKey", idempotentKey);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        String idempotentKey = (String) request.getAttribute("idempotentKey");
        if (idempotentKey != null) {
            if (ex == null) {
                idempotentService.unlock(idempotentKey);
            }
        }
    }

    private String generateKey(HttpServletRequest request, Idempotent idempotent,
                               HandlerMethod handlerMethod) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("request", request);
        variables.put("method", handlerMethod.getMethod().getName());
        variables.put("uri", request.getRequestURI());
        variables.put("ip", clientIpResolver.resolve(request));

        request.getParameterMap().forEach((k, v) ->
                variables.put(k, v.length > 0 ? v[0] : null));

        return idempotentService.generateKey(idempotent.key(), variables);
    }

    private void writeResponse(HttpServletResponse response, ErrorCode errorCode, String message) {
        try {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(errorCode.getHttpStatusCode());
            ApiResponse<?> apiResponse = ApiResponse.error(errorCode, message);
            response.getWriter().write(
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .writeValueAsString(apiResponse)
            );
        } catch (Exception e) {
            log.error("写入幂等响应失败", e);
        }
    }
}
