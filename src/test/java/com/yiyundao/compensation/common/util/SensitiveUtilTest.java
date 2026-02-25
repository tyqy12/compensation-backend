package com.yiyundao.compensation.common.util;

import com.yiyundao.compensation.common.annotation.SensitiveType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("数据脱敏工具测试")
class SensitiveUtilTest {

    private SensitiveUtil sensitiveUtil;

    @BeforeEach
    void setUp() {
        sensitiveUtil = new SensitiveUtil();
        ReflectionTestUtils.setField(sensitiveUtil, "enabled", true);
    }

    @Test
    @DisplayName("身份证号脱敏测试 - 正常身份证")
    void testDesensitizeIdCard_Normal() {
        String idCard = "110101199001011234";
        String result = sensitiveUtil.desensitizeIdCard(idCard);
        assertEquals("110***********1234", result);
    }

    @Test
    @DisplayName("身份证号脱敏测试 - 短身份证")
    void testDesensitizeIdCard_Short() {
        String idCard = "110101900101123";
        String result = sensitiveUtil.desensitizeIdCard(idCard);
        assertNotNull(result);
        assertTrue(result.contains("*"));
    }

    @Test
    @DisplayName("身份证号脱敏测试 - null 值")
    void testDesensitizeIdCard_Null() {
        String result = sensitiveUtil.desensitizeIdCard(null);
        assertNull(result);
    }

    @Test
    @DisplayName("身份证号脱敏测试 - 空字符串")
    void testDesensitizeIdCard_Empty() {
        String result = sensitiveUtil.desensitizeIdCard("");
        assertEquals("", result);
    }

    @Test
    @DisplayName("手机号脱敏测试 - 正常手机号")
    void testDesensitizePhone_Normal() {
        String phone = "13812345678";
        String result = sensitiveUtil.desensitizePhone(phone);
        assertEquals("138****5678", result);
    }

    @Test
    @DisplayName("手机号脱敏测试 - 短手机号")
    void testDesensitizePhone_Short() {
        String phone = "1381234";
        String result = sensitiveUtil.desensitizePhone(phone);
        assertNotNull(result);
    }

    @Test
    @DisplayName("手机号脱敏测试 - null 值")
    void testDesensitizePhone_Null() {
        String result = sensitiveUtil.desensitizePhone(null);
        assertNull(result);
    }

    @Test
    @DisplayName("银行卡号脱敏测试 - 正常银行卡号")
    void testDesensitizeBankCard_Normal() {
        String bankCard = "6222021234567890123";
        String result = sensitiveUtil.desensitizeBankCard(bankCard);
        assertEquals("6222**********0123", result);
    }

    @Test
    @DisplayName("银行卡号脱敏测试 - 短银行卡号")
    void testDesensitizeBankCard_Short() {
        String bankCard = "622202123456";
        String result = sensitiveUtil.desensitizeBankCard(bankCard);
        assertNotNull(result);
    }

    @Test
    @DisplayName("姓名脱敏测试 - 双字姓名")
    void testDesensitizeName_TwoChars() {
        String name = "张三";
        String result = sensitiveUtil.desensitizeName(name);
        assertEquals("张*", result);
    }

    @Test
    @DisplayName("姓名脱敏测试 - 三字姓名")
    void testDesensitizeName_ThreeChars() {
        String name = "李四五六";
        String result = sensitiveUtil.desensitizeName(name);
        assertEquals("李*", result);
    }

    @Test
    @DisplayName("姓名脱敏测试 - 单字姓名")
    void testDesensitizeName_OneChar() {
        String name = "王";
        String result = sensitiveUtil.desensitizeName(name);
        assertEquals("*", result);
    }

    @Test
    @DisplayName("邮箱脱敏测试 - 正常邮箱")
    void testDesensitizeEmail_Normal() {
        String email = "test@example.com";
        String result = sensitiveUtil.desensitizeEmail(email);
        assertEquals("te**@example.com", result);
    }

    @Test
    @DisplayName("邮箱脱敏测试 - 企业邮箱")
    void testDesensitizeEmail_Company() {
        String email = "admin@company.cn";
        String result = sensitiveUtil.desensitizeEmail(email);
        assertEquals("ad**@company.cn", result);
    }

    @Test
    @DisplayName("邮箱脱敏测试 - null 值")
    void testDesensitizeEmail_Null() {
        String result = sensitiveUtil.desensitizeEmail(null);
        assertNull(result);
    }

    @Test
    @DisplayName("地址脱敏测试 - 正常地址")
    void testDesensitizeAddress_Normal() {
        String address = "北京市朝阳区建国路100号";
        String result = sensitiveUtil.desensitizeAddress(address);
        assertNotNull(result);
        assertTrue(result.contains("*"));
    }

    @Test
    @DisplayName("脱敏开关关闭测试")
    void testDesensitize_Disabled() {
        ReflectionTestUtils.setField(sensitiveUtil, "enabled", false);

        String phone = "13812345678";
        String result = sensitiveUtil.desensitizePhone(phone);

        assertEquals(phone, result);
    }

    @Test
    @DisplayName("通用脱敏方法测试 - ID_CARD")
    void testDesensitize_IdCard() {
        String idCard = "110101199001011234";
        String result = sensitiveUtil.desensitize(idCard, SensitiveType.ID_CARD);
        assertEquals("110***********1234", result);
    }

    @Test
    @DisplayName("通用脱敏方法测试 - PHONE")
    void testDesensitize_Phone() {
        String phone = "13987654321";
        String result = sensitiveUtil.desensitize(phone, SensitiveType.PHONE);
        assertEquals("139****4321", result);
    }

    @Test
    @DisplayName("通用脱敏方法测试 - BANK_CARD")
    void testDesensitize_BankCard() {
        String bankCard = "1234567890123456";
        String result = sensitiveUtil.desensitize(bankCard, SensitiveType.BANK_CARD);
        assertEquals("1234**********3456", result);
    }

    @Test
    @DisplayName("通用脱敏方法测试 - NAME")
    void testDesensitize_Name() {
        String name = "赵六";
        String result = sensitiveUtil.desensitize(name, SensitiveType.NAME);
        assertEquals("赵*", result);
    }

    @Test
    @DisplayName("通用脱敏方法测试 - EMAIL")
    void testDesensitize_Email() {
        String email = "zhaoliu@test.com";
        String result = sensitiveUtil.desensitize(email, SensitiveType.EMAIL);
        assertEquals("zh**@test.com", result);
    }

    @Test
    @DisplayName("通用脱敏方法测试 - DEFAULT")
    void testDesensitize_Default() {
        String value = "测试数据";
        String result = sensitiveUtil.desensitize(value, SensitiveType.DEFAULT);
        assertEquals("***", result);
    }
}
