package com.yiyundao.compensation.common.exception;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void handleOtherShouldHideExceptionMessageWhenProdProfileComesFromEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        GlobalExceptionHandler handler = new GlobalExceptionHandler(null, null, environment);

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleOther(new RuntimeException("internal secret: jdbc password leaked"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
        assertThat(response.getBody().getMessage()).isEqualTo("系统繁忙，请稍后重试");
        assertThat(response.getBody().getMessage()).doesNotContain("jdbc", "password", "secret");
    }

    @Test
    void handleOtherShouldHideExceptionMessageWhenStagingProfileComesFromEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("staging");
        GlobalExceptionHandler handler = new GlobalExceptionHandler(null, null, environment);

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleOther(new RuntimeException("internal stack detail"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("系统繁忙，请稍后重试");
    }

    @Test
    void handleOtherShouldKeepExceptionMessageOutsideProdLikeProfiles() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        GlobalExceptionHandler handler = new GlobalExceptionHandler(null, null, environment);

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleOther(new RuntimeException("local debug detail"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("local debug detail");
    }

    @Test
    void handleMaxUploadSizeExceededShouldReturnParamInvalid() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(null, null, new MockEnvironment());

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(1024));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode());
        assertThat(response.getBody().getMessage()).isEqualTo("文件大小超过限制");
    }

    @Test
    void handleMultipartShouldReturnParamInvalid() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(null, null, new MockEnvironment());

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMultipart(new MultipartException("invalid multipart boundary"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode());
        assertThat(response.getBody().getMessage()).isEqualTo("文件上传请求格式错误");
    }
}
