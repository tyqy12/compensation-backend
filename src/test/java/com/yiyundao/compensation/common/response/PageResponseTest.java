package com.yiyundao.compensation.common.response;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("分页响应测试")
class PageResponseTest {

    @Test
    @DisplayName("从 IPage 构建分页响应")
    void testOf_IPage() {
        IPage<String> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10, 25);
        page.setRecords(Arrays.asList("item1", "item2", "item3"));

        PageResponse<String> response = PageResponse.of(page);

        assertEquals(3, response.getList().size());
        assertEquals(1, response.getPageNum());
        assertEquals(10, response.getPageSize());
        assertEquals(25L, response.getTotal());
        assertEquals(3, response.getTotalPages());
        assertTrue(response.getHasNextPage());
        assertFalse(response.getHasPreviousPage());
    }

    @Test
    @DisplayName("从 IPage 构建分页响应 - 带转换器")
    void testOf_IPageWithConverter() {
        IPage<Integer> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(2, 5, 20);
        page.setRecords(Arrays.asList(1, 2, 3, 4, 5));

        PageResponse<String> response = PageResponse.of(page, Object::toString);

        assertEquals(5, response.getList().size());
        assertEquals(Arrays.asList("1", "2", "3", "4", "5"), response.getList());
        assertEquals(2, response.getPageNum());
    }

    @Test
    @DisplayName("手动构建分页响应")
    void testOf_Manual() {
        List<String> list = Arrays.asList("a", "b", "c");

        PageResponse<String> response = PageResponse.of(list, 3, 10, 28);

        assertEquals(3, response.getList().size());
        assertEquals(3, response.getPageNum());
        assertEquals(10, response.getPageSize());
        assertEquals(28L, response.getTotal());
        assertEquals(3, response.getTotalPages());
    }

    @Test
    @DisplayName("手动构建分页响应 - Long 类型参数")
    void testOf_Manual_LongTypes() {
        List<String> list = Arrays.asList("x", "y");

        PageResponse<String> response = PageResponse.of(list, 1L, 20L, 40L);

        assertEquals(2, response.getList().size());
        assertEquals(1, response.getPageNum());
        assertEquals(20, response.getPageSize());
        assertEquals(40L, response.getTotal());
    }

    @Test
    @DisplayName("获取分页偏移量")
    void testGetOffset() {
        PageResponse<String> response = PageResponse.of(
                Collections.emptyList(), 5, 20, 100
        );

        assertEquals(80, response.getOffset());
    }

    @Test
    @DisplayName("转换为 ApiResponse")
    void testToApiResponse() {
        List<String> list = Arrays.asList("item1", "item2");
        PageResponse<String> pageResponse = PageResponse.of(list, 1, 10, 2);

        ApiResponse<PageResponse<String>> apiResponse = pageResponse.toApiResponse();

        assertEquals(0, apiResponse.getCode());
        assertEquals("操作成功", apiResponse.getMessage());
        assertNotNull(apiResponse.getData());
        assertNotNull(apiResponse.getPage());
    }

    @Test
    @DisplayName("转换为 ApiResponse - 自定义消息")
    void testToApiResponse_CustomMessage() {
        PageResponse<String> pageResponse = PageResponse.of(
                Arrays.asList("item1"), 1, 10, 1
        );

        ApiResponse<PageResponse<String>> apiResponse = pageResponse.toApiResponse("查询成功");

        assertEquals(0, apiResponse.getCode());
        assertEquals("查询成功", apiResponse.getMessage());
    }

    @Test
    @DisplayName("空列表分页响应")
    void testEmptyList() {
        IPage<String> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10, 0);
        page.setRecords(Collections.emptyList());

        PageResponse<String> response = PageResponse.of(page);

        assertTrue(response.getList().isEmpty());
        assertEquals(1, response.getPageNum());
        assertEquals(0L, response.getTotal());
        assertEquals(0, response.getTotalPages());
        assertFalse(response.getHasNextPage());
        assertFalse(response.getHasPreviousPage());
    }

    @Test
    @DisplayName("边界条件 - 第一页")
    void testFirstPage() {
        PageResponse<String> response = PageResponse.of(
                Arrays.asList("item1"), 1, 10, 100
        );

        assertEquals(1, response.getPageNum());
        assertFalse(response.getHasPreviousPage());
        assertTrue(response.getHasNextPage());
    }

    @Test
    @DisplayName("边界条件 - 最后一页")
    void testLastPage() {
        PageResponse<String> response = PageResponse.of(
                Arrays.asList("item1"), 10, 10, 100
        );

        assertEquals(10, response.getPageNum());
        assertTrue(response.getHasPreviousPage());
        assertFalse(response.getHasNextPage());
    }
}
