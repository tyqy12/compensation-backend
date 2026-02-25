package com.yiyundao.compensation.common.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("脱敏类型枚举测试")
class SensitiveTypeTest {

    @Test
    @DisplayName("ID_CARD 脱敏规则测试")
    void testIdCardType() {
        String input = "110101199001011234";
        String expected = "110***********1234";
        String result = SensitiveType.ID_CARD.desensitize(input);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("ID_CARD 脱敏规则测试 - 短身份证")
    void testIdCardType_Short() {
        String input = "110101900101123";
        String result = SensitiveType.ID_CARD.desensitize(input);
        assertNotNull(result);
    }

    @Test
    @DisplayName("PHONE 脱敏规则测试")
    void testPhoneType() {
        String input = "13812345678";
        String expected = "138****5678";
        String result = SensitiveType.PHONE.desensitize(input);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("PHONE 脱敏规则测试 - 短手机号")
    void testPhoneType_Short() {
        String input = "1381234";
        String result = SensitiveType.PHONE.desensitize(input);
        assertNotNull(result);
    }

    @Test
    @DisplayName("BANK_CARD 脱敏规则测试")
    void testBankCardType() {
        String input = "6222021234567890123";
        String expected = "6222**********0123";
        String result = SensitiveType.BANK_CARD.desensitize(input);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("NAME 脱敏规则测试 - 两字姓名")
    void testNameType_TwoChars() {
        String input = "张三";
        String expected = "张*";
        String result = SensitiveType.NAME.desensitize(input);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("NAME 脱敏规则测试 - 三字姓名")
    void testNameType_ThreeChars() {
        String input = "李四五六";
        String expected = "李*";
        String result = SensitiveType.NAME.desensitize(input);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("EMAIL 脱敏规则测试")
    void testEmailType() {
        String input = "test@example.com";
        String expected = "te**@example.com";
        String result = SensitiveType.EMAIL.desensitize(input);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("ADDRESS 脱敏规则测试")
    void testAddressType() {
        String input = "北京市朝阳区建国路100号";
        String result = SensitiveType.ADDRESS.desensitize(input);
        assertNotNull(result);
        assertTrue(result.contains("*"));
    }

    @Test
    @DisplayName("DEFAULT 脱敏规则测试")
    void testDefaultType() {
        String input = "测试数据";
        String expected = "***";
        String result = SensitiveType.DEFAULT.desensitize(input);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("脱敏 null 值测试")
    void testNullValue() {
        assertNull(SensitiveType.ID_CARD.desensitize(null));
        assertNull(SensitiveType.PHONE.desensitize(null));
        assertNull(SensitiveType.BANK_CARD.desensitize(null));
        assertNull(SensitiveType.NAME.desensitize(null));
        assertNull(SensitiveType.EMAIL.desensitize(null));
    }

    @Test
    @DisplayName("脱敏空字符串测试")
    void testEmptyString() {
        assertEquals("", SensitiveType.ID_CARD.desensitize(""));
        assertEquals("", SensitiveType.PHONE.desensitize(""));
        assertEquals("", SensitiveType.NAME.desensitize(""));
        assertEquals("", SensitiveType.EMAIL.desensitize(""));
    }

    @Test
    @DisplayName("枚举描述测试")
    void testDescriptions() {
        assertEquals("身份证号", SensitiveType.ID_CARD.getDescription());
        assertEquals("手机号", SensitiveType.PHONE.getDescription());
        assertEquals("银行卡号", SensitiveType.BANK_CARD.getDescription());
        assertEquals("姓名", SensitiveType.NAME.getDescription());
        assertEquals("邮箱", SensitiveType.EMAIL.getDescription());
        assertEquals("地址", SensitiveType.ADDRESS.getDescription());
        assertEquals("默认", SensitiveType.DEFAULT.getDescription());
    }
}
