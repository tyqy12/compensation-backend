package com.yiyundao.compensation.common.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("统一 API 响应测试")
class ApiResponseTest {

    @Test
    @DisplayName("成功响应 - 无数据")
    void testSuccess_NoData() {
        ApiResponse<String> response = ApiResponse.success();

        assertEquals(0, response.getCode());
        assertEquals("操作成功", response.getMessage());
        assertNull(response.getData());
        assertNull(response.getPage());
        assertNotNull(response.getTimestamp());
    }

    @Test
    @DisplayName("成功响应 - 有数据")
    void testSuccess_WithData() {
        String data = "测试数据";
        ApiResponse<String> response = ApiResponse.success(data);

        assertEquals(0, response.getCode());
        assertEquals("操作成功", response.getMessage());
        assertEquals(data, response.getData());
        assertNotNull(response.getTimestamp());
    }

    @Test
    @DisplayName("成功响应 - 有数据和分页信息")
    void testSuccess_WithDataAndPage() {
        String data = "测试数据";
        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.builder()
                .pageNum(1)
                .pageSize(10)
                .total(100L)
                .build();

        ApiResponse<String> response = ApiResponse.<String>success(data, pageInfo);

        assertEquals(0, response.getCode());
        assertEquals(data, response.getData());
        assertNotNull(response.getPage());
        assertEquals(1, response.getPage().getPageNum());
        assertEquals(10, response.getPage().getPageSize());
        assertEquals(100, response.getPage().getTotal());
    }

    @Test
    @DisplayName("成功响应 - 有扩展信息")
    void testSuccess_WithExtra() {
        String data = "测试数据";
        Map<String, Object> extra = Map.of("key", "value");

        ApiResponse<String> response = ApiResponse.<String>success(data, extra);

        assertEquals(0, response.getCode());
        assertEquals(data, response.getData());
        assertEquals(extra, response.getExtra());
    }

    @Test
    @DisplayName("错误响应 - 简单形式")
    void testError_Simple() {
        ApiResponse<Void> response = ApiResponse.error(400, "参数错误");

        assertEquals(400, response.getCode());
        assertEquals("参数错误", response.getMessage());
        assertNull(response.getData());
        assertNotNull(response.getTimestamp());
    }

    @Test
    @DisplayName("错误响应 - 使用 ErrorCode")
    void testError_WithErrorCode() {
        ApiResponse<Void> response = ApiResponse.error(ErrorCode.PARAM_INVALID);

        assertEquals(4003, response.getCode());
        assertEquals("参数值无效", response.getMessage());
    }

    @Test
    @DisplayName("错误响应 - 使用 ErrorCode 和自定义消息")
    void testError_WithErrorCodeAndMessage() {
        ApiResponse<Void> response = ApiResponse.error(ErrorCode.PARAM_INVALID, "自定义错误消息");

        assertEquals(4003, response.getCode());
        assertEquals("自定义错误消息", response.getMessage());
    }

    @Test
    @DisplayName("错误响应 - 带扩展信息")
    void testError_WithExtra() {
        Map<String, Object> extra = Map.of("traceId", "abc123");

        ApiResponse<Void> response = ApiResponse.error(500, "系统错误", extra);

        assertEquals(500, response.getCode());
        assertEquals("系统错误", response.getMessage());
        assertEquals(extra, response.getExtra());
    }

    @Test
    @DisplayName("分页响应 - 手动构建")
    void testPage_Manual() {
        List<String> list = Arrays.asList("item1", "item2", "item3");

        ApiResponse<List<String>> response = ApiResponse.page(list, 1, 10, 30);

        assertEquals(0, response.getCode());
        assertEquals(list, response.getData());
        assertNotNull(response.getPage());
        assertEquals(1, response.getPage().getPageNum());
        assertEquals(10, response.getPage().getPageSize());
        assertEquals(30, response.getPage().getTotal());
        assertEquals(3, response.getPage().getTotalPages());
    }

    @Test
    @DisplayName("分页响应 - 使用 PageInfo")
    void testPage_WithPageInfo() {
        List<String> list = Arrays.asList("item1", "item2");
        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.of(2, 20, 100);

        ApiResponse<List<String>> response = ApiResponse.page(list, pageInfo);

        assertEquals(2, response.getPage().getPageNum());
        assertEquals(20, response.getPage().getPageSize());
        assertEquals(100, response.getPage().getTotal());
    }

    @Test
    @DisplayName("判断是否成功测试")
    void testIsSuccess() {
        ApiResponse<String> successResponse = ApiResponse.success("data");
        ApiResponse<String> errorResponse = ApiResponse.error(400, "error");

        assertTrue(successResponse.isSuccess());
        assertFalse(errorResponse.isSuccess());
    }

    @Test
    @DisplayName("安全类型转换测试")
    void testGetDataAs() {
        ApiResponse<String> response = ApiResponse.success("test");

        String data = response.getDataAs(String.class);
        assertEquals("test", data);

        Integer intData = response.getDataAs(Integer.class);
        assertNull(intData);
    }

    @Test
    @DisplayName("PageInfo 工厂方法测试")
    void testPageInfoFactory() {
        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.of(1, 10, 95);

        assertEquals(1, pageInfo.getPageNum());
        assertEquals(10, pageInfo.getPageSize());
        assertEquals(95, pageInfo.getTotal());
        assertEquals(10, pageInfo.getTotalPages());
        assertTrue(pageInfo.getHasNextPage());
        assertFalse(pageInfo.getHasPreviousPage());
    }

    @Test
    @DisplayName("PageInfo 边界测试 - 第一页")
    void testPageInfo_FirstPage() {
        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.of(1, 10, 50);

        assertEquals(1, pageInfo.getPageNum());
        assertEquals(5, pageInfo.getTotalPages());
        assertFalse(pageInfo.getHasPreviousPage());
        assertTrue(pageInfo.getHasNextPage());
    }

    @Test
    @DisplayName("PageInfo 边界测试 - 最后一页")
    void testPageInfo_LastPage() {
        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.of(5, 10, 50);

        assertEquals(5, pageInfo.getPageNum());
        assertTrue(pageInfo.getHasPreviousPage());
        assertFalse(pageInfo.getHasNextPage());
    }

    @Test
    @DisplayName("PageInfo 边界测试 - 只有一页")
    void testPageInfo_SinglePage() {
        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.of(1, 10, 5);

        assertEquals(1, pageInfo.getTotalPages());
        assertFalse(pageInfo.getHasNextPage());
        assertFalse(pageInfo.getHasPreviousPage());
    }
}
