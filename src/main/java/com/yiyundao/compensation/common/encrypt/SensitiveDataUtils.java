package com.yiyundao.compensation.common.encrypt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 敏感数据脱敏工具类
 * <p>
 * 提供字段脱敏和对象脱敏功能。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Slf4j
@Component
public class SensitiveDataUtils {

    /**
     * 调试模式（调试时关闭脱敏）
     */
    private volatile boolean debugMode = false;

    /**
     * 设置调试模式
     *
     * @param debugMode 调试模式
     */
    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    /**
     * 判断是否调试模式
     *
     * @return 是否调试模式
     */
    public boolean isDebugMode() {
        return debugMode;
    }

    /**
     * 脱敏对象（递归处理）
     *
     * @param obj 对象
     * @return 脱敏后的对象
     */
    public Object desensitize(Object obj) {
        if (obj == null || debugMode) {
            return obj;
        }

        if (obj instanceof String) {
            return obj; // 字符串不自动脱敏
        }

        if (obj instanceof Collection) {
            return desensitizeCollection((Collection<?>) obj);
        }

        if (obj instanceof Map) {
            return desensitizeMap((Map<?, ?>) obj);
        }

        // 处理普通对象
        return desensitizeObject(obj);
    }

    /**
     * 脱敏集合
     *
     * @param collection 集合
     * @return 脱敏后的集合
     */
    private Collection<?> desensitizeCollection(Collection<?> collection) {
        List<Object> result = new java.util.ArrayList<>();
        for (Object item : collection) {
            result.add(desensitize(item));
        }
        if (collection instanceof List) {
            return result;
        }
        return result;
    }

    /**
     * 脱敏 Map
     *
     * @param map Map
     * @return 脱敏后的 Map
     */
    private Map<?, ?> desensitizeMap(Map<?, ?> map) {
        Map<Object, Object> result = new java.util.HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(entry.getKey(), desensitize(entry.getValue()));
        }
        return result;
    }

    /**
     * 脱敏对象（通过反射）
     *
     * @param obj 对象
     * @return 脱敏后的对象（注意：修改原对象）
     */
    private Object desensitizeObject(Object obj) {
        Class<?> clazz = obj.getClass();

        // 不处理基本类型和包装类
        if (isBasicType(clazz)) {
            return obj;
        }

        Field[] fields = clazz.getDeclaredFields();
        if (fields.length == 0) {
            // 尝试获取父类字段
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null && superClass != Object.class) {
                fields = superClass.getDeclaredFields();
            }
        }

        for (Field field : fields) {
            try {
                processField(obj, field);
            } catch (Exception e) {
                log.debug("字段脱敏失败: {}.{}", clazz.getSimpleName(), field.getName());
            }
        }

        return obj;
    }

    /**
     * 处理单个字段
     */
    private void processField(Object obj, Field field) throws IllegalAccessException {
        // 设置可访问
        field.setAccessible(true);

        // 检查注解
        Sensitive annotation = field.getAnnotation(Sensitive.class);
        if (annotation == null) {
            // 递归处理子对象
            Object value = field.get(obj);
            if (value != null && !isBasicType(field.getType())) {
                field.set(obj, desensitize(value));
            }
            return;
        }

        // 获取值
        Object value = field.get(obj);
        if (value == null) {
            return;
        }

        // 脱敏处理
        String desensitized;
        SensitiveType type = annotation.value();

        if (type == SensitiveType.CUSTOM) {
            desensitized = type.desensitize(
                    value.toString(),
                    annotation.prefix(),
                    annotation.suffix()
            );
        } else {
            desensitized = type.desensitize(value.toString());
        }

        // 设置脱敏后的值
        if (field.getType() == String.class) {
            field.set(obj, desensitized);
        } else {
            // 对于非 String 类型，尝试转换
            field.set(obj, convertValue(desensitized, field.getType()));
        }
    }

    /**
     * 转换值类型
     */
    private Object convertValue(String value, Class<?> targetType) {
        if (targetType == String.class) {
            return value;
        }
        // 其他类型不处理
        return value;
    }

    /**
     * 判断是否为基本类型
     */
    private boolean isBasicType(Class<?> clazz) {
        return clazz.isPrimitive()
                || clazz == String.class
                || clazz == Boolean.class
                || clazz == Byte.class
                || clazz == Short.class
                || clazz == Integer.class
                || clazz == Long.class
                || clazz == Float.class
                || clazz == Double.class
                || clazz == Character.class
                || clazz == Number.class
                || clazz == java.math.BigDecimal.class
                || clazz == java.math.BigInteger.class
                || Enum.class.isAssignableFrom(clazz);
    }

    /**
     * 快速脱敏单个值
     *
     * @param value 原始值
     * @param type  脱敏类型
     * @return 脱敏后的值
     */
    public String desensitize(String value, SensitiveType type) {
        if (value == null || type == SensitiveType.NONE) {
            return value;
        }
        return type.desensitize(value);
    }

    /**
     * 快速脱敏身份证号
     *
     * @param idCard 身份证号
     * @return 脱敏后的身份证号
     */
    public String desensitizeIdCard(String idCard) {
        return desensitize(idCard, SensitiveType.ID_CARD);
    }

    /**
     * 快速脱敏手机号
     *
     * @param phone 手机号
     * @return 脱敏后的手机号
     */
    public String desensitizePhone(String phone) {
        return desensitize(phone, SensitiveType.PHONE);
    }

    /**
     * 快速脱敏银行卡号
     *
     * @param bankCard 银行卡号
     * @return 脱敏后的银行卡号
     */
    public String desensitizeBankCard(String bankCard) {
        return desensitize(bankCard, SensitiveType.BANK_CARD);
    }

    /**
     * 快速脱敏姓名
     *
     * @param name 姓名
     * @return 脱敏后的姓名
     */
    public String desensitizeName(String name) {
        return desensitize(name, SensitiveType.NAME);
    }

    /**
     * 快速脱敏邮箱
     *
     * @param email 邮箱
     * @return 脱敏后的邮箱
     */
    public String desensitizeEmail(String email) {
        return desensitize(email, SensitiveType.EMAIL);
    }
}
