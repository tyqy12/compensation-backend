package com.yiyundao.compensation.common.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PinyinUtilsTest {
    @Test
    void testToPinyinSlug() {
        assertEquals("zhangfei", PinyinUtils.toPinyinSlug("张飞"));
        assertEquals("wangwu", PinyinUtils.toPinyinSlug("王五"));
        assertEquals("lisi", PinyinUtils.toPinyinSlug("李 四"));
        assertEquals("zhangsan", PinyinUtils.toPinyinSlug("Zhang San"));
    }
}

