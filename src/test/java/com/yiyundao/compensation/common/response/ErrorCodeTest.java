package com.yiyundao.compensation.common.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("错误码枚举测试")
class ErrorCodeTest {

    @Test
    @DisplayName("成功码测试")
    void testSuccessCode() {
        ErrorCode success = ErrorCode.SUCCESS;

        assertEquals(0, success.getCode());
        assertEquals("操作成功", success.getMessage());
        assertEquals(HttpStatus.OK, success.getHttpStatus());
    }

    @Test
    @DisplayName("从错误码查找枚举")
    void testFromCode() {
        ErrorCode code = ErrorCode.fromCode(4003);

        assertNotNull(code);
        assertEquals(ErrorCode.PARAM_INVALID, code);
    }

    @Test
    @DisplayName("从错误码查找 - 不存在的码")
    void testFromCode_NotFound() {
        ErrorCode code = ErrorCode.fromCode(99999);

        assertNull(code);
    }

    @Test
    @DisplayName("从错误码查找 - null")
    void testFromCode_Null() {
        ErrorCode code = ErrorCode.fromCode(null);

        assertNull(code);
    }

    @Test
    @DisplayName("获取 HTTP 状态码")
    void testGetHttpStatusCode() {
        assertEquals(200, ErrorCode.SUCCESS.getHttpStatusCode());
        assertEquals(400, ErrorCode.PARAM_INVALID.getHttpStatusCode());
        assertEquals(401, ErrorCode.UNAUTHORIZED.getHttpStatusCode());
        assertEquals(403, ErrorCode.FORBIDDEN.getHttpStatusCode());
        assertEquals(500, ErrorCode.SYSTEM_ERROR.getHttpStatusCode());
    }

    @Test
    @DisplayName("客户端错误判断")
    void testIsClientError() {
        assertTrue(ErrorCode.PARAM_INVALID.isClientError());
        assertTrue(ErrorCode.BODY_PARSE_ERROR.isClientError());
        assertFalse(ErrorCode.SUCCESS.isClientError());
        assertFalse(ErrorCode.SYSTEM_ERROR.isClientError());
    }

    @Test
    @DisplayName("服务端错误判断")
    void testIsServerError() {
        assertTrue(ErrorCode.SYSTEM_ERROR.isServerError());
        assertTrue(ErrorCode.DATABASE_ERROR.isServerError());
        assertFalse(ErrorCode.SUCCESS.isServerError());
        assertFalse(ErrorCode.PARAM_INVALID.isServerError());
    }

    @Test
    @DisplayName("认证错误判断")
    void testIsAuthenticationError() {
        assertTrue(ErrorCode.UNAUTHORIZED.isAuthenticationError());
        assertTrue(ErrorCode.TOKEN_INVALID.isAuthenticationError());
        assertFalse(ErrorCode.SUCCESS.isAuthenticationError());
        assertFalse(ErrorCode.FORBIDDEN.isAuthenticationError());
    }

    @Test
    @DisplayName("权限错误判断")
    void testIsAuthorizationError() {
        assertTrue(ErrorCode.FORBIDDEN.isAuthorizationError());
        assertTrue(ErrorCode.ROLE_INSUFFICIENT.isAuthorizationError());
        assertFalse(ErrorCode.SUCCESS.isAuthorizationError());
        assertFalse(ErrorCode.UNAUTHORIZED.isAuthorizationError());
    }

    @Test
    @DisplayName("所有错误码枚举测试")
    void testAllErrorCodes() {
        // 验证所有错误码都能正确获取属性
        for (ErrorCode code : ErrorCode.values()) {
            assertNotNull(code.getCode());
            assertNotNull(code.getMessage());
            assertNotNull(code.getHttpStatus());
            // SUCCESS 的 code 是 0，需要排除
            if (code != ErrorCode.SUCCESS) {
                assertTrue(code.getCode() > 0, "错误码必须大于0: " + code);
            }
        }
    }

    @Test
    @DisplayName("业务错误码测试")
    void testBusinessErrorCodes() {
        ErrorCode businessError = ErrorCode.BUSINESS_ERROR;
        assertEquals(1001, businessError.getCode());
        assertEquals("业务处理失败", businessError.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, businessError.getHttpStatus());
    }

    @Test
    @DisplayName("参数错误码测试")
    void testParamErrorCodes() {
        assertEquals(4001, ErrorCode.PARAM_MISSING.getCode());
        assertEquals(4002, ErrorCode.PARAM_FORMAT_ERROR.getCode());
        assertEquals(4003, ErrorCode.PARAM_INVALID.getCode());
    }

    @Test
    @DisplayName("认证错误码测试")
    void testAuthErrorCodes() {
        assertEquals(40101, ErrorCode.UNAUTHORIZED.getCode());
        assertEquals(40102, ErrorCode.TOKEN_INVALID.getCode());
        assertEquals(40103, ErrorCode.TOKEN_FORMAT_ERROR.getCode());
    }

    @Test
    @DisplayName("权限错误码测试")
    void testForbiddenErrorCodes() {
        assertEquals(40301, ErrorCode.FORBIDDEN.getCode());
        assertEquals(40302, ErrorCode.ROLE_INSUFFICIENT.getCode());
        assertEquals(40303, ErrorCode.ACCESS_DENIED.getCode());
    }

    @Test
    @DisplayName("系统错误码测试")
    void testSystemErrorCodes() {
        assertEquals(50001, ErrorCode.SYSTEM_ERROR.getCode());
        assertEquals(50002, ErrorCode.DATABASE_ERROR.getCode());
        assertEquals(50003, ErrorCode.CACHE_ERROR.getCode());
    }

    @Test
    @DisplayName("第三方集成错误码测试")
    void testThirdPartyErrorCodes() {
        assertEquals(60001, ErrorCode.ALIPAY_PAYMENT_FAILED.getCode());
        assertEquals(60002, ErrorCode.WECHAT_API_ERROR.getCode());
        assertEquals(60003, ErrorCode.DINGTALK_API_ERROR.getCode());
        assertEquals(60004, ErrorCode.FEISHU_API_ERROR.getCode());
    }
}
