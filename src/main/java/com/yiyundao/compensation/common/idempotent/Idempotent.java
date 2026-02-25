package com.yiyundao.compensation.common.idempotent;

import java.lang.annotation.*;

/**
 * 幂等性注解
 * <p>
 * 用于标记需要幂等性保护的接口。
 * 支持通过 SpEL 表达式或固定字符串指定幂等键。
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 使用 SpEL 表达式
 * {@code @}Idempotent(key = "#request.orderNo + ':' + #request.userId")
 * public ApiResponse<Void> createOrder(OrderRequest request) {
 *     // ...
 * }
 *
 * // 使用固定字符串
 * {@code @}Idempotent(key = "payment:" + @paymentKeyGenerator.generate(#request))
 * public ApiResponse<Void> processPayment(PaymentRequest request) {
 *     // ...
 * }
 * </pre>
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等键
     * <p>
     * 支持 SpEL 表达式，例如：
     * - "#request.orderNo" - 从请求对象中获取 orderNo 字段
     * - "#request.orderNo + ':' + #request.userId" - 拼接多个字段
     * - "#{#userId}" - 从方法参数中获取
     * </p>
     *
     * @return 幂等键表达式
     */
    String key();

    /**
     * 锁过期时间（秒）
     *
     * @return 过期时间
     */
    int expireSeconds() default 300;

    /**
     * 错误消息
     *
     * @return 错误消息
     */
    String message() default "请勿重复提交";

    /**
     * 是否在获取锁失败时抛出异常
     *
     * @return 是否抛出异常
     */
    boolean throwOnLockFail() default false;

    /**
     * 是否删除锁（即使方法执行失败）
     *
     * @return 是否删除锁
     */
    boolean deleteOnError() default false;

    /**
     * 自定义锁前缀
     *
     * @return 锁前缀
     */
    String lockPrefix() default "idempotent:";
}
